from pathlib import Path
import re
import pcbnew as p
R=Path(__file__).resolve().parent
b=p.LoadBoard(str(R/'RykerConnect.kicad_pcb'))
for t in b.GetTracks():t.SetLocked(True)
p.SaveBoard(str(R/'RykerConnect.kicad_pcb'),b)
assert p.ExportSpecctraDSN(b,str(R/'validation/protection.dsn'))
f=R/'validation/protection.dsn';s=f.read_text()
s=s.replace('(width 150)','(width 250)').replace('(clearance 37.5 (type smd_smd))','(clearance 150 (type smd_smd))')
s=s.replace('(class kicad_default +12V +12V_PROTECTED ','(class kicad_default ')
controls=['PROT_DVDT','PROT_ILIM','PROT_OV','PROT_RTN','PROT_UV','TVS_MID']
match=re.search(r'\(class kicad_default[\s\S]*?\(circuit',s);old=match.group();new=old
for net in controls:new=re.sub(r'\b'+net+r'\b','',new)
s=s.replace(old,new)
s=s.replace('(use_via "Via[0-1]_500:300_um")','(use_via "Via[0-1]_600:300_um")')
s=s.replace('    )\n  )\n  (wiring','    )\n    (class power_protection +12V +12V_PROTECTED (circuit (use_via "Via[0-1]_800:300_um")) (rule (width 500) (clearance 150)))\n    (class protection_control PROT_DVDT PROT_ILIM PROT_OV PROT_RTN PROT_UV (circuit (use_via "Via[0-1]_600:300_um")) (rule (width 250) (clearance 150)))\n    (class tvs_return TVS_MID (circuit (use_via "Via[0-1]_800:300_um")) (rule (width 800) (clearance 150)))\n  )\n  (wiring')
f.write_text(s)
print('Prepared DSN: existing routing fixed; 0.5mm accessory power, 0.25mm control; 0.15mm clearance.')
