#!/usr/bin/env python3
"""Execute SQL produced by the actual OfferHistoryFilters Java class in SQLite.

No Android SDK required. Confirms that legacy/unverified readings never pass
hourly/distance conditions, but can pass OR if the payout itself qualifies.
"""
from pathlib import Path
import sqlite3
import subprocess
import tempfile

BASE=Path(__file__).resolve().parents[1]
JAVA=BASE/'app/src/main/java/com/personal/offerguardian/OfferHistoryFilters.java'
EMITTER=BASE/'tests/OfferHistoryFilterSqlDump.java'

with tempfile.TemporaryDirectory() as temp:
    subprocess.run(['javac','-d',temp,str(JAVA),str(EMITTER)],check=True)
    result=subprocess.run(['java','-cp',temp,'com.personal.offerguardian.OfferHistoryFilterSqlDump'],check=True,capture_output=True,text=True).stdout
    con=sqlite3.connect(':memory:')
    con.execute('CREATE TABLE offers (id INTEGER PRIMARY KEY, payout_cents INTEGER, minutes INTEGER, metres INTEGER, extraction_status TEXT)')
    con.executemany('INSERT INTO offers VALUES (?,?,?,?,?)', [
        (1,2710,61,6800,'VERIFIED'),
        (2,2966,67,9800,'VERIFIED'),
        (3,4100,61,6800,'LEGACY_UNVERIFIED'),
        (4,1100,10,1600,'VERIFIED'),
        (5,2000,20,4500,'CORRECTED'),
        (6,3000,0,0,'VERIFIED'),
        (7,2710,61,6800,'LEGACY_UNVERIFIED'),
        (8,3500,5,2000,'VERIFIED'),
        (9,2710,0,6800,'HOUR_BOUND'),  # provable decline, but exact rate UNKNOWN
    ])
    expected={
        'unfiltered': [1,2,3,4,5,6,7,8,9],
        'price_range': [1,2,5,6,7,8,9],
        'hourly_range': [1],
        'hourly_max': [1,2],
        'perkm_range': [1],
        'distance_range': [1,2],
        'price_hourly_and': [],
        'price_hourly_or': [3,4,5,8],
        'price_or_unverified': [1,2,4,5,6,7,8,9],
    }
    counter=0
    for row in result.splitlines():
        label, where, args=row.split('\t')
        bind=args.split(',') if args else []
        got=[r[0] for r in con.execute('SELECT id FROM offers WHERE '+where+' ORDER BY id', bind)]
        if got!=expected[label]:
            raise AssertionError(f'{label}: got {got}, expected {expected[label]}, SQL={where}, args={bind}')
        counter+=1
    if counter!=len(expected):
        raise AssertionError('Missing SQL test cases')
    print(f'PASS history filter SQLite integration ({counter} scenarios)')
