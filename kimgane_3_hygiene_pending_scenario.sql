-- 위생 점검 미완료 매장 시나리오 (2026-08-11) — 김가네 25개 임포트 매장 중 3곳의
-- hygiene_inspections 행을 삭제해, 통합 대시보드 "위생 점검 완료 N/전체"에서
-- 미확인 매장이 0이 아니라 3개로 뜨도록 함. 전체 매장 수(30개)는 그대로 유지 —
-- 30개 중 3곳만 "아직 점검 안 함" 상태가 되는 것.
-- 대상: KG-005(1004, 경기 고양시), KG-084(1083, 부산 강서구), KG-178(1177, 서울 중랑구)
-- — 세 곳 다 hygiene_check_results/hygiene_images/improvement_tasks에 연결된 행이
-- 없음을 사전 확인(kimgane_25_stores_import.sql이 hygiene_inspections만 채웠음).
START TRANSACTION;

DELETE FROM hygiene_inspections WHERE store_id IN (1004, 1083, 1177);

COMMIT;
