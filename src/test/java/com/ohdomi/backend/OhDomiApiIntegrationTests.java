package com.ohdomi.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OhDomiApiIntegrationTests {
    @Autowired
    MockMvc mockMvc;

    @Test
    void storeAndOperationalDataAreAvailable() throws Exception {
        mockMvc.perform(get("/api/stores/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storeName").value("강남역점"))
                .andExpect(jsonPath("$.ownerName").value("김도윤"));

        mockMvc.perform(get("/api/stores/1/order-recommendations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].itemName").value("연어"))
                .andExpect(jsonPath("$[0].recommendedQuantity").value(16));

        mockMvc.perform(get("/api/hygiene-inspections/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inspection.score").value(92))
                .andExpect(jsonPath("$.checkResults.length()").value(4));
    }

    @Test
    void boardSupportsRestCreationAndListing() throws Exception {
        mockMvc.perform(post("/api/board/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "authorUserId": 2,
                                  "storeId": 1,
                                  "boardType": "INQUIRY",
                                  "category": "시스템",
                                  "title": "테스트 문의",
                                  "content": "REST API 등록 테스트입니다.",
                                  "isPinned": false,
                                  "isUrgent": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.boardType").value("INQUIRY"))
                .andExpect(jsonPath("$.authorName").value("김도윤"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(get("/api/board/posts").param("boardType", "INQUIRY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("테스트 문의"));
    }

    @Test
    void missingStoreReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/stores/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Store 99999 was not found"));
    }
}
