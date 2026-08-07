package com.ohdomi.backend.risk;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/risk-assessments")
public class RiskController {
    private static final String SELECT_LATEST_RISKS = """
            SELECT r.risk_assessment_id, r.store_id, s.name, u.name, s.region,
                   r.model_version, r.risk_score, r.risk_level, r.location_risk_score,
                   r.classification_detail, r.main_reason, r.prediction,
                   r.recommended_action, r.assessed_at,
                   f.risk_factor_id, f.model_version, f.factor_rank, f.feature_name,
                   f.category, f.shap_contribution, f.evidence, f.preventive_action,
                   f.clause_template
            FROM risk_assessments r
            JOIN stores s ON s.store_id = r.store_id
            JOIN app_users u ON u.user_id = s.owner_user_id
            LEFT JOIN risk_factors f ON f.risk_assessment_id = r.risk_assessment_id
            WHERE r.risk_assessment_id = (
                SELECT MAX(r2.risk_assessment_id)
                FROM risk_assessments r2
                WHERE r2.store_id = r.store_id
            )
            """;

    private final JdbcTemplate jdbc;

    public RiskController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/latest")
    public List<RiskResponse> latest(
            @RequestParam(required = false) @Min(1) @Max(5) Integer level) {
        String sql = SELECT_LATEST_RISKS
                + (level == null ? "" : " AND r.risk_level = ?")
                + " ORDER BY r.risk_score DESC, r.risk_assessment_id DESC, f.factor_rank";

        return jdbc.query(sql, RiskController::collectRisks,
                level == null ? new Object[0] : new Object[]{level});
    }

    private static List<RiskResponse> collectRisks(java.sql.ResultSet rs)
            throws java.sql.SQLException {
        Map<Long, RiskBuilder> risks = new LinkedHashMap<>();
        while (rs.next()) {
            long assessmentId = rs.getLong("risk_assessment_id");
            RiskBuilder risk = risks.computeIfAbsent(assessmentId, ignored -> readRisk(rs));
            long factorId = rs.getLong("risk_factor_id");
            if (!rs.wasNull()) {
                risk.factors.add(new RiskFactorResponse(
                        factorId, rs.getString(16), rs.getInt(17), rs.getString(18),
                        rs.getString(19), rs.getBigDecimal(20), rs.getString(21),
                        rs.getString(22), rs.getString(23)));
            }
        }
        return risks.values().stream().map(RiskBuilder::build).toList();
    }

    private static RiskBuilder readRisk(java.sql.ResultSet rs) {
        try {
            return new RiskBuilder(
                    rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                    rs.getString(5), rs.getString(6), rs.getBigDecimal(7), rs.getInt(8),
                    rs.getBigDecimal(9), rs.getString(10), rs.getString(11),
                    rs.getString(12), rs.getString(13),
                    rs.getTimestamp(14).toLocalDateTime());
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException("Could not map a risk assessment", exception);
        }
    }

    public record RiskResponse(
            long riskAssessmentId, long storeId, String storeName, String ownerName,
            String region, String modelVersion, BigDecimal riskScore, int riskLevel,
            BigDecimal locationRiskScore, String classificationDetail, String mainReason,
            String prediction, String recommendedAction, LocalDateTime assessedAt,
            List<RiskFactorResponse> riskFactors) {}

    public record RiskFactorResponse(
            long riskFactorId, String modelVersion, int factorRank, String featureName,
            String category, BigDecimal shapContribution, String evidence,
            String preventiveAction, String clauseTemplate) {}

    private static final class RiskBuilder {
        private final long riskAssessmentId;
        private final long storeId;
        private final String storeName;
        private final String ownerName;
        private final String region;
        private final String modelVersion;
        private final BigDecimal riskScore;
        private final int riskLevel;
        private final BigDecimal locationRiskScore;
        private final String classificationDetail;
        private final String mainReason;
        private final String prediction;
        private final String recommendedAction;
        private final LocalDateTime assessedAt;
        private final List<RiskFactorResponse> factors = new ArrayList<>();

        private RiskBuilder(long riskAssessmentId, long storeId, String storeName,
                            String ownerName, String region, String modelVersion,
                            BigDecimal riskScore, int riskLevel, BigDecimal locationRiskScore,
                            String classificationDetail, String mainReason, String prediction,
                            String recommendedAction, LocalDateTime assessedAt) {
            this.riskAssessmentId = riskAssessmentId;
            this.storeId = storeId;
            this.storeName = storeName;
            this.ownerName = ownerName;
            this.region = region;
            this.modelVersion = modelVersion;
            this.riskScore = riskScore;
            this.riskLevel = riskLevel;
            this.locationRiskScore = locationRiskScore;
            this.classificationDetail = classificationDetail;
            this.mainReason = mainReason;
            this.prediction = prediction;
            this.recommendedAction = recommendedAction;
            this.assessedAt = assessedAt;
        }

        private RiskResponse build() {
            return new RiskResponse(riskAssessmentId, storeId, storeName, ownerName, region,
                    modelVersion, riskScore, riskLevel, locationRiskScore,
                    classificationDetail, mainReason, prediction, recommendedAction,
                    assessedAt, List.copyOf(factors));
        }
    }
}
