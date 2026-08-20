-- kimgane_25_june_july_sales_seed.sql 롤백용
-- 주의: 이 스크립트가 지운 기존 legacy 주문 24건(id 9~57 일부)은 복구되지 않음
DELETE FROM customer_orders WHERE customer_order_id >= 5732;
