#pragma once
#include <Arduino.h>
String rtcClockString();
bool rtcSetTime(uint8_t hour, uint8_t minute, uint8_t second);
