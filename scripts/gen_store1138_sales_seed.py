"""매장 1138(김가네 잠원동점, 가맹점주 qwer 연결 매장)의 시간대별 매출/메뉴별 판매 순위 화면이
비어있는 문제(customer_orders는 월 1건씩만 있고 customer_order_items가 아예 없음) 해결용.
최근 6개월(2026-02-20~2026-08-20) 점심/저녁 피크가 있는 현실적인 주문+아이템 데이터를
결정적(seed 고정)으로 생성해 kimgane_store1138_6month_sales_seed.sql로 저장한다.
"""
import hashlib
import random
from datetime import date, datetime, timedelta

STORE_ID = 1138
START_DATE = date(2026, 2, 20)
END_DATE = date(2026, 8, 20)
OPEN_HOUR, CLOSE_HOUR = 8, 22
ORDER_ID_START = 5000
ORDER_ITEM_ID_START = 5000

MENU = [
    (1, 11000),  # 연어 포케
    (2, 9000),   # 참치 마요 덮밥
    (3, 9000),   # 메밀 소바
    (4, 11000),  # 닭가슴살 샐러드
]
CHANNELS = ["IN_STORE", "DELIVERY", "TAKEOUT"]
CHANNEL_WEIGHTS = [0.55, 0.30, 0.15]

# 매장별로 다른 결과가 나오게 시드 고정(재실행해도 동일 결과)
seed = int(hashlib.sha1(f"store-sales-{STORE_ID}".encode()).hexdigest(), 16) % (2**32)
rng = random.Random(seed)


def pick_hour() -> int:
    r = rng.random()
    if r < 0.45:  # 점심 피크
        return rng.choice([11, 12, 13])
    if r < 0.80:  # 저녁 피크
        return rng.choice([17, 18, 19])
    return rng.randint(OPEN_HOUR, CLOSE_HOUR - 1)


def gen_order(order_id: int, day: date, item_id_start: int):
    hour = pick_hour()
    minute = rng.randint(0, 59)
    second = rng.randint(0, 59)
    ordered_at = datetime(day.year, day.month, day.day, hour, minute, second)
    channel = rng.choices(CHANNELS, weights=CHANNEL_WEIGHTS)[0]

    n_items = rng.choices([1, 2, 3], weights=[0.5, 0.35, 0.15])[0]
    lines = rng.sample(MENU, k=min(n_items, len(MENU)))
    item_rows = []
    total = 0
    item_id = item_id_start
    for menu_item_id, price in lines:
        qty = rng.choices([1, 2], weights=[0.7, 0.3])[0]
        total += price * qty
        item_rows.append((item_id, order_id, menu_item_id, qty, price))
        item_id += 1

    order_row = (order_id, STORE_ID, channel, ordered_at, total, "COMPLETED")
    return order_row, item_rows, item_id


def main():
    order_id = ORDER_ID_START
    item_id = ORDER_ITEM_ID_START
    orders, items = [], []

    day = START_DATE
    while day <= END_DATE:
        is_weekend = day.weekday() >= 5
        n_orders = rng.randint(4, 7) if is_weekend else rng.randint(2, 5)
        for _ in range(n_orders):
            order_row, item_rows, item_id = gen_order(order_id, day, item_id)
            orders.append(order_row)
            items.extend(item_rows)
            order_id += 1
        day += timedelta(days=1)

    lines = [
        f"-- 매장 1138(김가네 잠원동점) 6개월 매출 시드 (2026-08-20 생성, scripts/gen_store1138_sales_seed.py)",
        f"-- 배경: 시간대별 매출 흐름/메뉴별 판매 순위 화면이 비어있던 문제 — 기존엔 월 1건 total만",
        f"-- 있고 customer_order_items가 아예 없어서 발생. {START_DATE}~{END_DATE} 점심/저녁 피크가",
        f"-- 있는 현실적인 주문 {len(orders)}건 + 아이템 {len(items)}건을 생성해 채움.",
        "START TRANSACTION;",
        "",
    ]

    lines.append("INSERT INTO customer_orders (customer_order_id, store_id, channel, ordered_at, total_amount, status) VALUES")
    lines.append(",\n".join(
        f"  ({oid}, {sid}, '{ch}', '{ts}', {tot}, '{st}')"
        for oid, sid, ch, ts, tot, st in orders
    ) + ";")
    lines.append("")

    lines.append("INSERT INTO customer_order_items (customer_order_item_id, customer_order_id, menu_item_id, quantity, unit_price) VALUES")
    lines.append(",\n".join(
        f"  ({iid}, {oid}, {mid}, {qty}, {price})"
        for iid, oid, mid, qty, price in items
    ) + ";")
    lines.append("")
    lines.append("COMMIT;")

    Path_out = "kimgane_store1138_6month_sales_seed.sql"
    with open(Path_out, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    print(f"{Path_out} 생성 완료 — 주문 {len(orders)}건, 아이템 {len(items)}건")


if __name__ == "__main__":
    main()
