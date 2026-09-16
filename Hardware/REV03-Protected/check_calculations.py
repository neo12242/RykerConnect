"""Independent worst-case DC/ramp checks; not a transient or thermal simulation."""
import itertools,json
from pathlib import Path
R=Path(__file__).resolve().parent
def limits(tol):
 ov=[];uv=[]
 for signs in itertools.product([-1,1],repeat=3):
  a,b,c=[n*(1+sgn*tol) for n,sgn in zip([120000,20000,10000],signs)]
  # Divider loading solved directly for independent +/-100nA pin currents.
  for iu,io in itertools.product([-1e-7,1e-7],repeat=2):
   # At OVP threshold vo, vu = vo + b*(vo/c + io).
   for vo in [1.17,1.225]:
    vu=vo+b*(vo/c+io);ov.append(vu+a*(vo/c+io+iu))
   # At UVLO threshold vu, vo=(vu/b-io)/(1/b+1/c).
   for vu in [1.17,1.225]:
    vo=(vu/b-io)/(1/b+1/c);uv.append(vu+a*((vu-vo)/b+iu))
 return dict(ov_min=min(ov),ov_max=max(ov),uv_min=min(uv),uv_max=max(uv))
room=limits(.01);conservative=limits(.025)
assert conservative['ov_min']>16 and conservative['ov_max']<20
assert conservative['uv_max']<6.6
current=[.92/1.01,1.08/.99] # Conservative device +/-8%, plus resistor tolerance.
assert current[0] > 3.3/(5.5*.70)
dvdt_nom=4.7e-6/10e-9*24.6
dvdt_max=5.5e-6/9e-9*25.5
report={'method':'DC worst-case corner enumeration, independent OVP/UVLO leakage; datasheet reference thresholds. No physical simulation or certification.',
'nominal':{'ov_cutoff_V':1.19*15,'uv_start_V':1.19*5,'ov_recovery_V':1.1*15,'current_limit_A':1.0,'12V_ramp_ms':12/dvdt_nom*1000},
'1pct_resistors':room,'2_5pct_resistor_envelope':conservative,
'current_limit_conservative_A':current,'max_capacitive_inrush_A_13uF':13e-6*dvdt_max,
'TVS_25C_table_checks':{'positive_clamp_with_3_5V_forward_allowance':53.3+3.5,'negative_clamp_with_3_5V_forward_allowance':29.2+3.5,'worst_negative_IN_OUT_difference':29.2+3.5+conservative['ov_max'],'absolute_IN_OUT_limit_V':60,'caveat':'At specified pulse currents only. Clamp voltage/energy at actual temperature, source impedance, pulse shape and layout overshoot must be measured; no unsuppressed load-dump claim.'},
'supply_envelope':{'normal_V':[9,16],'suppressed_load_dump_target_V':35,'reverse_connection_target_V':-16},
'thermal_notes':'Isolated bottom RTN copper has no through-board thermal vias to avoid disturbing top routing. Validate temperature at intended load; controller is an industrial part, not automotive-qualified. Use the existing fused accessory branch; onboard eFuse does not protect a shorted input TVS or upstream wiring.'}
assert report['TVS_25C_table_checks']['positive_clamp_with_3_5V_forward_allowance']<60
assert report['TVS_25C_table_checks']['worst_negative_IN_OUT_difference']<60
(R/'validation/protection-calculations.json').write_text(json.dumps(report,indent=2))
print(json.dumps(report,indent=2))
