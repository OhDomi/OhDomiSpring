package com.ohdomi.backend.hygiene;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class HygieneAnalysisIntegrationTests {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    HygieneAiClient hygieneAi;

    @Test
    void checklistIsExposedToReactWithCamelCaseFields() throws Exception {
        when(hygieneAi.checklist()).thenReturn(List.of(new HygieneAiClient.ChecklistItem(
                "item_01", "홀", "천장", "천장 청결 상태 확인", "visual", 2, false)));

        mockMvc.perform(get("/api/hygiene-inspections/check-items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].itemId").value("item_01"))
                .andExpect(jsonPath("$[0].shootingItem").value("천장"))
                .andExpect(jsonPath("$[0].aiCheckPoint").value("천장 청결 상태 확인"))
                .andExpect(jsonPath("$[0].item_id").doesNotExist());
    }

    @Test
    void imageIsAnalyzedAndPersistedWithBinaryContent() throws Exception {
        byte[] imageBytes = new byte[]{1, 2, 3, 4, 5};
        HygieneAiClient.AiResult result = new HygieneAiClient.AiResult(
                "item_03", "홀", "바닥", false, "FAIR", 68, "주의",
                List.of("바닥에 오염 흔적이 있습니다."), "바닥을 세척해 주세요.", null, 0);
        when(hygieneAi.review(eq("item_03"), eq(0), eq("floor.jpg"),
                eq("image/jpeg"), any(byte[].class))).thenReturn(new HygieneAiClient.ReviewResponse(
                List.of(result), List.of(), List.of(), List.of(), true,
                Map.of("홀", 68.0), 68.0));

        mockMvc.perform(multipart("/api/hygiene-inspections/analyze")
                        .file(new MockMultipartFile("image", "floor.jpg", "image/jpeg", imageBytes))
                        .param("storeId", "1")
                        .param("itemId", "item_03"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.aiResult.grade").value("FAIR"))
                .andExpect(jsonPath("$.aiResult.score").value(68))
                .andExpect(jsonPath("$.inspection.inspection.score").value(68))
                .andExpect(jsonPath("$.inspection.images[0].imageUrl").isNotEmpty());

        Long imageId = jdbc.queryForObject("""
                SELECT MAX(c.image_id) FROM hygiene_image_contents c
                WHERE c.original_filename = 'floor.jpg'
                """, Long.class);
        assertThat(imageId).isNotNull();
        assertThat(jdbc.queryForObject(
                "SELECT image_data FROM hygiene_image_contents WHERE image_id = ?",
                byte[].class, imageId)).isEqualTo(imageBytes);

        mockMvc.perform(get("/api/hygiene-inspections/images/{imageId}", imageId))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(content().bytes(imageBytes));
    }
}
