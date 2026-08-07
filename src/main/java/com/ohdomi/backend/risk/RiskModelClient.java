package com.ohdomi.backend.risk;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RiskModelClient {
    private final RestClient client;
    private final String predictPath;

    public RiskModelClient(
            @Value("${risk-model.base-url:http://127.0.0.1:8001}") String baseUrl,
            @Value("${risk-model.predict-path:/risk/predict}") String predictPath) {
        this.client = RestClient.builder().baseUrl(baseUrl).build();
        this.predictPath = predictPath;
    }

    public RiskPrediction predict(StoreRiskInput store) {
        RiskPrediction prediction = client.post()
                .uri(predictPath)
                .contentType(MediaType.APPLICATION_JSON)
                .body(store)
                .retrieve()
                .body(RiskPrediction.class);
        if (prediction == null) {
            throw new IllegalStateException("Risk model returned an empty response");
        }
        prediction.validate();
        return prediction;
    }

    public record StoreRiskInput(
            @JsonProperty("store_id") long storeId,
            @JsonProperty("store_code") String storeCode,
            @JsonProperty("store_name") String storeName,
            String region,
            String address,
            @JsonProperty("opened_on") LocalDate openedOn,
            @JsonProperty("exclusive_area_sqm") BigDecimal exclusiveAreaSqm,
            BigDecimal latitude,
            BigDecimal longitude) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RiskPrediction(
            @JsonProperty("model_version") String modelVersion,
            @JsonProperty("risk_score") BigDecimal riskScore,
            @JsonProperty("risk_level") Integer riskLevel,
            @JsonProperty("location_risk_score") BigDecimal locationRiskScore,
            @JsonProperty("classification_detail") String classificationDetail,
            @JsonProperty("main_reason") String mainReason,
            String prediction,
            @JsonProperty("recommended_action") String recommendedAction,
            @JsonProperty("risk_factors") List<RiskFactorPrediction> riskFactors) {

        private void validate() {
            if (modelVersion == null || modelVersion.isBlank()) {
                throw new IllegalStateException("Risk model response is missing model_version");
            }
            if (riskScore == null || riskScore.compareTo(BigDecimal.ZERO) < 0
                    || riskScore.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new IllegalStateException("Risk model response has an invalid risk_score");
            }
            if (riskLevel == null || riskLevel < 1 || riskLevel > 5) {
                throw new IllegalStateException("Risk model response has an invalid risk_level");
            }
            if (locationRiskScore != null
                    && (locationRiskScore.compareTo(BigDecimal.ZERO) < 0
                    || locationRiskScore.compareTo(BigDecimal.valueOf(100)) > 0)) {
                throw new IllegalStateException("Risk model response has an invalid location_risk_score");
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RiskFactorPrediction(
            @JsonProperty("model_version") String modelVersion,
            @JsonProperty("factor_rank") Integer factorRank,
            @JsonProperty("feature_name") String featureName,
            String category,
            @JsonProperty("shap_contribution") BigDecimal shapContribution,
            String evidence,
            @JsonProperty("preventive_action") String preventiveAction,
            @JsonProperty("clause_template") String clauseTemplate) {}
}
