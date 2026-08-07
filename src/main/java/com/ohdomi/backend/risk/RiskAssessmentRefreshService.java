package com.ohdomi.backend.risk;

import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@ConditionalOnProperty(name = "risk-model.enabled", havingValue = "true", matchIfMissing = true)
public class RiskAssessmentRefreshService {
    private static final Logger log = LoggerFactory.getLogger(RiskAssessmentRefreshService.class);

    private final JdbcTemplate jdbc;
    private final RiskModelClient modelClient;
    private final TransactionTemplate transactions;

    public RiskAssessmentRefreshService(
            JdbcTemplate jdbc,
            RiskModelClient modelClient,
            TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.modelClient = modelClient;
        this.transactions = transactions;
    }

    @Scheduled(
            cron = "${risk-model.refresh-cron:0 0 3 * * *}",
            zone = "${risk-model.refresh-zone:Asia/Seoul}")
    public void scheduledRefresh() {
        RefreshSummary summary = refreshAll();
        log.info("Risk assessment refresh completed: total={}, succeeded={}, failed={}",
                summary.total(), summary.succeeded(), summary.failed());
    }

    public RefreshSummary refreshAll() {
        List<RiskModelClient.StoreRiskInput> stores = jdbc.query("""
                SELECT store_id, store_code, name, region, address, opened_on,
                       exclusive_area_sqm, latitude, longitude
                FROM stores
                WHERE operation_status = 'OPEN'
                ORDER BY store_id
                """, (rs, row) -> new RiskModelClient.StoreRiskInput(
                rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getObject(6, LocalDate.class), rs.getBigDecimal(7),
                rs.getBigDecimal(8), rs.getBigDecimal(9)));

        int succeeded = 0;
        for (RiskModelClient.StoreRiskInput store : stores) {
            try {
                RiskModelClient.RiskPrediction prediction = modelClient.predict(store);
                transactions.executeWithoutResult(status -> save(store.storeId(), prediction));
                succeeded++;
            } catch (RuntimeException exception) {
                log.warn("Risk assessment refresh failed for store {} ({}): {}",
                        store.storeId(), store.storeCode(), exception.getMessage());
            }
        }
        return new RefreshSummary(stores.size(), succeeded, stores.size() - succeeded);
    }

    private void save(long storeId, RiskModelClient.RiskPrediction prediction) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO risk_assessments
                      (store_id, model_version, risk_score, risk_level, location_risk_score,
                       classification_detail, main_reason, prediction, recommended_action,
                       assessed_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"risk_assessment_id"});
            statement.setLong(1, storeId);
            statement.setString(2, prediction.modelVersion());
            statement.setBigDecimal(3, prediction.riskScore());
            statement.setInt(4, prediction.riskLevel());
            statement.setBigDecimal(5, prediction.locationRiskScore());
            statement.setString(6, prediction.classificationDetail());
            statement.setString(7, prediction.mainReason());
            statement.setString(8, prediction.prediction());
            statement.setString(9, prediction.recommendedAction());
            statement.setObject(10, LocalDateTime.now());
            return statement;
        }, keys);

        Number assessmentId = keys.getKey();
        if (assessmentId == null) {
            throw new IllegalStateException("Database did not return a risk assessment id");
        }

        List<RiskModelClient.RiskFactorPrediction> factors = prediction.riskFactors() == null
                ? List.of() : prediction.riskFactors();
        for (int index = 0; index < factors.size(); index++) {
            RiskModelClient.RiskFactorPrediction factor = factors.get(index);
            int rank = factor.factorRank() == null ? index + 1 : factor.factorRank();
            jdbc.update("""
                    INSERT INTO risk_factors
                      (risk_assessment_id, model_version, factor_rank, feature_name, category,
                       shap_contribution, evidence, preventive_action, clause_template)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, assessmentId.longValue(),
                    factor.modelVersion() == null ? prediction.modelVersion() : factor.modelVersion(),
                    rank, factor.featureName(), factor.category(), factor.shapContribution(),
                    factor.evidence(), factor.preventiveAction(), factor.clauseTemplate());
        }
    }

    public record RefreshSummary(int total, int succeeded, int failed) {}
}
