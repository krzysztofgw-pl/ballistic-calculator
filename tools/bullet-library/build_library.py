"""Builds app/src/main/resources/bullets.csv from the ammolytics dataset, the Berger 2024 catalogue and curated overrides."""
import csv, re, sys
projectiles, berger_csv, out = sys.argv[1:4]

def clean(s):
    s = re.sub(r'[®™]', '', s)
    return re.sub(r'\s+', ' ', s).strip()

def num(s):
    s = (s or '').strip()
    return float(s) if s and s.lower() not in ('null', 'n/a', 'na') else None

rows = {}
def add(maker, name, dia, wt, g1, g7, length=None, twist=None, source=''):
    if not g1 and not g7:
        return
    key = (maker, name.lower(), round(dia, 4), round(wt, 1))
    rows[key] = dict(maker=maker, name=name, diameter_in=dia, weight_gr=wt, g1=g1, g7=g7,
                     length_in=length, twist_in=twist, source=source)

LAPUA_NAMES = {'ScenarL': 'Scenar-L', 'Scenar L': 'Scenar-L'}
for r in csv.DictReader(open(projectiles, newline='')):
    if r['type'] != 'rifle' or r['company'] in ('Berger', 'Sierra'):
        continue
    name = clean(r['product_name']) or clean(r['name'])
    if r['company'] == 'Lapua':
        name = LAPUA_NAMES.get(name, name)
    add(r['company'], name, float(r['diameter_in']), float(r['weight_gr']),
        num(r['bc_g1']), num(r['bc_g7']), source='ammolytics/projectiles (MIT)')

for r in csv.DictReader(open(berger_csv, newline='')):
    add('Berger', r['name'], float(r['diameter']), float(r['weight']), num(r['g1']), num(r['g7']),
        num(r['length']), num(r['twist']), source='Berger catalogue 2024')

# Current manufacturer figures that differ from or are missing in the open dataset.
CURATED = [
    ('Hornady', 'ELD Match', 0.224, 73, 0.398, 0.200, 0.96),
    ('Hornady', 'ELD Match', 0.224, 75, 0.467, 0.235, 1.00),
    ('Hornady', 'ELD Match', 0.243, 108, 0.536, 0.270, 1.24),
    ('Hornady', 'ELD Match', 0.264, 140, 0.646, 0.326, 1.39),
    ('Hornady', 'ELD Match', 0.264, 147, 0.697, 0.351, 1.43),
    ('Hornady', 'ELD-X', 0.264, 143, 0.625, 0.315, 1.38),
    ('Hornady', 'ELD Match', 0.308, 178, 0.547, 0.275, 1.34),
    ('Hornady', 'ELD-X', 0.308, 178, 0.552, 0.278, 1.36),
    ('Hornady', 'ELD Match', 0.308, 208, 0.670, 0.337, 1.48),
    ('Hornady', 'ELD Match', 0.338, 285, 0.789, 0.399, 1.74),
    ('Sierra', 'MatchKing HPBT', 0.224, 77, 0.372, None, 0.995),
    ('Sierra', 'MatchKing HPBT', 0.308, 168, 0.462, 0.218, 1.215),
    ('Sierra', 'MatchKing HPBT', 0.308, 175, 0.505, 0.243, 1.240),
    ('Sierra', 'MatchKing HPBT', 0.338, 300, 0.768, None, 1.70),
    # From the box of S&B 6.5 Creedmoor No. 2890; its V100-V300 table matches these BCs within 0.5 m/s.
    ('Sellier & Bellot', 'HPBT', 0.264, 142, 0.553, 0.278, None, 8),
]
for maker, name, dia, wt, g1, g7, length, *twist in CURATED:
    add(maker, name, dia, wt, g1, g7, length, twist[0] if twist else None, source='manufacturer, curated')

def f(v, d):
    return '' if v is None else (f'{v:.{d}f}'.rstrip('0').rstrip('.') if d else str(v))

ordered = sorted(rows.values(), key=lambda r: (r['diameter_in'], r['maker'], r['weight_gr'], r['name']))
with open(out, 'w', newline='') as fh:
    fh.write('# Rifle bullet library. BCs in lb/in^2 as published by the manufacturers; length in inches;\n')
    fh.write('# twist = fastest recommended twist in inches per turn. Sources: Berger catalogue 2024;\n')
    fh.write('# ammolytics/projectiles open dataset (MIT, https://github.com/ammolytics/projectiles);\n')
    fh.write('# curated current manufacturer figures. Always check against the maker\'s current data.\n')
    w = csv.writer(fh, lineterminator='\n')
    w.writerow(['maker', 'name', 'diameter_in', 'weight_gr', 'g1', 'g7', 'length_in', 'twist_in', 'source'])
    for r in ordered:
        w.writerow([r['maker'], r['name'], f(r['diameter_in'], 4), f(r['weight_gr'], 1), f(r['g1'], 3), f(r['g7'], 3),
                    f(r['length_in'], 3), f(r['twist_in'], 1), r['source']])
from collections import Counter
print(len(ordered), Counter(r['maker'] for r in ordered), sum(1 for r in ordered if r['g7']), 'g7')
