#!/usr/bin/env python3
"""Use production Java SQL against SQLite with Australia/Brisbane local clock."""
import datetime
import os
import pathlib
import sqlite3
import subprocess
import tempfile
import time
from zoneinfo import ZoneInfo

base=pathlib.Path(__file__).resolve().parents[1]
java=base/'app/src/main/java/com/personal/offerguardian/OfferHistoryTimeWindow.java'
dump=base/'tests/OfferHistoryTimeWindowSqlDump.java'
checks=0
with tempfile.TemporaryDirectory() as folder:
    subprocess.run(['javac','-d',folder,str(java),str(dump)],check=True)
    def sql_for(start,end):
        return subprocess.check_output(['java','-cp',folder,
              'com.personal.offerguardian.OfferHistoryTimeWindowSqlDump',
              str(start),str(end)],text=True).strip()
    previous=os.environ.get('TZ')
    os.environ['TZ']='Australia/Brisbane'
    if hasattr(time,'tzset'):time.tzset()
    try:
        con=sqlite3.connect(':memory:')
        con.execute('create table offers(seen_at integer, payout_cents integer, pickup text)')
        tz=ZoneInfo('Australia/Brisbane')
        times=[('2026-11-22T16:59:00',1000),('2026-11-22T17:00:00',2500),
            ('2026-11-22T18:20:00',3300),('2026-11-22T20:00:00',4900),
            ('2026-11-22T20:01:00',2500),('2026-11-22T23:00:00',1900),
            ('2026-11-23T00:30:00',2100),('2026-11-23T02:00:00',2600),
            ('2026-11-23T02:01:00',3000),('2026-11-23T18:30:00',2800)]
        for stamp,price in times:
            dt=datetime.datetime.fromisoformat(stamp).replace(tzinfo=tz)
            con.execute('insert into offers values (?,?,?)',(int(dt.timestamp()*1000),price,stamp))
        query=sql_for(17*60,20*60)
        rows=con.execute('select pickup from offers where '+query,('1020','1200')).fetchall()
        assert [x[0] for x in rows]==[
            '2026-11-22T17:00:00','2026-11-22T18:20:00',
            '2026-11-22T20:00:00','2026-11-23T18:30:00'],rows
        checks+=1
        # Date restriction AND time AND payment remains consistent for list and stats.
        start_day=int(datetime.datetime(2026,11,23,tzinfo=tz).timestamp()*1000)
        q='select count(*) from offers where seen_at >= ? and ('+query+') and payout_cents >= ?'
        assert con.execute(q,(start_day,1020,1200,2500)).fetchone()[0]==1
        checks+=1
        overnight=sql_for(22*60,2*60)
        rows=con.execute('select pickup from offers where '+overnight,(1320,120)).fetchall()
        assert [x[0] for x in rows]==[
            '2026-11-22T23:00:00','2026-11-23T00:30:00','2026-11-23T02:00:00'],rows
        checks+=1
        print('Offer History time-of-day SQLite: ',checks,'cases passed')
    finally:
        if previous is None:os.environ.pop('TZ',None)
        else:os.environ['TZ']=previous
        if hasattr(time,'tzset'):time.tzset()
