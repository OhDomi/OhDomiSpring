package com.ohdomi.backend.hygiene;

import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;

import com.ohdomi.backend.global.ResourceNotFoundException;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
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
        List<HygieneImageResponse> images = jdbc.query("""
                SELECT image_id, image_url, category, analysis_result, uploaded_at
                FROM hygiene_images WHERE inspection_id = ? ORDER BY image_id
                """, (rs, row) -> new HygieneImageResponse(
                rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getTimestamp(5).toLocalDateTime()), inspectionId);
        return new InspectionDetailResponse(inspection, results, images, tasks);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public InspectionDetailResponse createInspection(@Valid @RequestBody CreateInspectionRequest request) {
        requireStore(request.storeId());
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO hygiene_inspections
                      (store_id, score, status, reviewer, summary, inspected_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, new String[]{"inspection_id"});
            statement.setLong(1, request.storeId());
            statement.setInt(2, request.score());
            statement.setString(3, request.status().toUpperCase());
            statement.setString(4, request.reviewer().trim());
            statement.setString(5, request.summary());
            statement.setObject(6, request.inspectedAt());
            return statement;
        }, keys);
        Number generatedId = keys.getKey();
        if (generatedId == null) throw new IllegalStateException("Database did not return an inspection id");
        long inspectionId = generatedId.longValue();

        values(request.checkResults()).forEach(result -> jdbc.update("""
                INSERT INTO hygiene_check_results
                  (inspection_id, item_name, score, status, memo)
                VALUES (?, ?, ?, ?, ?)
                """, inspectionId, result.itemName().trim(), result.score(),
                result.status().toUpperCase(), result.memo()));
        values(request.images()).forEach(image -> jdbc.update("""
                INSERT INTO hygiene_images
                  (inspection_id, image_url, category, analysis_result, uploaded_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, inspectionId, image.imageUrl().trim(), image.category().toUpperCase(),
                image.analysisResult()));
        values(request.improvementTasks()).forEach(task -> jdbc.update("""
                INSERT INTO improvement_tasks
                  (inspection_id, title, description, priority, status, due_at, completed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, inspectionId, task.title().trim(), task.description().trim(),
                task.priority().toUpperCase(), task.status().toUpperCase(),
                task.dueAt(), task.completedAt()));
        return inspection(inspectionId);
    }

    private void requireStore(long storeId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM stores WHERE store_id = ?",
                Integer.class, storeId);
        if (count == null || count == 0) {
            throw new ResourceNotFoundException("Store " + storeId + " was not found");
        }
    }

    private static <T> List<T> values(List<T> items) {
        return items == null ? List.of() : items;
    }

    public record InspectionResponse(long inspectionId, long storeId, String storeName, int score,
                                     String status, String reviewer, String summary,
                                     LocalDateTime inspectedAt, int imageCount, int openTaskCount) {}
    public record CheckResultResponse(long checkResultId, String itemName, int score,
                                      String status, String memo) {}
    public record ImprovementTaskResponse(long improvementTaskId, String title, String description,
                                          String priority, String status, LocalDateTime dueAt,
                                          LocalDateTime completedAt) {}
    public record HygieneImageResponse(long imageId, String imageUrl, String category,
                                       String analysisResult, LocalDateTime uploadedAt) {}
    public record InspectionDetailResponse(InspectionResponse inspection,
                                           List<CheckResultResponse> checkResults,
                                           List<HygieneImageResponse> images,
                                           List<ImprovementTaskResponse> improvementTasks) {}

    public record CreateInspectionRequest(
            @NotNull @Positive Long storeId,
            @Min(0) @Max(100) int score,
            @NotBlank @Size(max = 30) String status,
            @NotBlank @Size(max = 100) String reviewer,
            @Size(max = 1000) String summary,
            @NotNull LocalDateTime inspectedAt,
            List<@Valid CheckResultRequest> checkResults,
            List<@Valid HygieneImageRequest> images,
            List<@Valid ImprovementTaskRequest> improvementTasks) {}

    public record CheckResultRequest(
            @NotBlank @Size(max = 100) String itemName,
            @Min(0) @Max(100) int score,
            @NotBlank @Size(max = 30) String status,
            @Size(max = 500) String memo) {}

    public record HygieneImageRequest(
            @NotBlank @Size(max = 1000) String imageUrl,
            @NotBlank @Size(max = 50) String category,
            @Size(max = 1000) String analysisResult) {}

    public record ImprovementTaskRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 1000) String description,
            @NotBlank @Size(max = 20) String priority,
            @NotBlank @Size(max = 30) String status,
            LocalDateTime dueAt,
            LocalDateTime completedAt) {}
}
