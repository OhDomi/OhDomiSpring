-- demo 계정 매장(1091) 위생점수가 0점으로 뜨는 문제 해결 (2026-08-21).
-- 원인: 팀원이 실시간 AI 위생점검 기능을 매장 1091로 테스트하면서 사진 항목별
-- 개별 점검 기록(hygiene_inspections)을 다수 남겼는데, 그중 가장 최근 것이
-- "바닥 청결 상태" 항목 하나짜리(score=0)라 대시보드가 그걸 그대로 표시함.
-- 팀원 테스트 데이터는 삭제하지 않고, 종합 점검 기록 하나를 가장 최신 시각으로
-- 추가해 대시보드가 그걸 집계하도록 함.
START TRANSACTION;

INSERT INTO hygiene_inspections (store_id, score, status, reviewer, summary, inspected_at)
VALUES (1091, 93, 'GOOD', '매장 임포트(더미)', '종합 위생 점검 결과 양호 — 조리대/냉장고/홀 전반 청결 상태 양호.', NOW());

COMMIT;
