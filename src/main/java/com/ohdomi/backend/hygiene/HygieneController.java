package com.ohdomi.backend.hygiene;

import java.time.LocalDateTime;
import java.util.List;

import com.ohdomi.backend.global.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hygiene-inspections")
public class HygieneController {
    private final JdbcTemplate jdbc;

    public HygieneController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public List<InspectionResponse> inspections(@RequestParam(required = false) Long storeId) {
        String sql = """
                SELECT h.inspection_id, h.store_id, s.name, h.score, h.status, h.reviewer,
                       h.summary, h.inspected_at,
                       (SELECT COUNT(*) FROM hygiene_images hi WHERE hi.inspection_id = h.inspection_id),
                       (SELECT COUNT(*) FROM improvement_tasks it WHERE it.inspection_id = h.inspection_id AND it.status = 'OPEN')
                FROM hygiene_inspections h JOIN stores s ON s.store_id = h.store_id
                """ + (storeId == null ? "" : " WHERE h.store_id = ?") + " ORDER BY h.inspected_at DESC";
        Object[] args = storeId == null ? new Object[0] : new Object[]{storeId};
        return jdbc.query(sql, (rs, row) -> new InspectionResponse(
                rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getInt(4), rs.getString(5),
                rs.getString(6), rs.getString(7), rs.getTimestamp(8).toLocalDateTime(),
                rs.getInt(9), rs.getInt(10)), args);
    }

    @GetMapping("/{inspectionId}")
    public InspectionDetailResponse inspection(@PathVariable long inspectionId) {
        InspectionResponse inspection = inspections(null).stream()
                .filter(item -> item.inspectionId() == inspectionId).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Hygiene inspection " + inspectionId + " was not found"));
        List<CheckResultResponse> results = jdbc.query("""
                SELECT check_result_id, item_name, score, status, memo
                FROM hygiene_check_results WHERE inspection_id = ? ORDER BY check_result_id
                """, (rs, row) -> new CheckResultResponse(
                rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getString(4), rs.getString(5)), inspectionId);
        List<ImprovementTaskResponse> tasks = jdbc.query("""
                SELECT improvement_task_id, title, description, priority, status, due_at, completed_at
                FROM improvement_tasks WHERE inspection_id = ? ORDER BY improvement_task_id
                """, (rs, row) -> new ImprovementTaskResponse(
                rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                rs.getTimestamp(6) == null ? null : rs.getTimestamp(6).toLocalDateTime(),
                rs.getTimestamp(7) == null ? null : rs.getTimestamp(7).toLocalDateTime()), inspectionId);
        return new InspectionDetailResponse(inspection, results, tasks);
    }

    public record InspectionResponse(long inspectionId, long storeId, String storeName, int score,
                                     String status, String reviewer, String summary,
                                     LocalDateTime inspectedAt, int imageCount, int openTaskCount) {}
    public record CheckResultResponse(long checkResultId, String itemName, int score,
                                      String status, String memo) {}
    public record ImprovementTaskResponse(long improvementTaskId, String title, String description,
                                          String priority, String status, LocalDateTime dueAt,
                                          LocalDateTime completedAt) {}
    public record InspectionDetailResponse(InspectionResponse inspection,
                                           List<CheckResultResponse> checkResults,
                                           List<ImprovementTaskResponse> improvementTasks) {}
}
