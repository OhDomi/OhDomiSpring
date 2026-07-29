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

    @Test
    void storeOperationalInformationCanBeUploaded() throws Exception {
        mockMvc.perform(post("/api/stores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ownerUserId": 2,
                                  "storeCode": "ST-API-TEST",
                                  "name": "API Test Store",
                                  "region": "Seoul",
                                  "address": "1 API Street",
                                  "phone": "010-1111-2222",
                                  "openTime": "09:00:00",
                                  "closeTime": "22:00:00",
                                  "operationStatus": "OPEN",
                                  "openedOn": "2026-07-29",
                                  "contractEndsOn": "2028-07-28",
                                  "monthlySalesTarget": 50000000
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.storeCode").value("ST-API-TEST"));

        Long storeId = jdbc.queryForObject(
                "SELECT store_id FROM stores WHERE store_code='ST-API-TEST'", Long.class);

        mockMvc.perform(post("/api/stores/{storeId}/staff", storeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "API Staff",
                                  "role": "CASHIER",
                                  "workDate": "2026-07-29",
                                  "startsAt": "09:00:00",
                                  "endsAt": "17:00:00",
                                  "status": "SCHEDULED"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("API Staff"));

        mockMvc.perform(post("/api/stores/{storeId}/facilities", storeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"API Refrigerator","active":true}
                                """))
                .andExpect(status().isCreated());
        Long facilityId = jdbc.queryForObject(
                "SELECT facility_id FROM facilities WHERE store_id=? AND name='API Refrigerator'",
                Long.class, storeId);

        mockMvc.perform(post("/api/stores/{storeId}/facilities/{facilityId}/checks", storeId, facilityId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status":"NORMAL",
                                  "memo":"Temperature is normal",
                                  "checkedAt":"2026-07-29T10:00:00"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NORMAL"));

        mockMvc.perform(post("/api/stores/{storeId}/inventory", storeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "itemName":"API Salmon",
                                  "category":"SEAFOOD",
                                  "unit":"kg",
                                  "currentQuantity":10,
                                  "reorderLevel":20,
                                  "unitPrice":18000
                                }
                                """))
                .andExpect(status().isCreated());
        Long inventoryItemId = jdbc.queryForObject(
                "SELECT inventory_item_id FROM inventory_items WHERE store_id=? AND item_name='API Salmon'",
                Long.class, storeId);

        mockMvc.perform(post("/api/stores/{storeId}/order-recommendations", storeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "inventoryItemId": %d,
                                  "recommendationDate":"2026-07-29",
                                  "expectedUsage":18,
                                  "recommendedQuantity":8,
                                  "riskLevel":"WARNING",
                                  "reason":"Stock is below expected usage"
                                }
                                """.formatted(inventoryItemId)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/stores/{storeId}/customer-orders", storeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "channel":"IN_STORE",
                                  "orderedAt":"2026-07-29T12:00:00",
                                  "status":"COMPLETED",
                                  "items":[{"menuItemId":1,"quantity":2,"unitPrice":11000}]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAmount").value(22000));

        mockMvc.perform(post("/api/stores/{storeId}/purchase-orders", storeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderNumber":"PO-API-TEST",
                                  "status":"DRAFT",
                                  "orderedAt":null,
                                  "expectedAt":"2026-07-31T09:00:00",
                                  "items":[{
                                    "inventoryItemId":%d,
                                    "quantity":5,
                                    "unitPrice":18000
                                  }]
                                }
                                """.formatted(inventoryItemId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAmount").value(90000));
    }

    @Test
    void hygieneInspectionAndRiskInformationCanBeUploaded() throws Exception {
        mockMvc.perform(post("/api/hygiene-inspections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "storeId":1,
                                  "score":85,
                                  "status":"WARNING",
                                  "reviewer":"API Inspector",
                                  "summary":"Entrance needs cleaning",
                                  "inspectedAt":"2026-07-29T11:30:00",
                                  "checkResults":[{
                                    "itemName":"Entrance cleanliness",
                                    "score":70,
                                    "status":"WARNING",
                                    "memo":"Clean before opening"
                                  }],
                                  "images":[{
                                    "imageUrl":"/uploads/hygiene/api-test.jpg",
                                    "category":"ENTRANCE",
                                    "analysisResult":"Cleaning required"
                                  }],
                                  "improvementTasks":[{
                                    "title":"Clean entrance",
                                    "description":"Clean and upload a new photo",
                                    "priority":"WARNING",
                                    "status":"OPEN",
                                    "dueAt":"2026-07-30T09:00:00",
                                    "completedAt":null
                                  }]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.inspection.score").value(85))
                .andExpect(jsonPath("$.checkResults.length()").value(1))
                .andExpect(jsonPath("$.images.length()").value(1))
                .andExpect(jsonPath("$.improvementTasks.length()").value(1));

        mockMvc.perform(post("/api/risk-assessments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "storeId":1,
                                  "riskScore":65.5,
                                  "riskLevel":"WARNING",
                                  "salesChangeRate":-3.2,
                                  "hygieneScore":85,
                                  "delayedOrderCount":1,
                                  "complaintCount":2,
                                  "mainReason":"Sales decreased",
                                  "prediction":"Risk may increase",
                                  "recommendedAction":"Review store operations",
                                  "assessedAt":"2026-07-29T12:00:00"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.riskLevel").value("WARNING"))
                .andExpect(jsonPath("$.storeId").value(1));
    }
}
