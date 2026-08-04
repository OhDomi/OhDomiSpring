package com.ohdomi.backend.hygiene;

import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class HygieneAiClient {
    private final RestClient restClient;

    @Autowired
    public HygieneAiClient(@Value("${hygiene-ai.base-url}") String baseUrl) {
        this(createRestClient(baseUrl));
    }

    HygieneAiClient(RestClient restClient) {
        this.restClient = restClient;
    }

    private static RestClient createRestClient(String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofMinutes(3));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public List<ChecklistItem> checklist() {
        try {
            List<ChecklistItem> items = restClient.get()
                    .uri("/api/v1/checklist")
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return items == null ? List.of() : items;
        } catch (RestClientResponseException exception) {
            throw rejected(exception);
        } catch (RestClientException exception) {
            throw unavailable(exception);
        }
    }

    public ReviewResponse review(String itemId, int retakeCount, String fileName,
                                 String contentType, byte[] image) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("item_ids", itemId);
        body.add("retake_counts", Integer.toString(retakeCount));
        body.add("photo_item_ids", itemId);
        HttpHeaders imageHeaders = new HttpHeaders();
        imageHeaders.setContentType(MediaType.parseMediaType(contentType));
        imageHeaders.setContentDispositionFormData("photos", fileName);
        body.add("photos", new HttpEntity<>(new NamedByteArrayResource(image, fileName), imageHeaders));

        try {
            ReviewResponse response = restClient.post()
                    .uri("/api/v1/review")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(ReviewResponse.class);
            if (response == null || response.results() == null || response.results().isEmpty()) {
                throw new HygieneAiException("Hygiene AI returned an empty analysis", null);
            }
            return response;
        } catch (HygieneAiException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw rejected(exception);
        } catch (RestClientException exception) {
            throw unavailable(exception);
        }
    }

    private static HygieneAiException rejected(RestClientResponseException exception) {
        String response = exception.getResponseBodyAsString();
        String detail = response == null || response.isBlank() ? exception.getStatusText() : response;
        return new HygieneAiException(
                "Hygiene AI rejected the request (" + exception.getStatusCode().value() + "): " + detail,
                exception);
    }

    private static HygieneAiException unavailable(RestClientException exception) {
        return new HygieneAiException("Hygiene AI service is unavailable or rejected the image", exception);
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {
        private final String fileName;

        private NamedByteArrayResource(byte[] bytes, String fileName) {
            super(bytes);
            this.fileName = fileName;
        }

        @Override
        public String getFilename() {
            return fileName;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChecklistItem(
            @JsonAlias("item_id") String itemId,
            String zone,
            @JsonAlias("shooting_item") String shootingItem,
            @JsonAlias("ai_check_point") String aiCheckPoint,
            @JsonAlias("check_type") String checkType,
            @JsonAlias("max_score") int maxScore,
            boolean optional) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReviewResponse(
            List<AiResult> results,
            @JsonAlias("retake_needed") List<RetakeNeeded> retakeNeeded,
            @JsonAlias("human_review_needed") List<String> humanReviewNeeded,
            @JsonAlias("missing_required") List<String> missingRequired,
            @JsonAlias("scoring_complete") boolean scoringComplete,
            @JsonAlias("zone_scores") java.util.Map<String, Double> zoneScores,
            @JsonAlias("store_score") Double storeScore) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiResult(
            @JsonAlias("item_id") String itemId,
            String zone,
            @JsonAlias("shooting_item") String shootingItem,
            boolean optional,
            String grade,
            Integer score,
            String status,
            List<String> findings,
            String improvement,
            @JsonAlias("recheck_reason") String recheckReason,
            @JsonAlias("retake_count") int retakeCount) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RetakeNeeded(@JsonAlias("item_id") String itemId, String reason) {}
}
