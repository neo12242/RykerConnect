#ifdef RYKER_REV02
#include "environment_sensor.h"
#include "global_vars.h"
#include <Adafruit_BME280.h>
#include <Wire.h>
#include <cmath>
#include <cstring>

namespace {
Adafruit_BME280 sensor;
EnvironmentReading reading;
portMUX_TYPE readingLock = portMUX_INITIALIZER_UNLOCKED;
uint8_t address = 0;
uint32_t previousPoll = 0;
bool firstPoll = true;
#ifdef RYKER_SENSOR_TEST
bool testBme = true, testRtc = true, testStale = false, testEnabled = true;
float testTemperature = 22, testHumidity = 48, testPressure = 1013.2;
uint32_t testClockSeconds = 12 * 3600, testClockAt = 0;
#endif


bool readRegisters(uint8_t reg, uint8_t* bytes, size_t count) {
    Wire.beginTransmission(address);
    Wire.write(reg);
    if (Wire.endTransmission(false) != 0) return false;
    if (Wire.requestFrom(address, static_cast<uint8_t>(count)) != count) return false;
    for (size_t i = 0; i < count; ++i) bytes[i] = Wire.read();
    return true;
}

bool respondsAsBme280() {
    uint8_t id = 0;
    return readRegisters(0xD0, &id, 1) && id == 0x60;
}

class EnvironmentCallbacks : public NimBLECharacteristicCallbacks {
    void onRead(NimBLECharacteristic* characteristic, NimBLEConnInfo&) override {
        const auto r = environmentReading();
        uint8_t frame[20] = {1, static_cast<uint8_t>((r.valid ? 1 : 0) | (r.simulated ? 2 : 0))};
        // Version 1 wire format is little-endian; ESP32 is little-endian.
        memcpy(frame + 2, &r.sequence, 2);
        const float t = r.valid ? r.celsius : 0;
        const float h = r.valid ? r.humidity : 0;
        const float p = r.valid ? r.pressureHpa : 0;
        memcpy(frame + 4, &t, 4); memcpy(frame + 8, &h, 4); memcpy(frame + 12, &p, 4);
        const uint32_t age = r.sampledAt ? millis() - r.sampledAt : UINT32_MAX;
        memcpy(frame + 16, &age, 4);
        characteristic->setValue(frame, sizeof(frame));
    }
};
EnvironmentCallbacks callbacks;
}

EnvironmentReading environmentReading() {
    portENTER_CRITICAL(&readingLock);
    auto r = reading;
    portEXIT_CRITICAL(&readingLock);
    if (r.valid && millis() - r.sampledAt > 15000) r.valid = false;
    return r;
}

void pollEnvironment() {
    if (!firstPoll && millis() - previousPoll < 5000) return;
    firstPoll = false; previousPoll = millis();
    EnvironmentReading next;
#ifdef RYKER_SENSOR_TEST
    if (testEnabled) {
        next.simulated = true;
        next.valid = testBme && !testStale;
        next.celsius = testTemperature; next.humidity = testHumidity; next.pressureHpa = testPressure;
        next.sampledAt = testStale ? millis() - 20000 : millis();
        next.sequence = environmentReading().sequence + 1;
        portENTER_CRITICAL(&readingLock); reading = next; portEXIT_CRITICAL(&readingLock);
        global_temp = next.valid ? next.celsius : NAN;
        Serial.printf("[TEST ONLY] BME280 %s RTC %s %.1f C / %.1f %% / %.1f hPa\n", testBme ? (testStale ? "STALE" : "ON") : "OFF", testRtc ? "ON" : "OFF", testTemperature, testHumidity, testPressure);
        return;
    }
#endif
    next.sequence = environmentReading().sequence + 1;
    if (!address || !respondsAsBme280()) {
        address = 0;
        for (uint8_t candidate : {uint8_t(0x76), uint8_t(0x77)}) {
            address = candidate;
            if (respondsAsBme280() && sensor.begin(address, &Wire)) {
                sensor.setSampling(Adafruit_BME280::MODE_NORMAL,
                    Adafruit_BME280::SAMPLING_X1, Adafruit_BME280::SAMPLING_X1,
                    Adafruit_BME280::SAMPLING_X1, Adafruit_BME280::FILTER_OFF,
                    Adafruit_BME280::STANDBY_MS_1000);
                break;
            }
            address = 0;
        }
        // Allow the first conversion to finish; the next polling cycle reads it.
    } else {
        uint8_t raw[8];
        // Check actual transaction success before accepting library floats.
        if (readRegisters(0xF7, raw, sizeof(raw))) {
            next.celsius = sensor.readTemperature() - sEEPROM.temp_calibration;
            next.humidity = sensor.readHumidity();
            next.pressureHpa = sensor.readPressure() / 100.0f;
            next.valid = respondsAsBme280() && std::isfinite(next.celsius) &&
                std::isfinite(next.humidity) && std::isfinite(next.pressureHpa) &&
                next.celsius >= -40 && next.celsius <= 85 &&
                next.humidity >= 0 && next.humidity <= 100 &&
                next.pressureHpa >= 300 && next.pressureHpa <= 1100;
            if (next.valid) next.sampledAt = millis();
        }
        if (!next.valid) address = 0;
    }
    portENTER_CRITICAL(&readingLock);
    reading = next;
    portEXIT_CRITICAL(&readingLock);
    global_temp = next.valid ? next.celsius : NAN;
    if (next.valid) Serial.printf("[Environment] %.2f C, %.1f %%RH, %.1f hPa\n", next.celsius, next.humidity, next.pressureHpa);
    else Serial.println("[Environment] unavailable; probing again in 5 seconds");
}

void addEnvironmentCharacteristic(NimBLEService* service) {
    auto characteristic = service->createCharacteristic(ENVIRONMENT_UUID,
        NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::READ_ENC, 20);
    characteristic->setCallbacks(&callbacks);
}


// Nonblocking, bounded serial diagnostics. Injection commands only exist in the test build.
void environmentDiagnostics() {
    static String command;
    static bool overflow = false;
    for (int count = 0; count < 32 && Serial.available(); ++count) {
        const char c = Serial.read();
        if (c == '\r') continue;
        if (c != '\n') { if (command.length() < 80 && !overflow) command += c; else overflow = true; continue; }
        if (overflow) { Serial.println("Command too long"); command = ""; overflow = false; continue; }
        command.trim();
        if (command == "SCAN") {
#ifdef RYKER_SENSOR_TEST
            if (testEnabled) Serial.printf("[SIMULATED I2C] BME280 0x76=%s DS3231 0x68=%s\n", testBme?"present":"missing", testRtc?"present":"missing");
            else
#endif
            {
                for (uint8_t candidate : {uint8_t(0x68), uint8_t(0x76), uint8_t(0x77)}) {
                    Wire.beginTransmission(candidate);
                    Serial.printf("[I2C] 0x%02x %s\n", candidate, Wire.endTransmission() == 0 ? "ACK" : "missing");
                }
            }
        } else if (command == "STATUS") {
            const auto r = environmentReading();
            Serial.printf("[Environment] source=%s valid=%d sequence=%u age=%lu ms\n", r.simulated?"SIMULATED":"physical", r.valid, r.sequence, millis()-r.sampledAt);
        }
#ifdef RYKER_SENSOR_TEST
        else if (command == "BME ON") { testEnabled=true; testBme=true; testStale=false; firstPoll=true; }
        else if (command == "BME OFF") { testEnabled=true; testBme=false; firstPoll=true; }
        else if (command == "BME STALE") { testEnabled=true; testBme=true; testStale=true; firstPoll=true; }
        else if (command == "BME REAL") { testEnabled=false; address=0; firstPoll=true; }
        else if (command == "RTC ON") testRtc=true;
        else if (command == "RTC OFF") testRtc=false;
        else if (command.startsWith("ENV ")) {
            float t,h,p; char extra;
            if(sscanf(command.c_str(),"ENV %f %f %f %c",&t,&h,&p,&extra)==3 && std::isfinite(t) && std::isfinite(h) && std::isfinite(p) && t>=-40 && t<=85 && h>=0 && h<=100 && p>=300 && p<=1100) {
                testTemperature=t; testHumidity=h; testPressure=p; testEnabled=true; firstPoll=true;
            } else Serial.println("Expected ENV <C -40..85> <RH 0..100> <hPa 300..1100>");
        }
#endif
        else if(command.length()) Serial.println("Commands: SCAN, STATUS. Injection commands require RykerConnect_REV02_SensorTest.");
        command="";
    }
}
#ifdef RYKER_SENSOR_TEST
String environmentTestClock() {
    if (!testRtc) return "--:--";
    const uint32_t seconds=(testClockSeconds+(millis()-testClockAt)/1000)%86400;
    char text[6]; snprintf(text,sizeof(text),"%02u:%02u",static_cast<unsigned>(seconds/3600),static_cast<unsigned>(seconds/60%60));
    return String(text);
}
void environmentTestSetClock(uint8_t h,uint8_t m,uint8_t s) {
    if(h<24 && m<60 && s<60){testClockSeconds=h*3600+m*60+s;testClockAt=millis();}
}
#endif

#endif // RYKER_REV02
