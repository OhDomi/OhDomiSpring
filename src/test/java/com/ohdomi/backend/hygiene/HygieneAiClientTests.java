package com.ohdomi.backend.hygiene;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HygieneAiClientTests {
    @Test
    void reviewUsesMvcMultipartWithoutReactiveStreams() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://hygiene-ai.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HygieneAiClient client = new HygieneAiClient(builder.build());
        server.expect(once(), requestTo("http://hygiene-ai.test/api/v1/review"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    MediaType contentType = request.getHeaders().getContentType();
                    assertThat(contentType).isNotNull();
                    assertThat(contentType.isCompatibleWith(MediaType.MULTIPART_FORM_DATA)).isTrue();
                    String body = ((MockClientHttpRequest) request)
                            .getBodyAsString(StandardCharsets.ISO_8859_1);
                    assertThat(body).contains("name=\"item_ids\"")
                            .contains("item_03")
                            .contains("name=\"photos\"")
                            .contains("filename=\"floor.jpg\"");
                })
                .andRespond(withSuccess("""
                        {
                          "results": [{
                            "item_id": "item_03",
                            "zone": "홀",
                            "shooting_item": "바닥",
                            "optional": false,
                            "grade": "GOOD",
                            "score": 91,
                            "status": "양호",
                            "findings": ["깨끗함"],
                            "improvement": "",
                            "recheck_reason": null,
                            "retake_count": 0
                          }],
                          "retake_needed": [],
                          "human_review_needed": [],
                          "missing_required": [],
                          "scoring_complete": true,
                          "zone_scores": {"홀": 91.0},
                          "store_score": 91.0
                        }
                        """, MediaType.APPLICATION_JSON));

        HygieneAiClient.ReviewResponse response = client.review(
                "item_03", 0, "floor.jpg", "image/jpeg", new byte[]{1, 2, 3});

        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).score()).isEqualTo(91);
        server.verify();
    }
}
