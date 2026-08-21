"""매출 분석 "월별 통합 매출 추이" 그래프 형태 조정(2026-08-21) — 천만~3천만원 단위로
변동하면서 전체적으로 우상향, 6월에만 한 번 꺾이는 곡선. 매장 1138(가맹점주 qwer 연결,
실거래 데이터라 제외)을 뺀 24개 매장 전체를 월별 목표 총액에 맞춰 비율대로 분배해 생성.
"""
import hashlib
import random

STORES = [
    1004, 1083, 1091, 1097, 1100, 1101, 1105, 1106, 1107, 1116,
    1119, 1120, 1121, 1127, 1131, 1133, 1137, 1144, 1145, 1164,
    1175, 1177, 1185, 1199,
]  # 1138 제외(이미 실거래 6개월 데이터 있음)

CHANNELS = ["IN_STORE", "DELIVERY", "TAKEOUT"]

# (연, 월, 24개 매장 목표 총액) — 우상향 기조에 6월만 한 번 하락, 월별 증감폭 1천만~3천만원
MONTHLY_TARGETS = [
    (2025, 8, 180_000_000),
    (2025, 9, 198_000_000),
    (2025, 10, 215_000_000),
    (2025, 11, 235_000_000),
    (2025, 12, 250_000_000),
    (2026, 1, 270_000_000),
    (2026, 2, 285_000_000),
    (2026, 3, 305_000_000),
    (2026, 4, 320_000_000),
    (2026, 5, 340_000_000),
    (2026, 6, 315_000_000),  # 유일한 하락 구간
    (2026, 7, 335_000_000),
    (2026, 8, 355_000_000),
]

seed = int(hashlib.sha1(b"smooth-monthly-sales-2026-08-21-v2").hexdigest(), 16) % (2**32)
rng = random.Random(seed)

# 매장별 고정 비중(매장 규모 차이) — 랜덤이지만 시드 고정이라 매번 동일
weights = {store_id: rng.uniform(0.7, 1.4) for store_id in STORES}
weight_sum = sum(weights.values())

lines = [
    "-- 24개 매장(1138 제외) 매출을 월별 목표 총액에 맞춰 재생성 (2026-08-21,",
    "-- scripts/gen_smooth_monthly_sales.py) — 천만~3천만원 단위로 변동하며 전체적으로",
    "-- 우상향, 6월만 한 번 꺾이는 곡선.",
    "START TRANSACTION;",
    "",
    "DELETE FROM customer_orders WHERE store_id IN ("
    + ",".join(str(s) for s in STORES) + ");",
    "",
    "INSERT INTO customer_orders (store_id, channel, ordered_at, total_amount, status) VALUES",
]

rows = []
for year, month, target_total in MONTHLY_TARGETS:
    for store_id in STORES:
        share = weights[store_id] / weight_sum
        amount = round(target_total * share / 100_000) * 100_000
        day = rng.randint(10, 20)
        hour = rng.randint(11, 20)
        channel = rng.choice(CHANNELS)
        rows.append(
            f"  ({store_id}, '{channel}', '{year:04d}-{month:02d}-{day:02d} {hour:02d}:00:00', {int(amount)}, 'COMPLETED')"
        )

lines.append(",\n".join(rows) + ";")
lines.append("")
lines.append("COMMIT;")

with open("kimgane_24stores_smooth_13month_sales_seed.sql", "w", encoding="utf-8") as f:
    f.write("\n".join(lines))
print(f"생성 완료 — 매장 {len(STORES)}곳 x {len(MONTHLY_TARGETS)}개월 = {len(rows)}건")
