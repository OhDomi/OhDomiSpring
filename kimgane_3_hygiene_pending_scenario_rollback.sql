-- kimgane_3_hygiene_pending_scenario.sql 되돌리기 — 삭제했던 3건의
-- hygiene_inspections 행을 원래 값 그대로 복원(kimgane_25_stores_import.sql 발췌).
START TRANSACTION;

INSERT INTO `hygiene_inspections` VALUES
  (1004,1004,60,'URGENT','매장 임포트(더미)','실제 점검 전 초기 더미 데이터입니다 — 실 점검 결과 아님.','2026-08-07 15:38:16'),
  (1083,1083,82,'WARNING','매장 임포트(더미)','실제 점검 전 초기 더미 데이터입니다 — 실 점검 결과 아님.','2026-08-07 15:38:16'),
  (1177,1177,76,'WARNING','매장 임포트(더미)','실제 점검 전 초기 더미 데이터입니다 — 실 점검 결과 아님.','2026-08-07 15:38:16');

COMMIT;
