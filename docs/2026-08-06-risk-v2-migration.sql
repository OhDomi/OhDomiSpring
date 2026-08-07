-- One-time migration for an existing OhDomi MySQL database.
-- Back up risk_assessments first if its historical rows must be retained.

ALTER TABLE stores
    ADD COLUMN exclusive_area_sqm DECIMAL(10, 2) NULL AFTER contract_ends_on,
    ADD COLUMN latitude DECIMAL(10, 7) NULL AFTER exclusive_area_sqm,
    ADD COLUMN longitude DECIMAL(10, 7) NULL AFTER latitude;

DROP TABLE IF EXISTS risk_factors;
DROP TABLE IF EXISTS risk_assessments;

CREATE TABLE risk_assessments (
    risk_assessment_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id BIGINT NOT NULL,
    model_version VARCHAR(255),
    risk_score DECIMAL(5, 2) NOT NULL CHECK (risk_score BETWEEN 0 AND 100),
    risk_level INTEGER NOT NULL CHECK (risk_level BETWEEN 1 AND 5),
    location_risk_score DECIMAL(5, 2) CHECK (location_risk_score BETWEEN 0 AND 100),
    classification_detail VARCHAR(1000),
    main_reason VARCHAR(1000),
    prediction VARCHAR(1000),
    recommended_action VARCHAR(1000),
    assessed_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_risk_store FOREIGN KEY (store_id) REFERENCES stores(store_id),
    INDEX idx_risk_store_date (store_id, assessed_at)
);

CREATE TABLE risk_factors (
    risk_factor_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    risk_assessment_id BIGINT NOT NULL,
    model_version VARCHAR(255),
    factor_rank INTEGER NOT NULL,
    feature_name VARCHAR(255) NOT NULL,
    category VARCHAR(255),
    shap_contribution DECIMAL(12, 6) NOT NULL,
    evidence VARCHAR(1000),
    preventive_action VARCHAR(1000),
    clause_template VARCHAR(1000),
    CONSTRAINT uq_risk_factor_rank UNIQUE (risk_assessment_id, factor_rank),
    CONSTRAINT fk_risk_factor_assessment FOREIGN KEY (risk_assessment_id)
        REFERENCES risk_assessments(risk_assessment_id) ON DELETE CASCADE,
    INDEX idx_risk_factors_assessment (risk_assessment_id, factor_rank)
);
