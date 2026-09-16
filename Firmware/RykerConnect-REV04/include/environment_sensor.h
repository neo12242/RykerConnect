#pragma once
#include <Arduino.h>
#include <NimBLEDevice.h>

#define ENVIRONMENT_UUID "cac36b81-1245-4f86-a437-001dc1b86a02"

struct EnvironmentReading {
    float celsius = NAN;
    float humidity = NAN;
    float pressureHpa = NAN;
    uint32_t sampledAt = 0;
    uint16_t sequence = 0;
    bool valid = false;
    bool simulated = false;
};

void pollEnvironment();
EnvironmentReading environmentReading();
void addEnvironmentCharacteristic(NimBLEService* service);

void environmentDiagnostics();
#ifdef RYKER_SENSOR_TEST
String environmentTestClock();
void environmentTestSetClock(uint8_t hour, uint8_t minute, uint8_t second);
#endif
