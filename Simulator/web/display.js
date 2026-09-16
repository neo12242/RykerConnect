'use strict';
const $=id=>document.getElementById(id);
const buffer=document.createElement('canvas'); buffer.width=320; buffer.height=132;
const ctx=buffer.getContext('2d');
let state=null,icons={};
const layouts=['Default','Media','Split'];
function text(value,x,y,size=12,align='left'){ctx.font=`${size}px monospace`;ctx.textAlign=align;ctx.fillText(String(value),Math.round(x),Math.round(y));ctx.textAlign='left';}
function line(x,y,a,b){ctx.beginPath();ctx.moveTo(x+.5,y+.5);ctx.lineTo(a+.5,b+.5);ctx.stroke();}
function rect(x,y,w,h){ctx.strokeRect(x+.5,y+.5,w-1,h-1);}
function box(x,y,w,h){ctx.fillRect(Math.round(x),Math.round(y),Math.round(w),Math.round(h));}
function icon(name,x,y){const a=icons[name];if(!a)return;const stride=Math.ceil(a.w/8);for(let j=0;j<a.h;j++)for(let i=0;i<a.w;i++)if(a.data[j*stride+(i>>3)]&(1<<(i&7)))box(x+i,y+j,1,1);}
function trim(value,width,size){ctx.font=`${size}px monospace`;let s=String(value);if(ctx.measureText(s).width<=width)return s;while(s.length&&ctx.measureText(s+'…').width>width)s=s.slice(0,-1);return s+'…';}
function scroll(value,x,y,size,stamp){ctx.save();ctx.beginPath();ctx.rect(x,20,320-x,112);ctx.clip();ctx.font=`${size}px monospace`;const width=ctx.measureText(value).width,overflow=Math.max(0,width-(320-x-4));const cycle=overflow/20+4;const t=(stamp/1000)%cycle;const offset=overflow?Math.min(overflow,Math.max(0,(t-2)*20)):0;text(value,x-offset,y,size);ctx.restore();}
function roundPanel(x,y,w,h){ctx.fillStyle='#000';ctx.beginPath();ctx.roundRect(x,y,w,h,8);ctx.fill();ctx.fillStyle='#fff';ctx.stroke();}
function wrapped(value,x,y,width,size,maxLines){const words=String(value).split(/\s+/);let row='';const rows=[];ctx.font=`${size}px monospace`;for(const word of words){if(row&&ctx.measureText(row+' '+word).width>width){rows.push(row);row=word;}else row+=(row?' ':'')+word;}if(row)rows.push(row);rows.slice(0,maxLines).forEach((r,i)=>text(trim(r+(i===maxLines-1&&rows.length>maxLines?' …':''),width,size),x,y+i*(size+4),size));}
function weatherIcon(w,x,y){
 ctx.save();ctx.translate(x,y);
 if(!w?.active||!w.available){text('?',8,14,14,'center');}
 else if(w.stale){text('!',8,14,15,'center');}
 else {
  const c=w.conditions.toLowerCase();
  if(c.includes('clear')){ctx.beginPath();ctx.arc(8,8,3,0,Math.PI*2);ctx.stroke();for(let i=0;i<8;i++){const a=i*Math.PI/4;line(8+Math.cos(a)*5,8+Math.sin(a)*5,8+Math.cos(a)*7,8+Math.sin(a)*7);}}
  else if(c.includes('fog')){line(1,4,15,4);line(3,8,13,8);line(1,12,15,12);}
  else {ctx.beginPath();ctx.moveTo(2,10);ctx.bezierCurveTo(-1,5,4,3,6,5);ctx.bezierCurveTo(6,0,13,1,13,5);ctx.bezierCurveTo(18,4,18,10,14,10);ctx.closePath();ctx.stroke();
   if(c.includes('thunder')){line(9,10,6,13);line(6,13,10,13);line(10,13,7,16);}
   else if(c.includes('snow')){text('*',8,18,10,'center');}
   else if(c.includes('rain')||c.includes('drizzle')){line(4,12,3,15);line(11,12,10,15);}
  }
 }
 ctx.restore();
}
function statusBar(s){
 const w=s.weather;
 const choices=s.batteryIcons.map((v,i)=>v?i:null).filter(v=>v!==null);const first=choices.indexOf(s.batteryFirst);if(first>0)choices.push(...choices.splice(0,first));
 const type=choices[Math.floor(s.elapsed/Math.max(1,s.batteryInterval))%choices.length];const level=type===0?s.phoneBattery:type===1?s.intercomBattery:null;
 icon(type===0?'phone_16':type===1?'headset_16':'unkown_device_16',3,2);box(20,2,3,2);rect(18,4,7,14);
 if(level!==null){const h=Math.round(12*level/100);box(19,17-h,5,h);text(level+'%',28,15,10);if(type===0&&s.charging)text('+',21,13,9,'center');}else text('?',21,15,10,'center');
 text(s.clock,96,16,s.clock.length>5?10:12,'center');
 weatherIcon(w,132,1);text('WX',153,6,6);text(w?.active&&w.available?w.temperature:'—',153,18,11);text('SENSOR',216,6,6);text((s.temperature===null?'—':Math.round(s.temperature)+'°'+s.temperatureUnit),216,18,11);
 icon('network'+s.networkSignal,277,2);icon(s.connected?'bluetooth_16_verbunden':'bluetooth_16',302,2);
 if(typeof s.units.musicLeft==='boolean'){
  if(w?.active&&w.available){
   text(trim((w.stale?'STALE: ':'')+w.conditions,119,8),5,30,8);
   text(trim('Wind '+w.wind,99,8),129,30,8);
   text(trim('Precip '+w.rain,81,8),233,30,8);
  }else text(trim(w?.active?'Weather: '+w.status:'Weather off',310,9),5,30,9);
  line(5,35,314,35);
 }else line(5,20,314,20);
}
function media(s,stamp){const m=s.media,x=s.layout===2?90:8,y=s.layout===2?48:34,large=s.units.large;
 if(s.layout===2){icon('musik_20',x,y-20);text('Audio',x+25,y-6,11);}scroll(m.title,x,y+24,large?22:20,stamp);scroll(m.artist,x,y+44,large?15:13,stamp);
 if(m.title||m.artist){if(m.playing){box(x,y+60,4,14);box(x+9,y+60,4,14);}else{ctx.beginPath();ctx.moveTo(x,y+60);ctx.lineTo(x+12,y+67);ctx.lineTo(x,y+74);ctx.fill();}
 const start=x+18,w=320-(x+14)-8;const time=v=>`${Math.floor(v/60)}:${String(Math.floor(v%60)).padStart(2,'0')}`;
 if(m.duration>15){box(start,y+63,w,2);ctx.beginPath();ctx.arc(start+3+(w-7)*Math.min(1,m.position/m.duration),y+64,3,0,Math.PI*2);ctx.fill();text(time(m.position),start,y+74,8);text(time(m.duration),start+w,y+74,8,'right');}else text(time(m.position),start,y+74,10);}
}
function panel(x,render,weather=false){ctx.save();ctx.beginPath();ctx.rect(x,weather?36:23,160,weather?96:109);ctx.clip();ctx.translate(x,weather?16:0);if(weather)ctx.scale(1,0.875);render();ctx.restore();}
function panelScroll(value,y,size,stamp){ctx.save();ctx.beginPath();ctx.rect(8,y-size-1,144,size+4);ctx.clip();ctx.font=`${size}px monospace`;const overflow=Math.max(0,ctx.measureText(value).width-144),cycle=overflow/16+4;const offset=overflow?Math.min(overflow,Math.max(0,((stamp/1000)%cycle-2)*16)):0;text(value,8-offset,y,size);ctx.restore();}
function musicPanel(s,stamp){const m=s.media,large=s.units.large;
 text(m.source==='Sample media'?'SAMPLE MUSIC':'MUSIC',8,33,9);text(m.playing?'PLAYING':'PAUSED',152,33,8,'right');
 if(!m.title&&!m.artist){text('No music playing',80,69,12,'center');text('Start music on phone',80,91,9,'center');return;}
 const font=large?14:12,words=(m.title||'Unknown track').split(/\s+/);let first='';ctx.font=`${font}px monospace`;while(words.length&&ctx.measureText(first+(first?' ':'')+words[0]).width<=144)first+=(first?' ':'')+words.shift();if(!first)first=words.shift()||'';
 panelScroll(first,54,font,stamp);panelScroll(words.join(' '),74,font,stamp);panelScroll(m.artist,94,11,stamp);
 const time=v=>`${Math.floor(v/60)}:${String(Math.floor(v%60)).padStart(2,'0')}`;
 rect(8,107,144,4);if(m.duration>0)box(9,108,142*Math.min(1,m.position/m.duration),2);
 text(time(m.position),8,125,9);text(m.duration>0?time(m.duration):'LIVE',152,125,9,'right');
}
function tripField(s){const t=s.trip;if(!t?.active)return 'Trip not recording';return ({distance:t.distance,time:t.duration,gps:t.gps})[s.units.drivingField]||`${t.distance} · ${t.duration}`;}
function drivingPanel(s){const n=s.navigation,t=s.trip;
 if(n?.active){
  text(n.stale?'CHECK MAPS':n.source==='Sample route'?'SAMPLE ROUTE':'GOOGLE MAPS',80,33,9,'center');
  text(n.stale?'!':({left:'←',right:'→',straight:'↑',uturn:'↶',roundabout:'↻',arrive:'✓'}[n.direction]||'…'),24,66,32,'center');
  text(trim(n.stale?'STALE':n.distance||'Distance —',102,13),52,57,13);
  wrapped(n.instruction,8,80,144,s.units.large?12:11,2);text(trim(n.arrival,144,8),8,108,8);
  line(8,114,152,114);text(trim(tripField(s),144,10),80,128,10,'center');
 }else if(t?.active){text('CURRENT TRIP',80,33,9,'center');text(trim(t.distance,144,22),80,62,22,'center');text(trim(t.duration,144,20),80,87,20,'center');text(trim(t.gps,144,9),80,108,9,'center');text(trim(tripField(s),144,9),80,128,9,'center');}
 else {text('DRIVING',80,33,9,'center');text('No active route',80,64,13,'center');text('Trip not recording',80,89,11,'center');text('Start Maps for directions',80,118,9,'center');}
}
function draw(stamp){if(state){const s=state;ctx.fillStyle='#000';ctx.fillRect(0,0,320,132);ctx.fillStyle=ctx.strokeStyle='#fff';ctx.lineWidth=1;
 const n=s.navigation,t=s.trip;
 if(typeof s.units.musicLeft==='boolean'){statusBar(s);panel(s.units.musicLeft?0:160,()=>musicPanel(s,stamp),true);panel(s.units.musicLeft?160:0,()=>drivingPanel(s),true);}
 else if(n?.active){statusBar(s);text(n.stale?'CHECK MAPS':n.source==='Sample route'?'SAMPLE ROUTE':'GOOGLE MAPS',80,39,10,'center');text(n.stale?'!':({left:'←',right:'→',straight:'↑',uturn:'↶',roundabout:'↻',arrive:'✓'}[n.direction]||'…'),80,87,43,'center');text(n.stale?'DATA STALE':n.distance,80,111,11,'center');wrapped(n.instruction,173,42,140,s.units.large?13:11,4);text(trim(n.arrival,140,9),173,124,9);}
 else if(t?.active){statusBar(s);text('RIDE DISTANCE',80,45,10,'center');text('RIDE TIME',240,45,10,'center');text(t.distance,80,78,22,'center');text(t.duration,240,78,20,'center');text(trim(t.gps,300,12),160,113,12,'center');}
 else {statusBar(s);if(s.layout===0){text(s.clock,80,77,s.clock.length>5?22:29,'center');text((s.temperature===null?'—':Math.trunc(s.temperature)+'°'+s.temperatureUnit),240,77,26,'center');}else{if(s.layout===2){text(s.clock,42,60,10,'center');text((s.temperature===null?'—':Math.trunc(s.temperature)+'°'+s.temperatureUnit),42,88,16,'center');}media(s,stamp);}}
 if(s.notification){const p=s.notification;roundPanel(14,33,292,88);rect(15,34,290,86);icon(p.app.toLowerCase().includes('whatsapp')?'whatsapp_20':p.app.toLowerCase().includes('message')?'chat_20':'notification_20',21,38);text(trim(p.app,250,14),47,53,14);text(trim(p.title,276,19),22,79,19);wrapped(p.text,22,97,276,12,2);}
 if(s.volume){roundPanel(80,74,160,52);text(s.volume.level?s.volume.level+'%':'Mute',160,94,17,'center');rect(92,102,136,10);box(94,104,132*s.volume.level/100,6);}
 if(s.lowBattery){roundPanel(70,82,180,44);text('LOW BATTERY',160,100,14,'center');text(s.lowBattery.text,160,117,12,'center');}
 if(s.pairing){roundPanel(85,84,150,44);text('PAIRING PIN',160,102,14,'center');text('123456',160,122,20,'center');}
 if(s.reinitializing){ctx.fillStyle='#000';ctx.fillRect(0,0,320,132);}
 const pixels=ctx.getImageData(0,0,320,132),light=35+220*s.brightness/255;for(let i=0;i<pixels.data.length;i+=4){const v=pixels.data[i]>100?light:0;pixels.data[i]=pixels.data[i+1]=pixels.data[i+2]=v;}ctx.putImageData(pixels,0,0);
 $('display').getContext('2d').drawImage(buffer,0,0);
 }requestAnimationFrame(draw);}
let i2cInitialized=false,i2cPending=false;
function updateUi(s){
 if(s.i2c){
 if(!i2cPending){$('bme-connected').checked=s.i2c.bme280;$('rtc-connected').checked=s.i2c.ds3231;$('sensor-stale').checked=s.i2c.stale;}
 if(!i2cInitialized){$('humidity').value=s.i2c.humidity;$('pressure').value=s.i2c.pressure;$('temperature').value=s.sensorTemperature;$('temperature-value').textContent=s.sensorTemperature+' °C';i2cInitialized=true;}
$('i2c-status').textContent=`SIMULATED BME280: ${!s.i2c.bme280?'disconnected':s.i2c.stale?'stale / frozen':`${s.temperature}°${s.temperatureUnit} · ${s.i2c.humidity}% · ${s.i2c.pressure} hPa`}`;$('rtc-status').textContent=`DS3231 ${s.i2c.ds3231?'connected':'disconnected'} · ${s.clockSource}`;}
$('scenario-status').textContent=s.scenario&&s.scenario!=='live'?`SAMPLE SCENARIO: ${s.scenario.replaceAll('_',' ')} — display only`:'Live phone data';const mode=s.navigation?.active?'Navigation':s.trip?.active?'Trip':layouts[s.layout];$('connection').textContent=(s.scenario&&s.scenario!=='live'?'SAMPLE · ':'')+(s.connected?'Bluetooth connected':'Bluetooth disconnected');$('connection').className='status'+(s.connected?' connected':'');$('layout').textContent=mode.toUpperCase();$('brightness').textContent=`Brightness ${Math.round(s.brightness/255*100)}%${s.adaptive?' · Adaptive':''}`;
 $('feed').textContent=s.navigation?.active?s.navigation.source+(s.navigation.stale?' · STALE':''):s.trip?.active?'Trip recording':s.media.title?`${s.media.source} · ${s.media.playing?'Playing':'Paused'}`:'Live · No track';
 $('last-write').textContent=s.lastWrite?`Last command: ${s.lastWrite} · ${s.lastWriteAge}s ago`:'No commands received yet';$('source').textContent=`ESP sensor: ${s.temperature===null?"unavailable":s.temperature+"°"+s.temperatureUnit} (simulated) · Media: ${s.media.source}`;$('pause').disabled=s.media.source!=='Sample media';
 $('display').setAttribute('aria-label',`${mode}: ${s.clock}, ${s.navigation?.active?s.navigation.distance+' '+s.navigation.instruction:s.trip?.active?s.trip.distance+' '+s.trip.duration:''}, ${s.temperature} degrees ${s.temperatureUnit}`);$('description').textContent=`${mode} display. Fonts, scrolling, and OLED contrast are approximations. Riding features use the simulator extension.`;
 if(typeof s.units.musicLeft==='boolean'){const music=`Music: ${s.media.title||'No music playing'} · ${s.media.artist||''}`;const driving=`Driving: ${s.navigation?.active?(s.navigation.stale?'Stale directions: ':'')+s.navigation.instruction:'No active route'} · ${s.trip?.active?s.trip.distance+' · '+s.trip.duration:'Trip not recording'}`;$('display').setAttribute('aria-label',s.units.musicLeft?`${music}; ${driving}`:`${driving}; ${music}`);$('layout').textContent=s.units.musicLeft?'MUSIC | DRIVING':'DRIVING | MUSIC';$('feed').textContent=`${s.media.source} · ${s.media.playing?'Playing':'Paused'} | ${s.navigation?.active?s.navigation.source:s.trip?.active?'Trip recording':'Driving ready'}`;if(s.weather?.active)$('display').setAttribute('aria-label',$('display').getAttribute('aria-label')+'; GPS forecast: '+(s.weather.stale?'stale; ':'')+s.weather.status+'; '+s.weather.temperature+' '+s.weather.conditions+'; wind '+s.weather.wind+'; precipitation chance '+s.weather.rain);$('description').textContent='One continuous display with music and driving side by side. Top bar: WX is the GPS-based forecast temperature; SENSOR is the ESP sensor temperature. The sensor is simulated here. Trip time includes stops. Swap sides in the Android riding settings.';}}
async function poll(){try{const response=await fetch('/state');if(!response.ok)throw Error('Display unavailable');state=await response.json();updateUi(state);$('error').hidden=true;}catch(e){$('error').hidden=false;$('error').textContent='Simulator unavailable. Start the RykerConnect simulator to reconnect.';$('connection').textContent='Simulator offline';$('connection').className='status';if(state){state.connected=false;if(state.navigation)state.navigation.stale=true;if(state.trip)state.trip.gps='Trip data stale';if(state.weather)state.weather.stale=true;}}finally{setTimeout(poll,250);}}
async function demo(data){try{const response=await fetch('/demo',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(data)});if(!response.ok)throw Error('Sample control failed');}catch(e){$('error').hidden=false;$('error').textContent=e.message;}}
let sensorTimer,volumeTimer;for(const id of ['temperature','ambient'])$(id).addEventListener('input',()=>{$('temperature-value').textContent=$('temperature').value+' °C';$('ambient-value').textContent=$('ambient').value+' ADC';clearTimeout(sensorTimer);sensorTimer=setTimeout(()=>demo({action:'sensors',temperature:Number($('temperature').value),ambient:Number($('ambient').value)}),120);});
 $('sample-media').onclick=()=>demo({action:'media',title:$('title').value,artist:$('artist').value});for(const action of ['pause','live','notification','battery'])$(action).onclick=()=>demo({action});$('volume').oninput=()=>{$('volume-value').textContent=$('volume').value+'%';clearTimeout(volumeTimer);volumeTimer=setTimeout(()=>demo({action:'volume',level:Number($('volume').value)}),80);};
fetch('/icons.json').then(r=>r.json()).then(v=>icons=v).catch(()=>{$('error').hidden=false;$('error').textContent='Display icons unavailable';});poll();requestAnimationFrame(draw);

document.querySelectorAll("[data-scenario]").forEach(button=>button.onclick=()=>demo({action:"scenario",name:button.dataset.scenario}));

async function applyI2c(){if(!$('humidity').reportValidity()||!$('pressure').reportValidity())return;i2cPending=true;try{await demo({action:'i2c',bme280:$('bme-connected').checked,ds3231:$('rtc-connected').checked,stale:$('sensor-stale').checked,humidity:Number($('humidity').value),pressure:Number($('pressure').value)});}finally{i2cPending=false;}}
for(const id of ['bme-connected','rtc-connected','sensor-stale'])$(id).onchange=applyI2c;
$('apply-i2c').onclick=applyI2c;
