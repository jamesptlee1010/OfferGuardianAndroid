#!/usr/bin/env python3
"""Execute ORDER BY from production Java with real SQLite and History filters.

Tests verified-only hourly sorting, legacy/HOUR_BOUND/COUNT_ONLY last,
exact computed ratios (no rounded labels), tie breaks, pagination, and
composing order with date, search, outcome and Advanced Filters.
"""
from pathlib import Path
import sqlite3
import subprocess
import tempfile

base = Path(__file__).resolve().parents[1]
source = base / 'app/src/main/java/com/personal/offerguardian'
with tempfile.TemporaryDirectory() as tmp:
    subprocess.run(['javac', '-d', tmp,
                    str(source/'OfferHistorySort.java'),
                    str(source/'OfferHistoryFilters.java'),
                    str(base/'tests/OfferHistorySortSqlDump.java')], check=True)
    proc = subprocess.run(['java', '-cp', tmp, 'com.personal.offerguardian.OfferHistorySortSqlDump'],
                          text=True, capture_output=True, check=True)
    lines = [row.split('\t') for row in proc.stdout.splitlines()]
    orders = {r[0]: r[1] for r in lines[:-1]}
    where, arg = lines[-1][1:]
    con = sqlite3.connect(':memory:')
    con.execute('''CREATE TABLE offers(id INTEGER PRIMARY KEY, seen_at INTEGER,
          payout_cents INTEGER, minutes INTEGER, metres INTEGER, extraction_status TEXT,
          outcome TEXT, pickup TEXT)''')
    data = [
        (1, 1100, 1922, 43, 14700, 'VERIFIED', 'UNKNOWN', 'Yiros'),
        (2, 1100, 2966, 67, 9800, 'VERIFIED', 'ACCEPTED', 'Coles Kedron'),
        (3, 1300, 3603, 65, 14400, 'CORRECTED', 'DECLINED', 'Coles Chermside'),
        (4, 1400, 500, 11, 2300, 'VERIFIED', 'DECLINED', 'Hane Sushi'),
        (5, 1500, 2710, 61, 6800, 'VERIFIED', 'UNKNOWN', 'Coles Lutwyche'),
        (6, 1600, 5000, 0, 8000, 'HOUR_BOUND', 'DECLINED', 'Coles Hour Bound'),
        (7, 1700, 4000, 4, 8000, 'LEGACY_UNVERIFIED', 'UNKNOWN', 'Coles Legacy'),
        (8, 1800, 1215, 27, 6900, 'VERIFIED', 'ACCEPTED', 'Sunshine'),
        (9, 1900, 1201, 27, 8200, 'VERIFIED', 'DECLINED', 'Liquorland'),
        (10, 2000, 3059, 56, 6600, 'VERIFIED', 'UNKNOWN', 'Coles Shopping'),
        (11, 1100, 2966, 67, 9800, 'VERIFIED', 'ACCEPTED', 'Coles Kedron'),
        (12, 2100, 1358, 31, 9100, 'VERIFIED', 'UNKNOWN', 'Hane Sushi'),
        (13, 2200, 9900, 0, 3000, 'COUNT_ONLY', 'UNKNOWN', 'Other'),
        (14, 2300, 99900, 0, 3000, 'VERIFIED', 'UNKNOWN', 'Other'),
        (15, 2400, 99900, 2, 3000, 'LEGACY_UNVERIFIED', 'UNKNOWN', 'Other')
    ]
    con.executemany('INSERT INTO offers VALUES (?,?,?,?,?,?,?,?)', data)
    def get(order, where='1=1', args=(), limit=100, offset=0):
        sql=f'SELECT id FROM offers WHERE {where} ORDER BY {orders[order]} LIMIT ? OFFSET ?'
        return [r[0] for r in con.execute(sql, (*args,limit,offset))]
    checks=0
    known=[d for d in data if d[5] in ('VERIFIED','CORRECTED') and d[3]>0]
    unknown=[d for d in data if d not in known]
    # all rate ordering must be based on the raw payout/duration ratio,
    # never the historically inflated OCR rates or a formatted currency string.
    for order, reverse in [('HOURLY_HIGH', True), ('HOURLY_LOW', False)]:
        # Stable tie break by seen_at DESC, id DESC.
        expected=[d[0] for d in sorted(known,key=lambda x:(
            -(x[2]/x[3]) if reverse else x[2]/x[3],-x[1],-x[0]))]
        expected += [d[0] for d in sorted(unknown,key=lambda x:(-x[1],-x[0]))]
        got=get(order)
        assert got==expected,(order,got,expected)
        checks+=1
        assert got[-5:]==[15,14,13,7,6],(order,got[-5:]);checks+=1
    for order, sortkey in [
        ('NEWEST',lambda d:(-d[1],-d[0])),
        ('PAYMENT_HIGH',lambda d:(-d[2],-d[1],-d[0])),
        ('PAYMENT_LOW',lambda d:(d[2],-d[1],-d[0]))]:
        expected=[d[0] for d in sorted(data,key=sortkey)]
        assert get(order)==expected,(order,get(order),expected)
        checks+=1
    for order in orders:
        assert get(order,limit=5,offset=0)+get(order,limit=5,offset=5)+get(order,limit=5,offset=10)==get(order),order
        checks+=1
    # Filters are applied BEFORE the sorting/pagination, same as the Android store.
    expected=[i for i in get('HOURLY_HIGH') if con.execute('SELECT payout_cents>=2000 FROM offers WHERE id=?',(i,)).fetchone()[0]]
    assert get('HOURLY_HIGH',where,arg.split(','))==expected;checks+=1
    # Outcome/date/search composition does not defeat or alter sorting.
    criteria="outcome='ACCEPTED' AND seen_at>=? AND pickup LIKE ?"
    args=(1000,'%Coles%')
    assert get('HOURLY_HIGH',criteria,args)==[11,2];checks+=1
    # When no reliable rates exist, still display matching rows in recent-first order.
    assert get('HOURLY_HIGH',"extraction_status IN ('COUNT_ONLY','HOUR_BOUND')")==[13,6];checks+=1
    # Ensure the sort does not inadvertently exclude any rows.
    assert all(len(get(key))==len(data) for key in orders);checks+=1
    print(f'PASS history sorting SQLite ({checks} scenarios)')
