"""Public JLCPCB browser observations, 2026-09-13. Not a reservation or assembly quote."""
from pathlib import Path
import json,collections
R=Path(__file__).resolve().parent
observed=[('C105575',3931993,.0055),('C114607',1258,.0061),('C114659',1135903,.0060),('C130253',13120,.7357),('C13585',2763665,.2658),('C13832',1317397,.0692),('C151256',7768,.1480),('C15578',1808,.3744),('C15759',52247,.0831),('C1589',2563451,.0101),('C1591',1757736,.0104),('C160402',59842,.1320),('C160404',49327,.2136),('C160407',126467,.3320),('C1653',2274360,.0067),('C192562',4584,.5778),('C2071056',21849,.4265),('C224019',10812,.1202),('C23162',24092243,.0030),('C25804',20655586,.0027),('C28323',2894122,.0399),('C29780898',1927,.0173),('C3019921',5194,.0258),('C408447',39717,.1317),('C45783',4240524,.2458),('C544399',4850,1.6227),('C7440',48054,.6201),('C7500786',52,.3274),('C7501066',374,1.1344),('C96446',4042429,.0552)]
allcounts=collections.Counter()
for v in ['12V','USB']:
 parts=json.loads((R/('REV05-'+v)/'design.json').read_text());counts=collections.Counter(a['code'] for a in parts);allcounts.update(counts)
 data={'observed_date':'2026-09-13','source':'Live public JLCPCB part-detail pages, browser rendered available-order quantity and tier-1 prices','not_reserved':True,'parts':[{'code':code,'mpn':next(a['mpn'] for a in parts if a['code']==code),'available_order_quantity':qty,'minimum_purchase':1,'unit_price_usd_tier1':price,'source':'https://jlcpcb.com/partdetail/'+code} for code,qty,price in observed if code in counts]}
 data['per_board_components_usd_tier1']=round(sum(counts[c]*pr for c,q,pr in observed if c in counts),4)
 assert all(q>=counts[c]*5 for c,q,pr in observed)
 (R/('REV05-'+v)/'validation/stock-check.json').write_text(json.dumps(data,indent=2))
 print(v,data['per_board_components_usd_tier1'])
assert all(q>=allcounts[c]*5 for c,q,pr in observed)
