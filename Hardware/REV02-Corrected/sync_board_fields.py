"""Synchronize purchasing and electrical pad metadata; never change copper here."""
from pathlib import Path
import json
import pcbnew as p
from sexpr import read,fields,field,prop
root=Path(__file__).resolve().parent
b=p.LoadBoard(str(root/'RykerConnect.kicad_pcb'))
symbols={}
for name in ['RykerConnect.kicad_sch','power.kicad_sch']:
    for s in fields(read(root/name),'symbol'):symbols[str(prop(s,'Reference')[2])]=s
for f in b.GetFootprints():
    r=f.GetReference()
    if r not in symbols:continue
    s=symbols[r]
    f.SetValue(str(prop(s,'Value')[2]))
    for key in ['MPN','Manufacturer','LCSC','PROD_ID','Datasheet','Description']:
        v=prop(s,key)
        if v:
            f.SetField(key,str(v[2]));f.GetField(key).SetVisible(False)
    if r=='U5':
        for pad in f.Pads():pad.SetPinType({'1':'passive','2':'power_in','3':'input','4':'input','5':'power_in','6':'power_out'}[pad.GetNumber()])
    if r=='J5':
        for pad in f.Pads():
            if pad.GetNumber()!='MP':pad.SetPinFunction('Pin_'+pad.GetNumber());pad.SetPinType('passive')
    p.PCB_IO_KICAD_SEXPR().FootprintSave(str(root/'RykerConnect.pretty'),f)
p.SaveBoard(str(root/'RykerConnect.kicad_pcb'),b)
print('Synchronized board and local footprint fields')
