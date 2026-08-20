-- kimgane_store1138_6month_sales_seed.sql 롤백용
-- 주의: 이 스크립트가 지운 기존 placeholder 주문(id 42,43,92,93)은 복구되지 않음
DELETE FROM customer_order_items WHERE customer_order_item_id >= 5000;
DELETE FROM customer_orders WHERE customer_order_id >= 5000;
