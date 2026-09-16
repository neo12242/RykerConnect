#include "func_u8g2.h"

void initDisplays(uint8_t brightness) {
  pinMode(14, OUTPUT); digitalWrite(14, HIGH);
  pinMode(10, OUTPUT); digitalWrite(10, HIGH);
  #ifdef RYKER_REV02
  // One physical SOG320132A panel, two controllers sharing active-low GPIO15.
  // Both U8g2 constructors deliberately keep reset=-1: reset both only once.
  digitalWrite(15, HIGH); pinMode(15, OUTPUT);
  delay(10); digitalWrite(15, LOW); delay(10);
  digitalWrite(15, HIGH); delay(100);
  #endif
  delay(50);
  // First init with slow SPI clock for reliable startup
  u8g2_0.setBusClock(8000000);
  u8g2_1.setBusClock(8000000);
  u8g2_0.begin();
  u8g2_1.begin();
  delay(50);
  #ifndef RYKER_REV02
  // Legacy REV01 behavior; REV02 keeps the conservative 8MHz clock.
  u8g2_0.setBusClock(60000000);
  u8g2_1.setBusClock(60000000);
  u8g2_0.begin();
  u8g2_1.begin();
  #endif
  u8g2_0.setContrast(brightness);
  u8g2_1.setContrast(brightness);
  u8g2_0.enableUTF8Print();
  u8g2_1.enableUTF8Print();
}

void setLeftSide() {
  u8g2_current = &u8g2_0;
  offset = 0;
}
void setRightSide() {
  u8g2_current = &u8g2_1;
  offset = 160;
}

void drawStr(u8g2_uint_t x, u8g2_uint_t y, const char *s) {
  u8g2_current->drawStr(x-offset,y,s);
}

void drawUTF8(u8g2_uint_t x, u8g2_uint_t y, const char *s){
  u8g2_current->drawUTF8(x-offset,y,s);
}

void drawXBMP(u8g2_uint_t x, u8g2_uint_t y, u8g2_uint_t w, u8g2_uint_t h, const uint8_t *bitmap){
  u8g2_current->drawXBMP(x-offset,y,w,h,bitmap);
}

void drawCircle(u8g2_uint_t x0, u8g2_uint_t y0, u8g2_uint_t rad){
  u8g2_current->drawCircle(x0-offset, y0, rad);
}

void drawDisc(u8g2_uint_t x, u8g2_uint_t y, u8g2_uint_t r){
  u8g2_current->drawDisc(x-offset,y,r);
}

void drawTriangle(int16_t x0, int16_t y0, int16_t x1, int16_t y1, int16_t x2, int16_t y2){
  u8g2_current->drawTriangle(x0-offset, y0, x1-offset, y1,x2-offset,y2);
}

void drawBox(u8g2_uint_t x, u8g2_uint_t y, u8g2_uint_t w, u8g2_uint_t h){
  u8g2_current->drawBox(x-offset, y, w, h);
}

void drawRBox(u8g2_uint_t x, u8g2_uint_t y, u8g2_uint_t w, u8g2_uint_t h, u8g2_uint_t r){
  u8g2_current->drawRBox(x-offset, y, w, h, r);
}

void drawRFrame(u8g2_uint_t x, u8g2_uint_t y, u8g2_uint_t w, u8g2_uint_t h, u8g2_uint_t r){
  u8g2_current->drawRFrame(x-offset, y, w, h, r);
}

