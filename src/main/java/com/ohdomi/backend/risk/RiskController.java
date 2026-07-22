package com.ohdomi.backend.risk;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/risk-assessments")
public class RiskController {
    private final JdbcTemplate jdbc;

    public RiskController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/latest")
    public List<RiskResponse> latest(@RequestParam(required = false) String level) {
        String sql = """
                SELECT r.risk_assessment_id, r.store_id, s.name, u.name, s.region,
                       r.risk_score, r.risk_level, r.sales_change_rate, r.hygiene_score,
                       r.delayed_order_count, r.complaint_count, r.main_reason, r.prediction,
                       r.recommended_action, r.assessed_at
                FROM risk_assessments r
                JOIN stores s ON s.store_id = r.store_id
                JOIN app_users u ON u.user_id = s.owner_user_id
                WHERE r.risk_assessment_id = (
                    SELECT MAX(r2.risk_assessment_id) FROM risk_assessments r2 WHERE r2.store_id = r.store_id)
                """ + (level == null ? "" : " AND r.risk_level = ?") + " ORDER BY r.risk_score DESC";
        Object[] args = level == null ? new Object[0] : new Object[]{level.toUpperCase()};
        return jdbc.query(sql, (rs, row) -> new RiskResponse(
                rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5),
                rs.getBigDecimal(6), rs.getString(7), rs.getBigDecimal(8), rs.getInt(9),
                rs.getInt(10), rs.getInt(11), rs.getString(12), rs.getString(13), rs.getString(14),
                rs.getTimestamp(15).toLocalDateTime()), args);
    }

    public record RiskResponse(long riskAssessmentId, long storeId, String storeName, String ownerName,
                               String region, BigDecimal riskScore, String riskLevel,
                               BigDecimal salesChangeRate, int hygieneScore, int delayedOrderCount,
                               int complaintCount, String mainReason, String prediction,
                               String recommendedAction, LocalDateTime assessedAt) {}
}
