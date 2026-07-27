package com.ohdomi.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OhDomiApiIntegrationTests {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void loginChecksPasswordAndSelectedRole() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"demo","password":"1234","role":"OWNER"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginId").value("demo"))
                .andExpect(jsonPath("$.name").value("김도윤"))
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.storeId").value(1));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"demo","password":"wrong","role":"OWNER"}
                                """))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"demo","password":"1234","role":"ADMIN"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registrationCreatesOwnerWithHashedPassword() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "new.owner",
                                  "password": "safePassword123!",
                                  "name": "신규 점주",
                                  "phone": "010-1234-9876"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.loginId").value("new.owner"))
                .andExpect(jsonPath("$.role").value("OWNER"));

        String hash = jdbc.queryForObject(
                "SELECT password_hash FROM app_users WHERE login_id = 'new.owner'", String.class);
        assertThat(hash).startsWith("pbkdf2_sha256$").doesNotContain("safePassword123!");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "new.owner",
                                  "password": "anotherPassword123!",
                                  "name": "중복 사용자",
                                  "phone": "010-0000-0000"
                                }
                                """))
                .andExpect(status().isConflict());
    }

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
