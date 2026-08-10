-- kimgane_216_risk_assessments_import.sql 롤백용
-- risk_factors는 risk_assessments FK ON DELETE CASCADE라 자동 삭제됨
DELETE FROM risk_assessments WHERE store_id BETWEEN 1000 AND 1215;
