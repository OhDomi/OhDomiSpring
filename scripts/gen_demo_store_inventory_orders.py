"""demo 계정 매장(1091) 대시보드의 "발주 필요 품목 0개"가 실제로는 재고/발주추천 데이터
자체가 store_id=1091에 아예 없어서 그런 것(2026-08-21) — 원래 데모 매장(store_id=1)에
이미 있던 재고 5종+발주추천 패턴을 그대로 매장 1091용으로 복제한다. recommendation_date는
오늘 날짜로 넣어야 orders() 쿼리가 "최신"으로 인식한다.
"""
from datetime import date

STORE_ID = 1091
today = date.today().isoformat()

items = [
    ("연어", "수산", "kg", 22.000, 38.000, 18000.00),
    ("날치알", "토핑", "개", 45.000, 55.000, 3500.00),
    ("포장 용기", "소모품", "개", 120.000, 190.000, 750.00),
    ("샐러드 채소", "신선식품", "kg", 8.000, 13.000, 10600.00),
    ("메밀면", "면류", "kg", 8.500, 6.200, 7800.00),
]
recos = [
    (38.000, 16.000, "SHORTAGE", "점심 피크 시간대 연어 포케 주문 증가 예상"),
    (55.000, 10.000, "WARNING", "인기 메뉴 주문 증가로 추가 확보 권장"),
    (190.000, 70.000, "SHORTAGE", "배달앱 주문 비중 상승"),
    (13.000, 5.000, "WARNING", "저녁 시간대 샐러드 메뉴 판매 증가 예상"),
    (6.200, 0.000, "SAFE", "현재 재고로 예상 수요 대응 가능"),
]

lines = [
    "-- demo 계정 매장(1091)에 재고/발주추천 더미 데이터 추가 (2026-08-21,",
    "-- scripts/gen_demo_store_inventory_orders.py) — 원래 데모 매장(store_id=1)의",
    "-- 재고 5종 패턴을 그대로 복제. '발주 필요 품목 0개' 문제 해결.",
    "START TRANSACTION;",
    "",
    "INSERT INTO inventory_items (store_id, item_name, category, unit, current_quantity, reorder_level, unit_price) VALUES",
]
lines.append(",\n".join(
    f"  ({STORE_ID}, '{name}', '{cat}', '{unit}', {qty}, {reorder}, {price})"
    for name, cat, unit, qty, reorder, price in items
) + ";")
lines.append("")
lines.append("INSERT INTO order_recommendations (inventory_item_id, recommendation_date, expected_usage, recommended_quantity, risk_level, reason) VALUES")
lines.append(",\n".join(
    f"  ((SELECT inventory_item_id FROM inventory_items WHERE store_id={STORE_ID} AND item_name='{items[i][0]}'),"
    f" '{today}', {usage}, {qty}, '{risk}', '{reason}')"
    for i, (usage, qty, risk, reason) in enumerate(recos)
) + ";")
lines.append("")
lines.append("COMMIT;")

with open("kimgane_demo_store_inventory_orders_seed.sql", "w", encoding="utf-8") as f:
    f.write("\n".join(lines))
print("생성 완료")
