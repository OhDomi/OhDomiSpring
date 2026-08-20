"""매출 분석(관리자) 페이지의 "월별 통합 매출 추이"/매장별 순위에 2026-06/07 데이터가 없다는
리포트 해결용. kimgane_25_april_may_sales_seed.sql과 동일한 패턴(매장당 월 1건, 5월 대비
성장률 적용)으로 6월/7월을 이어붙인다. store_id 1138(가맹점주 qwer 연결 매장)은 이미 별도
6개월 상세 거래 데이터가 있어 여기서 제외 — 안 하면 이중 집계로 매출이 부풀려짐.
"""
STORES_MAY = [
    (1004, 7.0), (1083, 7.4), (1091, 7.8), (1097, 8.2), (1100, 8.6),
    (1101, 9.0), (1105, 9.4), (1106, 9.8), (1107, 10.2), (1116, 10.6),
    (1119, 11.0), (1120, 11.4), (1121, 11.8), (1127, 12.2), (1131, 12.6),
    (1133, 13.0), (1137, 13.4), (1138, 13.8), (1144, 14.2), (1145, 14.6),
    (1164, 15.0), (1175, 15.4), (1177, 15.8), (1185, 16.2), (1199, 16.6),
]  # kimgane_25_april_may_sales_seed.sql의 5월 값 그대로(단위: 백만원)

EXCLUDE_STORE = 1138  # 이미 별도 6개월 상세 데이터 있음 (kimgane_store1138_6month_sales_seed.sql)
JUNE_GROWTH = 378.09 / 295.0   # 옛 주석 기준 6월 목표 총액 378,090,000 / 5월 총액 295,000,000
JULY_GROWTH = 464.5 / 295.0    # 옛 주석 기준 7월 목표 총액 464,500,000 / 5월 총액 295,000,000

CHANNELS = ["IN_STORE", "DELIVERY", "TAKEOUT"]

order_id = 5732  # 현재 DB 최대 customer_order_id(5731) 다음부터
lines = [
    "-- 매출 분석(관리자) 화면 6/7월 매출 공백 해소 (2026-08-20, scripts/gen_june_july_sales_seed.py)",
    "-- kimgane_25_april_may_sales_seed.sql과 동일 패턴(매장당 월 1건)으로 6월/7월을 이어붙임.",
    "-- store_id 1138은 이미 별도 6개월 상세 데이터가 있어 제외(이중 집계 방지).",
    "START TRANSACTION;",
    "",
    "INSERT IGNORE INTO customer_orders (customer_order_id, store_id, channel, ordered_at, total_amount, status) VALUES",
]
rows = []
for i, (store_id, may_millions) in enumerate(STORES_MAY):
    if store_id == EXCLUDE_STORE:
        continue
    june_amount = round(may_millions * JUNE_GROWTH * 1_000_000 / 100_000) * 100_000
    july_amount = round(may_millions * JULY_GROWTH * 1_000_000 / 100_000) * 100_000
    june_channel = CHANNELS[i % 3]
    july_channel = CHANNELS[(i + 1) % 3]
    rows.append(f"  ({order_id}, {store_id}, '{june_channel}', '2026-06-15 12:00:00', {june_amount}, 'COMPLETED')")
    order_id += 1
    rows.append(f"  ({order_id}, {store_id}, '{july_channel}', '2026-07-15 12:00:00', {july_amount}, 'COMPLETED')")
    order_id += 1

lines.append(",\n".join(rows) + ";")
lines.append("")
lines.append("COMMIT;")

with open("kimgane_25_june_july_sales_seed.sql", "w", encoding="utf-8") as f:
    f.write("\n".join(lines))
print(f"생성 완료 — {len(rows)}건 (매장 {len(STORES_MAY) - 1}곳 x 2개월)")
