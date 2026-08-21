package com.ohdomi.backend.hygiene;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

class HygieneChecklistControllerTests {
    @Test
    void checklistKeepsAllThirtyNineItemIdsIncludingRepeatedShootingNames() {
        HygieneAiClient hygieneAi = mock(HygieneAiClient.class);
        List<HygieneAiClient.ChecklistItem> checklist = IntStream.iterate(39, number -> number >= 1, number -> number - 1)
                .mapToObj(number -> item(number,
                        number == 1 || number == 32 ? "천장" : "촬영 항목 " + number))
                .toList();
        when(hygieneAi.checklist()).thenReturn(checklist);

        List<HygieneAiClient.ChecklistItem> response = controller(hygieneAi).checkItems();

        assertThat(response).hasSize(39);
        assertThat(response).extracting(HygieneAiClient.ChecklistItem::itemId)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 39)
                        .mapToObj(number -> "item_%02d".formatted(number))
                        .toList());
        assertThat(response).filteredOn(item -> item.shootingItem().equals("천장"))
                .extracting(HygieneAiClient.ChecklistItem::itemId)
                .containsExactly("item_01", "item_32");
    }

    @Test
    void checklistRejectsAResponseWithAMissingItemId() {
        HygieneAiClient hygieneAi = mock(HygieneAiClient.class);
        when(hygieneAi.checklist()).thenReturn(IntStream.rangeClosed(1, 38)
                .mapToObj(number -> item(number, "촬영 항목 " + number))
                .toList());

        assertThatThrownBy(() -> controller(hygieneAi).checkItems())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("item_01 through item_39");
    }

    private static HygieneController controller(HygieneAiClient hygieneAi) {
        return new HygieneController(mock(JdbcTemplate.class), hygieneAi,
                mock(PlatformTransactionManager.class));
    }

    private static HygieneAiClient.ChecklistItem item(int number, String shootingItem) {
        return new HygieneAiClient.ChecklistItem(
                "item_%02d".formatted(number), number == 32 ? "조리장" : "홀",
                shootingItem, "점검 기준 " + number, "visual", 2, false);
    }
}
