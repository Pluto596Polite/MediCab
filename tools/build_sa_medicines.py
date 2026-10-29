#!/usr/bin/env python3
"""
Builds app/src/main/assets/sa_medicines.tsv from the National Department of
Health's "Database of Medicine Prices" spreadsheet (every medicine registered
for sale in South Africa, with its ingredients, strength, form and pack size).

Download the latest .xlsx from https://www.health.gov.za/nhi-pee/ and run:

    python3 tools/build_sa_medicines.py Database_Of_Medicine_Prices_<date>.xlsx

Only the standard library is used. Hospital-only products (infusions, stents,
dialysis fluids, bone cement and the like) are left out, and the dosage form
and ATC code are mapped onto the app's own form, category and 3D pack template
names so the app can use each row as-is.

Popular products that aren't registered medicines (complementary medicines,
supplements) are added from tools/sa_medicines_extra.tsv, kept by hand in the
output's column order.
"""
import re
import sys
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path

NS = '{http://schemas.openxmlformats.org/spreadsheetml/2006/main}'
OUT = Path(__file__).resolve().parent.parent / 'app/src/main/assets/sa_medicines.tsv'
EXTRA = Path(__file__).resolve().parent / 'sa_medicines_extra.tsv'

# Spreadsheet columns used.
COL_NAPPI, COL_ATC, COL_SCHEDULE, COL_NAME = 'D', 'E', 'F', 'G'
COL_INGREDIENT, COL_STRENGTH, COL_UNIT, COL_FORM, COL_PACK, COL_STATUS = 'H', 'I', 'J', 'K', 'L', 'T'

# Dosage forms nobody keeps at home.
HOSPITAL_FORMS = {'INF', 'DES', 'DEB', 'TPN', 'DIA', 'CES', 'BDL', 'IMP', 'COM', 'SLN', 'LIT', 'OPI', 'POR', 'VIA', 'IV'}

FORMS = {
    'Tablet': {'TAB', 'TABS', 'TABLET', 'FCT', 'FCR', 'SRT', 'SRC', 'ERT', 'MRT', 'EFT', 'CHU', 'CHW', 'DSP', 'ODT',
               'ECT', 'OCT', 'SLT', 'CAS'},
    'Capsule': {'CAP', 'CAPS', 'CAPSULES', 'CPS', 'CAB'},
    'Liquid': {'SYR', 'SUS', 'SUSP', 'SOL', 'SOLUTION', 'ORAL SOLUTION', 'LIQ', 'MOW'},
    'Cream': {'CRE', 'CREAM', 'CRM', 'CRO', 'OIN', 'OIO', 'GEL', 'JEL', 'GEO', 'VCR', 'VAG', 'LOT', 'LTN', 'FOA', 'FAO',
              'MLK', 'PAS', 'PSD', 'SHA', 'SHAMPOO', 'EYO', 'OPG'},
    'Drops': {'OPD', 'DRP', 'DRO', 'NOD', 'EAR', 'EED', 'EEN', 'ENS', 'NAS', 'NSS', 'NAG', 'MDS', 'SPO', 'SPR',
              'AQUEOUS NASAL SPRAY'},
}
FORM_OF = {code: form for form, codes in FORMS.items() for code in codes}

# Creams and gels come in tubes, but these come in bottles.
BOTTLED = {'LOT', 'LTN', 'SHA', 'SHAMPOO', 'MLK', 'FOA', 'FAO', 'SCL'}
# Lozenges, gum and sachets come in boxes.
BOXED = {'LOZ', 'CHG', 'SAC', 'POS'}

# ATC prefixes, most specific first, to the app's categories.
CATEGORIES = [
    ('R01AD', 'Allergy'), ('S01G', 'Allergy'), ('R06', 'Allergy'),
    ('R01', 'Cold & flu'), ('R02', 'Cold & flu'), ('R05', 'Cold & flu'),
    ('N02', 'Pain relief'),
    ('M01', 'Inflammation'), ('M02', 'Inflammation'),
    ('A02', 'Digestive'), ('A03', 'Digestive'), ('A04', 'Digestive'), ('A06', 'Digestive'), ('A07', 'Digestive'),
    ('A11', 'Vitamins'), ('A12', 'Vitamins'), ('B03', 'Vitamins'),
    ('D03', 'First aid'), ('D08', 'First aid'), ('D09', 'First aid'),
    ('D', 'Skin care'),
]


def read_rows(path):
    """Yields each spreadsheet row as {column letter: text}."""
    with zipfile.ZipFile(path) as z:
        shared = [''.join(t.text or '' for t in si.iter(NS + 't'))
                  for si in ET.fromstring(z.read('xl/sharedStrings.xml')).findall(NS + 'si')]
        sheet = ET.fromstring(z.read('xl/worksheets/sheet1.xml')).find(NS + 'sheetData')
        for row in sheet.findall(NS + 'row'):
            cells = {}
            for c in row.findall(NS + 'c'):
                v = c.find(NS + 'v')
                value = v.text if v is not None else ''
                if c.get('t') == 's':
                    value = shared[int(value)]
                cells[re.sub(r'\d', '', c.get('r'))] = value.strip()
            yield cells


def number(text):
    """'1.2E-2' -> '0.012', '20.0' -> '20', '2,5' -> '2.5'."""
    text = text.replace(',', '.')
    try:
        value = float(text)
    except ValueError:
        return text
    return f'{value:.4f}'.rstrip('0').rstrip('.') if value % 1 else str(int(value))


def unit(text):
    u = re.sub(r'\s+', ' ', text.strip())
    lower = u.lower()
    if lower in ('', 'zzz'):
        return None
    if lower in ('mcg', 'µg', 'μg', 'ug'):
        return 'mcg'
    if lower == 'iu':
        return 'IU'
    if lower == 'mg':
        return 'mg'
    # Match the app's own spelling, e.g. "mg/5 ml".
    return re.sub(r'/(\d+)\s*ml', r'/\1 ml', lower.replace('μ', 'mc').replace('µ', 'mc'))


def strength(amount, unit_text):
    u = unit(unit_text)
    if not amount or u is None:
        return None
    n = number(amount)
    return f'{n}%' if u == '%' else f'{n} {u}'


def tidy_name(name):
    name = re.sub(r'[®™*]', '', name)
    name = re.sub(r'\s+', ' ', name).strip(' -,')
    if len(name) > 4 and name == name.upper():
        name = ' '.join(w.capitalize() if w.isalpha() else w for w in name.split(' '))
    return name


def tidy_ingredient(text):
    text = re.sub(r'\s+', ' ', text).strip(' ,')
    if text == text.upper():
        text = text.capitalize()
    return text[:1].upper() + text[1:]


def category(atc, schedule):
    for prefix, name in CATEGORIES:
        if atc.upper().startswith(prefix):
            return name
    digits = re.findall(r'\d', schedule)
    return 'Prescription' if digits and int(max(digits)) >= 3 else ''


def template(code, form, name, pack):
    """The app's PackTemplate for this pack, or blank to let the app guess."""
    if code == 'EFT' or 'effervescent' in name.lower():
        return 'FIZZ_TUBE'
    if form in ('Tablet', 'Capsule') or code in BOXED:
        return 'CARTON'
    if form in ('Liquid', 'Drops') or code in BOTTLED:
        return 'SYRUP'
    if form == 'Cream':
        return 'JAR' if pack >= 200 else 'CREAM_TUBE'
    return ''


def pack_size(text):
    try:
        value = float(text.replace(',', '.'))
    except ValueError:
        return 0
    return int(value) if value.is_integer() and 1 <= value <= 999 else 0


def build(path):
    products = []
    for row in read_rows(path):
        if row.get(COL_NAPPI, '').lower().startswith('nappi'):
            continue  # header
        if row.get(COL_NAPPI):
            products.append({'row': row, 'parts': []})
        if products and row.get(COL_INGREDIENT):
            products[-1]['parts'].append((tidy_ingredient(row[COL_INGREDIENT]),
                                          strength(row.get(COL_STRENGTH, ''), row.get(COL_UNIT, ''))))

    seen, lines = set(), []
    for p in products:
        row = p['row']
        code = re.sub(r'\s+', ' ', row.get(COL_FORM, '')).upper()
        name = tidy_name(row.get(COL_NAME, ''))
        if code in HOSPITAL_FORMS or not name:
            continue
        ingredients = ' + '.join(dict.fromkeys(i for i, _ in p['parts'] if i))
        strengths = ' + '.join(s for _, s in p['parts'] if s)
        form = FORM_OF.get(code, '')
        schedule = re.sub(r'\s+', '', row.get(COL_SCHEDULE, '')).upper()
        pack = pack_size(row.get(COL_PACK, ''))
        record = (
            name, ingredients, strengths, form, str(pack),
            schedule, category(row.get(COL_ATC, ''), schedule),
            '1' if 'discontinued' in row.get(COL_STATUS, '').lower() or 'discountinued' in row.get(COL_STATUS, '').lower() else '0',
            template(code, form, name, pack),
        )
        key = record[:5]
        if key in seen:
            continue
        seen.add(key)
        lines.append('\t'.join(re.sub(r'\s+', ' ', field).strip() for field in record))

    for line in EXTRA.read_text(encoding='utf-8').splitlines():
        if line.strip() and not line.startswith('#'):
            fields = line.split('\t')
            assert len(fields) == 9, f'{EXTRA.name}: expected 9 columns in {line!r}'
            lines.append(line)

    lines.sort(key=str.lower)
    header = f'# South African medicines, from {Path(path).name} and {EXTRA.name}. ' \
             'name, ingredients, strength, form, pack size, schedule, category, discontinued, template'
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(header + '\n' + '\n'.join(lines) + '\n', encoding='utf-8')
    print(f'Wrote {len(lines)} medicines to {OUT}')


if __name__ == '__main__':
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    build(sys.argv[1])
