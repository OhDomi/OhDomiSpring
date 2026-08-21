"""24개 매장(1138 제외)의 기존 주문에 customer_order_items(메뉴별 상세)가 없어서
"메뉴별 판매 순위"가 항상 빈 화면인 문제 해결(2026-08-21). 기존 각 주문에 메뉴
1~3개를 배정하고, demo 계정 매장(1091)엔 오늘 날짜 주문도 하나 추가해
시간대별 매출/오늘 매출 카드도 비어있지 않게 한다.
"""
import hashlib
import random
from datetime import datetime

STORES = [
    1004, 1083, 1091, 1097, 1100, 1101, 1105, 1106, 1107, 1116,
    1119, 1120, 1121, 1127, 1131, 1133, 1137, 1144, 1145, 1164,
    1175, 1177, 1185, 1199,
]
DEMO_STORE = 1091

MENU = [(1, 11000), (2, 9000), (3, 9000), (4, 11000)]

ORDER_ID_START = 6404      # 현재 DB 최대(6403) 다음부터
ITEM_ID_START = 6196       # 현재 DB 최대(6195) 다음부터

seed = int(hashlib.sha1(b"order-items-2026-08-21").hexdigest(), 16) % (2**32)
rng = random.Random(seed)

lines = [
    "-- 24개 매장 기존 주문에 메뉴별 상세(customer_order_items) 채움 + demo 계정",
    "-- 매장(1091)에 오늘 날짜 주문 1건 추가 (2026-08-21,",
    "-- scripts/gen_order_items_for_stores.py) — 메뉴별 판매 순위/오늘 매출 공백 해소.",
    "START TRANSACTION;",
    "",
]

item_rows = []
item_id = ITEM_ID_START


def gen_items_for(order_id: int):
    global item_id
    n = rng.choices([1, 2, 3], weights=[0.5, 0.35, 0.15])[0]
    for menu_item_id, price in rng.sample(MENU, k=min(n, len(MENU))):
        qty = rng.choices([1, 2, 3], weights=[0.5, 0.3, 0.2])[0]
        item_rows.append((item_id, order_id, menu_item_id, qty, price))
        item_id += 1


# 1) 기존 24개 매장 주문 전부에 아이템 채움 (13개월 x 24매장 = 312건, order_id는
#    kimgane_24stores_smooth_13month_sales_seed.sql이 AUTO_INCREMENT로 넣은 순서라
#    DB에서 직접 조회해야 정확함 — 이 스크립트는 SQL만 만들고, order_id 목록은
#    실행 시점에 SELECT로 채워 넣는 방식 대신 아래처럼 placeholder INSERT..SELECT로 처리)
lines.append("""
INSERT INTO customer_order_items (customer_order_item_id, customer_order_id, menu_item_id, quantity, unit_price)
SELECT
  (@rn := @rn + 1) + %d,
  o.customer_order_id,
  ELT(1 + (o.customer_order_id %% 4), 1, 2, 3, 4),
  1 + (o.customer_order_id %% 3),
  ELT(1 + (o.customer_order_id %% 4), 11000, 9000, 9000, 11000)
FROM customer_orders o, (SELECT @rn := -1) r
WHERE o.store_id IN (%s)
  AND NOT EXISTS (SELECT 1 FROM customer_order_items oi WHERE oi.customer_order_id = o.customer_order_id);
""" % (ITEM_ID_START, ",".join(str(s) for s in STORES)))

# 2) demo 계정 매장(1091)에 "오늘" 날짜 주문 1건 추가
today = datetime.now().strftime("%Y-%m-%d")
lines.append(f"""
INSERT INTO customer_orders (store_id, channel, ordered_at, total_amount, status) VALUES
  ({DEMO_STORE}, 'IN_STORE', '{today} 12:30:00', 38000, 'COMPLETED');

INSERT INTO customer_order_items (customer_order_id, menu_item_id, quantity, unit_price)
SELECT customer_order_id, 1, 2, 11000 FROM customer_orders
  WHERE store_id={DEMO_STORE} AND ordered_at >= '{today} 00:00:00' ORDER BY customer_order_id DESC LIMIT 1;
INSERT INTO customer_order_items (customer_order_id, menu_item_id, quantity, unit_price)
SELECT customer_order_id, 3, 1, 9000 FROM customer_orders
  WHERE store_id={DEMO_STORE} AND ordered_at >= '{today} 00:00:00' ORDER BY customer_order_id DESC LIMIT 1;
INSERT INTO customer_order_items (customer_order_id, menu_item_id, quantity, unit_price)
SELECT customer_order_id, 4, 1, 11000 FROM customer_orders
  WHERE store_id={DEMO_STORE} AND ordered_at >= '{today} 00:00:00' ORDER BY customer_order_id DESC LIMIT 1;
""")

lines.append("COMMIT;")

with open("kimgane_order_items_and_demo_today_seed.sql", "w", encoding="utf-8") as f:
    f.write("\n".join(lines))
print("생성 완료")
