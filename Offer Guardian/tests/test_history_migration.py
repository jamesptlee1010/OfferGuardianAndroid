"""SQLite v1->v2 migration smoke test without Android (matches Java ALTER SQL)."""
from pathlib import Path
import re
import sqlite3

source = Path(__file__).resolve().parent.parent / 'app/src/main/java/com/personal/offerguardian/OfferHistoryStore.java'
java = source.read_text()
statements = re.findall(r'db\.execSQL\("(ALTER TABLE offers ADD COLUMN [^"\\]+)"\);',java)
assert len(statements) == 8, f'Expected 8 migration statements, got {len(statements)}'
conn = sqlite3.connect(':memory:')
conn.execute('''CREATE TABLE offers(id INTEGER PRIMARY KEY AUTOINCREMENT,seen_at INTEGER NOT NULL,
               payout_cents INTEGER NOT NULL,type TEXT NOT NULL,pickup TEXT NOT NULL DEFAULT '',
               destination TEXT NOT NULL DEFAULT '',minutes INTEGER NOT NULL DEFAULT 0,
               metres INTEGER NOT NULL DEFAULT 0,outcome TEXT NOT NULL DEFAULT 'UNKNOWN',
               action TEXT NOT NULL DEFAULT 'NONE')''')
conn.execute("INSERT INTO offers(seen_at,payout_cents,type,minutes,metres) VALUES(1,2966,'Pack & Deliver',7,9800)")
for statement in statements:
    conn.execute(statement)
row=conn.execute("SELECT payout_cents,minutes,metres,extraction_status,verified_frames,parsed_hours,parsed_minutes FROM offers").fetchone()
assert row == (2966,7,9800,'LEGACY_UNVERIFIED',0,0,0), row
assert 'THEN minutes ELSE 0 END' in java
assert 'THEN metres ELSE 0 END' in java
print('PASS SQLite upgrade preserved original records and marked old OCR unverified')
