#!/usr/bin/env python3
"""Seed structure check only. This is NOT a human-reviewed legal fixture or Oracle test."""
import re
from collections import defaultdict
from datetime import date, timedelta
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DB = ROOT / 'backend/src/main/resources/db/oracle'


def check():
    registered = {}
    for filename in ('V003__legal_rate_items_hr_attendance.sql',
                     'V005__legal_rate_items_hr_attendance_ext.sql'):
        for code, unit in re.findall(r"VALUES\s*\('([A-Z0-9_]+)',\s*'[^']*',\s*'([A-Z_]+)',", (DB / filename).read_text()):
            assert code not in registered, f'duplicate registration: {code}'
            registered[code] = unit
    sql = (DB / 'seed/legal_rate_hr_current.sql').read_text()
    pattern = r"legal_rate_api\.insert_period\('([^']+)', DATE '([^']+)', (NULL|DATE '[^']+'), (-?\d+(?:\.\d+)?), '([^']+)', '([^']+)', '(https://[^']+)', '([^']+)'\);"
    matches = re.findall(pattern, sql)
    assert len(matches) == sql.count('legal_rate_api.insert_period('), 'unparsed insert'
    periods = defaultdict(list)
    order = []
    for code, start, end, value, unit, basis, url, author in matches:
        assert registered.get(code) == unit, f'unit/registration mismatch: {code}'
        first = date.fromisoformat(start)
        last = None if end == 'NULL' else date.fromisoformat(end[6:-1])
        assert last is None or first < last, f'reversed period: {code}'
        periods[code].append((first, last))
        order.append((code, first))
    assert set(periods) == set(registered), 'missing/extra seed code'
    assert order == sorted(order), 'insert order must follow package lock order'
    guard = sql.split('WHERE item_code IN (', 1)[1].split(');', 1)[0]
    guarded = re.findall(r"'([A-Z0-9_]+)'", guard)
    assert len(guarded) == len(set(guarded)), 'duplicate guard entry'
    assert set(guarded) == set(periods), 'existing-data guard does not cover seed'
    checks = 0
    for code, intervals in periods.items():
        intervals.sort()
        for index, (start, end) in enumerate(intervals):
            if index:
                assert intervals[index - 1][1] == start, f'overlap/gap: {code}'
            points = {start - timedelta(days=1), start}
            if end:
                points.update({end - timedelta(days=1), end})
            for point in points:
                count = sum(a <= point and (b is None or point < b) for a, b in intervals)
                assert count <= 1, f'ambiguous boundary: {code} {point}'
                if point == start:
                    assert count == 1
                checks += 1
        if intervals[-1][1] is not None:
            assert not any(a <= intervals[-1][1] and (b is None or intervals[-1][1] < b) for a,b in intervals)
    print(f'{len(registered)} codes, {len(matches)} periods, {checks} boundary checks passed (structure only)')


if __name__ == '__main__':
    check()
