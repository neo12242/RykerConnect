#pragma once
#include <stdint.h>
#include <stddef.h>

// NXP PCF8563, 7-bit I2C address. Bus follows the Arduino TwoWire interface.
// Burst reads keep seconds/minutes/hours coherent across a rollover.
template<class Bus> class Pcf8563Clock {
public:
    explicit Pcf8563Clock(Bus& bus) : bus_(bus) {}
    bool read(uint8_t& hour, uint8_t& minute, uint8_t& second) {
        uint8_t r[5];
        if (!readRegisters(0, r, sizeof(r)) || (r[0] & 0x20) || (r[2] & 0x80)) return false;
        uint8_t h, m, s;
        if (!decode(r[2] & 0x7f, 59, s) || !decode(r[3] & 0x7f, 59, m) ||
            !decode(r[4] & 0x3f, 23, h)) return false;
        hour=h; minute=m; second=s;
        return true;
    }
    bool set(uint8_t hour, uint8_t minute, uint8_t second) {
        if (hour > 23 || minute > 59 || second > 59) return false;
        // STOP freezes the prescaler while updating. Clear unused alarm/timer flags.
        const uint8_t control[] = {0x20, 0x00};
        if (!writeRegisters(0, control, sizeof(control))) return false;
        // The phone protocol supplies time only. Initialize unused calendar to 2000-01-01.
        const uint8_t data[] = {encode(second), encode(minute), encode(hour), 0x01, 0x06, 0x01, 0x00};
        if (!writeRegisters(2, data, sizeof(data))) return false;
        const uint8_t unusedOutputs[] = {0x00, 0x00}; // CLKOUT and timer disabled
        if (!writeRegisters(0x0d, unusedOutputs, sizeof(unusedOutputs))) return false;
        const uint8_t start=0;
        return writeRegisters(0, &start, 1);
    }
private:
    Bus& bus_;
    static constexpr uint8_t address=0x51;
    static uint8_t encode(uint8_t v) { return uint8_t((v/10)*16 + v%10); }
    static bool decode(uint8_t b, uint8_t max, uint8_t& value) {
        if ((b & 15) > 9 || (b >> 4) > 9) return false;
        value=uint8_t((b>>4)*10+(b&15)); return value<=max;
    }
    bool readRegisters(uint8_t reg, uint8_t* out, size_t count) {
        bus_.beginTransmission(address); bus_.write(reg);
        if (bus_.endTransmission(false)!=0) return false;
        if (bus_.requestFrom(address, static_cast<uint8_t>(count))!=count) {
            while (bus_.available()) bus_.read();
            return false;
        }
        for (size_t i=0;i<count;++i) out[i]=static_cast<uint8_t>(bus_.read());
        return true;
    }
    bool writeRegisters(uint8_t reg, const uint8_t* data, size_t count) {
        bus_.beginTransmission(address); bus_.write(reg);
        for (size_t i=0;i<count;++i) bus_.write(data[i]);
        return bus_.endTransmission(true)==0;
    }
};
