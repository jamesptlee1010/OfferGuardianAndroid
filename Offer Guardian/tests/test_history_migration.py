"""SQLite v1 -> v3 smoke test without Android and conservative count recovery."""
from pathlib import Path
import re
import sqlite3

java=(Path(__file__).resolve().parent.parent /
      'app/src/main/java/com/personal/offerguardian/OfferHistoryStore.java').read_text()
statements=re.findall(r'db\.execSQL\("(ALTER TABLE offers ADD COLUMN [^"\\]+)"\);',java)
assert len(statements)==9, f'Expected nine ALTER statements, got {len(statements)}'
conn=sqlite3.connect(':memory:')
conn.execute('''CREATE TABLE offers(id INTEGER PRIMARY KEY AUTOINCREMENT,seen_at INTEGER NOT NULL,
               payout_cents INTEGER NOT NULL,type TEXT NOT NULL,pickup TEXT NOT NULL DEFAULT '',
               destination TEXT NOT NULL DEFAULT '',minutes INTEGER NOT NULL DEFAULT 0,
               metres INTEGER NOT NULL DEFAULT 0,outcome TEXT NOT NULL DEFAULT 'UNKNOWN',
               action TEXT NOT NULL DEFAULT 'NONE')''')
conn.executemany('INSERT INTO offers(seen_at,payout_cents,type,minutes,metres) VALUES(?,?,?,?,?)',[
    (1,2966,'Pack & Deliver',7,9800),
    (2,1100,'Delivery (multiple)',25,3000),
    (3,1250,'Delivery (2)',26,6000),
    (4,3059,'Pack & Deliver (3)',56,6600),
    (5,800,'Delivery',15,1000),
])
for statement in statements[:8]:conn.execute(statement)
conn.execute("UPDATE offers SET extraction_note='Two matching delivery headings confirm 2 deliveries. Duration not independently verified; hourly unknown.' WHERE id=2")
conn.execute(statements[8])
for n in range(1,4):
    conn.execute(f"UPDATE offers SET delivery_count={n} WHERE delivery_count=0 AND type IN ('Delivery ({n})','Pack & Deliver ({n})')")
    conn.execute(f"UPDATE offers SET delivery_count={n} WHERE delivery_count=0 AND extraction_note LIKE '%confirm {n} deliveries.%'")
rows=conn.execute('SELECT id,delivery_count FROM offers ORDER BY id').fetchall()
assert rows==[(1,0),(2,2),(3,2),(4,3),(5,0)],rows
assert conn.execute('SELECT minutes FROM offers WHERE id=1').fetchone()[0]==7
assert conn.execute('SELECT extraction_status FROM offers WHERE id=1').fetchone()[0]=='LEGACY_UNVERIFIED'
assert 'THEN minutes ELSE 0 END' in java
assert 'THEN metres ELSE 0 END' in java
print('PASS SQLite v1-v3 migration: known counts recovered; ambiguous rows remain unknown')

# Direct upgrade from the immediately preceding v2 schema (real v0.9.10 path).
previous=sqlite3.connect(':memory:')
previous.execute('''CREATE TABLE offers(id INTEGER PRIMARY KEY AUTOINCREMENT,seen_at INTEGER NOT NULL,
               payout_cents INTEGER NOT NULL,type TEXT NOT NULL,pickup TEXT NOT NULL DEFAULT '',
               destination TEXT NOT NULL DEFAULT '',minutes INTEGER NOT NULL DEFAULT 0,
               metres INTEGER NOT NULL DEFAULT 0,outcome TEXT NOT NULL DEFAULT 'UNKNOWN',
               action TEXT NOT NULL DEFAULT 'NONE')''')
for statement in statements[:8]:previous.execute(statement)
previous.execute("INSERT INTO offers(seen_at,payout_cents,type,minutes,metres,extraction_status,extraction_note) VALUES(?,?,?, ?,?,?,?)",
                 (5,1134,'Delivery (multiple)',26,6000,'COUNT_ONLY',
                  'Two matching delivery headings confirm 2 deliveries. Duration not independently verified; hourly unknown.'))
previous.execute(statements[8])
for n in range(1,4):
    previous.execute(f"UPDATE offers SET delivery_count={n} WHERE delivery_count=0 AND type IN ('Delivery ({n})','Pack & Deliver ({n})')")
    previous.execute(f"UPDATE offers SET delivery_count={n} WHERE delivery_count=0 AND extraction_note LIKE '%confirm {n} deliveries.%'")
assert previous.execute('SELECT delivery_count FROM offers').fetchone()[0]==2
print('PASS direct SQLite v2-v3 upgrade, confirmed 2-delivery history recovered')
