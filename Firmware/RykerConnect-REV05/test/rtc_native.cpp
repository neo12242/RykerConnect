#include "pcf8563_clock.h"
#include <cassert>
#include <vector>
#include <cstdio>
struct FakeWire {
    uint8_t regs[16]{}; std::vector<uint8_t> tx,rx; size_t cursor=0;
    unsigned calls=0, failAt=0; bool shortRead=false;
    void beginTransmission(uint8_t address) { assert(address==0x51);tx.clear(); }
    void write(uint8_t b) {tx.push_back(b);}
    int endTransmission(bool=true) {
        ++calls;if(calls==failAt)return 2;
        cursor=tx[0];for(size_t i=1;i<tx.size();++i)regs[cursor++]=tx[i];
        if(tx.size()==1)cursor=tx[0];return 0;
    }
    size_t requestFrom(uint8_t address,uint8_t n) {
        assert(address==0x51);rx.clear();if(shortRead)--n;
        for(unsigned i=0;i<n;++i)rx.push_back(regs[cursor++]);cursor=0;return n;
    }
    size_t available(){return rx.size()-cursor;}
    int read(){return rx[cursor++];}
};
int main(){
    FakeWire bus;Pcf8563Clock<FakeWire> clock(bus);uint8_t h=99,m=99,s=99;
    assert(clock.set(23,59,59));assert(clock.read(h,m,s));assert(h==23&&m==59&&s==59);
    assert(clock.set(0,0,0));assert(clock.read(h,m,s));assert(h==0&&m==0&&s==0);
    unsigned before=bus.calls;assert(!clock.set(24,0,0));assert(!clock.set(12,60,0));assert(!clock.set(12,0,60));assert(bus.calls==before);
    bus.regs[2]=0x80;assert(!clock.read(h,m,s)); // battery integrity lost
    bus.regs[2]=0x1a;assert(!clock.read(h,m,s)); // malformed BCD
    bus.regs[2]=0;bus.regs[0]=0x20;assert(!clock.read(h,m,s));
    bus.regs[0]=0;bus.regs[4]=0x24;assert(!clock.read(h,m,s));
    bus.regs[4]=0;bus.shortRead=true;assert(!clock.read(h,m,s));assert(!bus.available());bus.shortRead=false;
    bus.failAt=bus.calls+1;assert(!clock.read(h,m,s));
    bus.failAt=bus.calls+2;assert(!clock.set(12,34,56));assert(bus.regs[0]&0x20);
    assert(!clock.read(h,m,s));bus.failAt=0;assert(clock.set(12,34,56));assert(clock.read(h,m,s));assert(h==12&&m==34&&s==56);
    std::puts("PASS: RTC time boundaries, BCD, voltage-loss/STOP flags, I2C failures and recovery");
}
