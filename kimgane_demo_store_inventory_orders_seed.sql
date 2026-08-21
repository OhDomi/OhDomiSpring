-- demo 계정 매장(1091)에 재고/발주추천 더미 데이터 추가 (2026-08-21,
-- scripts/gen_demo_store_inventory_orders.py) — 원래 데모 매장(store_id=1)의
-- 재고 5종 패턴을 그대로 복제. '발주 필요 품목 0개' 문제 해결.
START TRANSACTION;

INSERT INTO inventory_items (store_id, item_name, category, unit, current_quantity, reorder_level, unit_price) VALUES
  (1091, '연어', '수산', 'kg', 22.0, 38.0, 18000.0),
  (1091, '날치알', '토핑', '개', 45.0, 55.0, 3500.0),
  (1091, '포장 용기', '소모품', '개', 120.0, 190.0, 750.0),
  (1091, '샐러드 채소', '신선식품', 'kg', 8.0, 13.0, 10600.0),
  (1091, '메밀면', '면류', 'kg', 8.5, 6.2, 7800.0);

INSERT INTO order_recommendations (inventory_item_id, recommendation_date, expected_usage, recommended_quantity, risk_level, reason) VALUES
  ((SELECT inventory_item_id FROM inventory_items WHERE store_id=1091 AND item_name='연어'), '2026-08-21', 38.0, 16.0, 'SHORTAGE', '점심 피크 시간대 연어 포케 주문 증가 예상'),
  ((SELECT inventory_item_id FROM inventory_items WHERE store_id=1091 AND item_name='날치알'), '2026-08-21', 55.0, 10.0, 'WARNING', '인기 메뉴 주문 증가로 추가 확보 권장'),
  ((SELECT inventory_item_id FROM inventory_items WHERE store_id=1091 AND item_name='포장 용기'), '2026-08-21', 190.0, 70.0, 'SHORTAGE', '배달앱 주문 비중 상승'),
  ((SELECT inventory_item_id FROM inventory_items WHERE store_id=1091 AND item_name='샐러드 채소'), '2026-08-21', 13.0, 5.0, 'WARNING', '저녁 시간대 샐러드 메뉴 판매 증가 예상'),
  ((SELECT inventory_item_id FROM inventory_items WHERE store_id=1091 AND item_name='메밀면'), '2026-08-21', 6.2, 0.0, 'SAFE', '현재 재고로 예상 수요 대응 가능');

COMMIT;