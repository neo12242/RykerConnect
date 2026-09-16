#include "rtc_clock.h"
#include "pcf8563_clock.h"
#include <Wire.h>

String rtcClockString() {
    Pcf8563Clock<TwoWire> clock(Wire);
    uint8_t hour, minute, second;
    if (!clock.read(hour, minute, second)) return "--:--";
    char text[6];
    snprintf(text, sizeof(text), "%02u:%02u", unsigned(hour), unsigned(minute));
    return String(text);
}

bool rtcSetTime(uint8_t hour, uint8_t minute, uint8_t second) {
    Pcf8563Clock<TwoWire> clock(Wire);
    return clock.set(hour, minute, second);
}
