"""Real SQLite integration for v0.9.11 delivery filters and filtered-share denominator."""
from pathlib import Path
from datetime import datetime,timezone,timedelta
import sqlite3

r=Path(__file__).resolve().parent.parent
source=(r/'app/src/main/java/com/personal/offerguardian/OfferHistoryStore.java').read_text()
quick=(r/'app/src/main/java/com/personal/offerguardian/OfferHistoryDeliveryFilter.java').read_text()
assert 'delivery_count = ?' in quick
assert 'SELECT COUNT(*) FROM offers WHERE ' in source
assert 'totalInTimeline' in source
assert 'parsed_minutes,delivery_count' in source
conn=sqlite3.connect(':memory:')
conn.execute('''CREATE TABLE offers(seen_at INTEGER NOT NULL,payout_cents INTEGER NOT NULL,
    minutes INTEGER NOT NULL,extraction_status TEXT NOT NULL,delivery_count INTEGER NOT NULL,
    pickup TEXT NOT NULL,outcome TEXT NOT NULL DEFAULT 'UNKNOWN')''')
aest=timezone(timedelta(hours=10))
def at(h,m):return int(datetime(2026,11,23,h,m,tzinfo=aest).timestamp()*1000)
conn.executemany('INSERT INTO offers VALUES(?,?,?,?,?,?,?)',[
    (at(17,10),1250,25,'VERIFIED',1,'Solo A','UNKNOWN'),
    (at(17,45),1134,26,'VERIFIED',2,'Double A','UNKNOWN'),
    (at(18,15),3059,56,'VERIFIED',3,'Triple','UNKNOWN'),
    (at(19,30),2000,30,'VERIFIED',2,'Double B','UNKNOWN'),
    (at(19,55),900,0,'COUNT_ONLY',2,'Unknown rate','UNKNOWN'),
    (at(22,10),3000,35,'VERIFIED',2,'Outside period','UNKNOWN'),
])
minute="(CAST(strftime('%H',seen_at/1000,'unixepoch','+10 hours') AS INTEGER)*60+CAST(strftime('%M',seen_at/1000,'unixepoch','+10 hours') AS INTEGER))"
window=f'{minute} >= ? AND {minute} <= ?'
start,end=1020,1200
base=conn.execute('SELECT COUNT(*) FROM offers WHERE '+window,(start,end)).fetchone()[0]
assert base==5,base
rate_filter=" AND extraction_status IN ('VERIFIED','CORRECTED') AND minutes>0 AND payout_cents*60 >= ?*minutes"
filtered=conn.execute('SELECT COUNT(*) FROM offers WHERE '+window+rate_filter,(start,end,3000)).fetchone()[0]
assert filtered==3,filtered
assert round(filtered/base*100,1)==60.0
for count,expected in [(1,1),(2,3),(3,1)]:
    found=conn.execute('SELECT COUNT(*) FROM offers WHERE '+window+' AND delivery_count=?',(start,end,count)).fetchone()[0]
    assert found==expected,(count,found)
combo=conn.execute('SELECT COUNT(*) FROM offers WHERE '+window+' AND delivery_count=?'+rate_filter,(start,end,2,3000)).fetchone()[0]
assert combo==1,combo
assert round(combo/base*100,1)==20.0
print('PASS SQLite delivery filters + min-hourly combination, timeline percentage, unknown-rate denominator')
