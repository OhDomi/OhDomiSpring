"""demo 계정 매장(1091)의 "이번 주 매출" 위젯이 하루 38,000원(주문 1건)짜리로 보여서
너무 적다는 리포트(2026-08-21) — 최근 7일을 매장당 하루 다건(20~35건) 실거래처럼
재생성한다. 월 매출목표(₩34,872,787) 대비 하루 평균 매출이 자연스러운 수준(80~110%
달성률)이 되도록 설계. 백원 단위 금액을 섞어 반올림된 느낌을 없앰.
"""
import hashlib
import random
from datetime import date, timedelta

STORE_ID = 1091
MONTHLY_TARGET = 34_872_787
DAILY_TARGET = MONTHLY_TARGET / 30  # ~1,162,426원

MENU = [(1, 11000), (2, 9000), (3, 9000), (4, 11000)]
CHANNELS = ["IN_STORE", "DELIVERY", "TAKEOUT"]

seed = int(hashlib.sha1(f"demo-store-{STORE_ID}-realistic-week".encode()).hexdigest(), 16) % (2**32)
rng = random.Random(seed)

today = date.today()
days = [today - timedelta(days=i) for i in range(6, -1, -1)]

lines = [
    "-- demo 계정 매장(1091)의 최근 7일치를 하루 다건 실거래처럼 재생성 (2026-08-21,",
    "-- scripts/gen_demo_store_realistic_week.py) — 기존 '오늘 매출 38,000원(1건)'이 너무",
    "-- 적어보인다는 리포트 해결. 백원 단위 섞어 반올림 느낌 제거.",
    "START TRANSACTION;",
    "",
    f"DELETE oi FROM customer_order_items oi JOIN customer_orders o ON o.customer_order_id=oi.customer_order_id"
    f" WHERE o.store_id={STORE_ID} AND o.ordered_at >= '{days[0].isoformat()} 00:00:00';",
    f"DELETE FROM customer_orders WHERE store_id={STORE_ID}"
    f" AND ordered_at >= '{days[0].isoformat()} 00:00:00';",
    "",
    "INSERT INTO customer_orders (store_id, channel, ordered_at, total_amount, status) VALUES",
]

order_rows = []
for day in days:
    day_target = DAILY_TARGET * rng.uniform(0.75, 1.15)
    n_orders = rng.randint(22, 34)
    remaining = day_target
    for i in range(n_orders):
        is_last = i == n_orders - 1
        if is_last:
            amount = max(8000, remaining)
        else:
            avg_remaining = remaining / (n_orders - i)
            amount = max(8000, rng.uniform(avg_remaining * 0.4, avg_remaining * 1.6))
        amount = round(amount / 100) * 100  # 백원 단위 유지(반올림 느낌 제거)
        remaining -= amount
        hour = rng.choices(
            [11, 12, 13, 17, 18, 19, *range(8, 22)],
            weights=[3, 4, 3, 3, 4, 3, *([1] * 14)],
        )[0]
        minute = rng.randint(0, 59)
        channel = rng.choices(CHANNELS, weights=[0.5, 0.32, 0.18])[0]
        order_rows.append((day, hour, minute, channel, int(amount)))

lines.append(",\n".join(
    f"  ({STORE_ID}, '{ch}', '{d.isoformat()} {h:02d}:{mi:02d}:00', {amt}, 'COMPLETED')"
    for d, h, mi, ch, amt in order_rows
) + ";")
lines.append("")

# 아이템 채우기: 방금 넣은 주문들(오늘 이후 삽입분)에 메뉴 1~2개씩 배정
lines.append("""
INSERT INTO customer_order_items (customer_order_id, menu_item_id, quantity, unit_price)
SELECT o.customer_order_id,
       ELT(1 + (o.customer_order_id %% 4), 1, 2, 3, 4),
       1 + (o.customer_order_id %% 2),
       ELT(1 + (o.customer_order_id %% 4), 11000, 9000, 9000, 11000)
FROM customer_orders o
WHERE o.store_id=%d AND o.ordered_at >= '%s 00:00:00'
  AND NOT EXISTS (SELECT 1 FROM customer_order_items oi WHERE oi.customer_order_id = o.customer_order_id);
""" % (STORE_ID, days[0].isoformat()))

lines.append("COMMIT;")

with open("kimgane_demo_store_realistic_week_seed.sql", "w", encoding="utf-8") as f:
    f.write("\n".join(lines))
print(f"생성 완료 — {len(order_rows)}건, 일평균 목표 {DAILY_TARGET:,.0f}원")
