-- 24개 매장 기존 주문에 메뉴별 상세(customer_order_items) 채움 + demo 계정
-- 매장(1091)에 오늘 날짜 주문 1건 추가 (2026-08-21,
-- scripts/gen_order_items_for_stores.py) — 메뉴별 판매 순위/오늘 매출 공백 해소.
START TRANSACTION;


INSERT INTO customer_order_items (customer_order_item_id, customer_order_id, menu_item_id, quantity, unit_price)
SELECT
  (@rn := @rn + 1) + 6196,
  o.customer_order_id,
  ELT(1 + (o.customer_order_id % 4), 1, 2, 3, 4),
  1 + (o.customer_order_id % 3),
  ELT(1 + (o.customer_order_id % 4), 11000, 9000, 9000, 11000)
FROM customer_orders o, (SELECT @rn := -1) r
WHERE o.store_id IN (1004,1083,1091,1097,1100,1101,1105,1106,1107,1116,1119,1120,1121,1127,1131,1133,1137,1144,1145,1164,1175,1177,1185,1199)
  AND NOT EXISTS (SELECT 1 FROM customer_order_items oi WHERE oi.customer_order_id = o.customer_order_id);


INSERT INTO customer_orders (store_id, channel, ordered_at, total_amount, status) VALUES
  (1091, 'IN_STORE', '2026-08-21 12:30:00', 38000, 'COMPLETED');

INSERT INTO customer_order_items (customer_order_id, menu_item_id, quantity, unit_price)
SELECT customer_order_id, 1, 2, 11000 FROM customer_orders
  WHERE store_id=1091 AND ordered_at >= '2026-08-21 00:00:00' ORDER BY customer_order_id DESC LIMIT 1;
INSERT INTO customer_order_items (customer_order_id, menu_item_id, quantity, unit_price)
SELECT customer_order_id, 3, 1, 9000 FROM customer_orders
  WHERE store_id=1091 AND ordered_at >= '2026-08-21 00:00:00' ORDER BY customer_order_id DESC LIMIT 1;
INSERT INTO customer_order_items (customer_order_id, menu_item_id, quantity, unit_price)
SELECT customer_order_id, 4, 1, 11000 FROM customer_orders
  WHERE store_id=1091 AND ordered_at >= '2026-08-21 00:00:00' ORDER BY customer_order_id DESC LIMIT 1;

COMMIT;