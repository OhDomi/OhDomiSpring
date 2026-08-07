-- 216개 김가네 매장 임포트 되돌리기 (kimgane_216_stores_import.sql의 반대 작업).
-- FK 제약(ON DELETE CASCADE 없음) 때문에 자식 -> 부모 순서로 지워야 함.
-- store_id/inspection_id 1000~1215, owner_user_id=1000 범위만 지운다(기존 데모 데이터 1~7과
-- 겹치지 않게 gen_ohdomi_store_import.py가 일부러 띄워둔 범위 — 이 범위 밖은 절대 안 건드림).

DELETE FROM hygiene_inspections WHERE store_id BETWEEN 1000 AND 1215;
DELETE FROM stores WHERE store_id BETWEEN 1000 AND 1215;
DELETE FROM app_users WHERE user_id = 1000;
