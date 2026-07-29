package com.ohdomi.backend.risk;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;

import com.ohdomi.backend.global.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/risk-assessments")
public class RiskController {
    private static final String SELECT_RISK = """
            SELECT r.risk_assessment_id, r.store_id, s.name, u.name, s.region,
                   r.risk_score, r.risk_level, r.sales_change_rate, r.hygiene_score,
                   r.delayed_order_count, r.complaint_count, r.main_reason, r.prediction,
                   r.recommended_action, r.assessed_at
            FROM risk_assessments r
            JOIN stores s ON s.store_id = r.store_id
            JOIN app_users u ON u.user_id = s.owner_user_id
            """;

    private final JdbcTemplate jdbc;

    public RiskController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/latest")
    public List<RiskResponse> latest(@RequestParam(required = false) String level) {
        String sql = SELECT_RISK + """
                WHERE r.risk_assessment_id = (
                    SELECT MAX(r2.risk_assessment_id) FROM risk_assessments r2 WHERE r2.store_id = r.store_id)
                """ + (level == null ? "" : " AND r.risk_level = ?") + " ORDER BY r.risk_score DESC";
        Object[] args = level == null ? new Object[0] : new Object[]{level.toUpperCase()};
        return jdbc.query(sql, (rs, row) -> mapRisk(rs), args);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public RiskResponse create(@Valid @RequestBody CreateRiskRequest request) {
        requireStore(request.storeId());
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO risk_assessments
                      (store_id, risk_score, risk_level, sales_change_rate, hygiene_score,
                       delayed_order_count, complaint_count, main_reason, prediction,
                       recommended_action, assessed_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"risk_assessment_id"});
            statement.setLong(1, request.storeId());
            statement.setBigDecimal(2, request.riskScore());
            statement.setString(3, request.riskLevel().toUpperCase());
            statement.setBigDecimal(4, request.salesChangeRate());
            statement.setInt(5, request.hygieneScore());
            statement.setInt(6, request.delayedOrderCount());
            statement.setInt(7, request.complaintCount());
            statement.setString(8, request.mainReason().trim());
            statement.setString(9, request.prediction().trim());
            statement.setString(10, request.recommendedAction().trim());
            statement.setObject(11, request.assessedAt());
            return statement;
        }, keys);
        Number id = keys.getKey();
        if (id == null) throw new IllegalStateException("Database did not return a risk assessment id");
        return jdbc.queryForObject(SELECT_RISK + " WHERE r.risk_assessment_id = ?",
                (rs, row) -> mapRisk(rs), id.longValue());
    }

    private RiskResponse mapRisk(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new RiskResponse(
                rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5),
                rs.getBigDecimal(6), rs.getString(7), rs.getBigDecimal(8), rs.getInt(9),
                rs.getInt(10), rs.getInt(11), rs.getString(12), rs.getString(13), rs.getString(14),
                rs.getTimestamp(15).toLocalDateTime());
    }

    private void requireStore(long storeId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM stores WHERE store_id = ?",
                Integer.class, storeId);
        if (count == null || count == 0) {
            throw new ResourceNotFoundException("Store " + storeId + " was not found");
        }
    }

    public record RiskResponse(long riskAssessmentId, long storeId, String storeName, String ownerName,
                               String region, BigDecimal riskScore, String riskLevel,
                               BigDecimal salesChangeRate, int hygieneScore, int delayedOrderCount,
                               int complaintCount, String mainReason, String prediction,
                               String recommendedAction, LocalDateTime assessedAt) {}

    public record CreateRiskRequest(
            @NotNull @Positive Long storeId,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal riskScore,
            @NotBlank @Size(max = 20) String riskLevel,
            @NotNull BigDecimal salesChangeRate,
            @Min(0) @Max(100) int hygieneScore,
            @Min(0) int delayedOrderCount,
            @Min(0) int complaintCount,
            @NotBlank @Size(max = 1000) String mainReason,
            @NotBlank @Size(max = 1000) String prediction,
            @NotBlank @Size(max = 1000) String recommendedAction,
            @NotNull LocalDateTime assessedAt) {}
}
