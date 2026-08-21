"""매출 분석 "월별 통합 매출 추이"가 억 단위로 급증락하는 문제 해결용(2026-08-21).
기존엔 여러 시드 파일(aug2025_mar2026/april_may/june_july)이 제각각 다른 성장률로
만들어져 월별 총액이 1.8억→2.8억→3.6억→4.5억→8.7억처럼 널뛰었음 — 매장 1138(가맹점주
qwer 연결 매장, 이미 실거래 패턴이라 손 안 댐)을 제외한 24개 매장 전체를 최근 13개월치
하나의 완만한 데이터로 다시 만든다(월별 총액 변동폭을 억 단위가 아니라 천만원 단위로).
"""
import hashlib
import random
from datetime import date

STORES = [
    1004, 1083, 1091, 1097, 1100, 1101, 1105, 1106, 1107, 1116,
    1119, 1120, 1121, 1127, 1131, 1133, 1137, 1144, 1145, 1164,
    1175, 1177, 1185, 1199,
]  # 1138 제외(이미 실거래 6개월 데이터 있음)

CHANNELS = ["IN_STORE", "DELIVERY", "TAKEOUT"]

# 최근 13개월 (Spring UiDataController.monthlySalesTrend()의 조회 범위와 동일)
MONTHS = [
    (2025, 8), (2025, 9), (2025, 10), (2025, 11), (2025, 12),
    (2026, 1), (2026, 2), (2026, 3), (2026, 4), (2026, 5),
    (2026, 6), (2026, 7), (2026, 8),
]

seed = int(hashlib.sha1(b"smooth-monthly-sales-2026-08-21").hexdigest(), 16) % (2**32)
rng = random.Random(seed)

lines = [
    "-- 24개 매장(1138 제외) 최근 13개월 매출을 완만한 변동(월별 총액 증감폭 천만원대)으로",
    "-- 재생성 (2026-08-21, scripts/gen_smooth_monthly_sales.py) — 기존엔 억 단위로 급증하는",
    "-- 문제가 있어 전체를 하나의 일관된 데이터로 교체.",
    "START TRANSACTION;",
    "",
    "DELETE FROM customer_orders WHERE store_id IN ("
    + ",".join(str(s) for s in STORES) + ");",
    "",
    "INSERT INTO customer_orders (store_id, channel, ordered_at, total_amount, status) VALUES",
]

rows = []
for store_id in STORES:
    base = rng.uniform(6_500_000, 9_500_000)  # 매장별 기본 월매출(고정, 매장 규모 차이)
    for year, month in MONTHS:
        noise = rng.uniform(-1_500_000, 1_500_000)  # 월별 변동(천만원 미만 단위)
        amount = round((base + noise) / 100_000) * 100_000
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
print(f"생성 완료 — 매장 {len(STORES)}곳 x {len(MONTHS)}개월 = {len(rows)}건")
