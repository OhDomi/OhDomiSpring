package com.ohdomi.backend.hygiene;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.ohdomi.backend.global.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/hygiene-inspections")
@Validated
public class HygieneController {
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final JdbcTemplate jdbc;
    private final HygieneAiClient hygieneAi;
    private final TransactionTemplate transactions;

    public HygieneController(JdbcTemplate jdbc, HygieneAiClient hygieneAi,
                             PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.hygieneAi = hygieneAi;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @GetMapping("/check-items")
    public List<HygieneAiClient.ChecklistItem> checkItems() {
        return hygieneAi.checklist();
    }

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public AnalyzeInspectionResponse analyze(
            @RequestParam @Positive long storeId,
            @RequestParam @NotBlank String itemId,
            @RequestParam(defaultValue = "0") @Min(0) @Max(2) int retakeCount,
            @RequestPart("image") MultipartFile image) {
        requireStore(storeId);
        String contentType = image.getContentType() == null
                ? "" : image.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Image must be JPG, PNG, or WebP");
        }
        if (image.isEmpty()) {
            throw new IllegalArgumentException("Image must not be empty");
        }
        if (image.getSize() > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("Image must be 10MB or smaller");
        }

        byte[] imageBytes;
        try {
            imageBytes = image.getBytes();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not read the uploaded image", exception);
        }
        String fileName = StringUtils.hasText(image.getOriginalFilename())
                ? image.getOriginalFilename() : "hygiene-image";
        HygieneAiClient.ReviewResponse review = hygieneAi.review(
                itemId.trim(), retakeCount, fileName, contentType, imageBytes);
        HygieneAiClient.AiResult aiResult = review.results().get(0);
        InspectionDetailResponse persisted = transactions.execute(status ->
                persistAiInspection(storeId, itemId.trim(), fileName, contentType, imageBytes, aiResult));
        if (persisted == null) {
            throw new IllegalStateException("Could not save the hygiene inspection");
        }
        return new AnalyzeInspectionResponse(persisted, aiResult);
    }

    @GetMapping("/images/{imageId}")
    public ResponseEntity<byte[]> image(@PathVariable long imageId) {
        List<StoredImage> images = jdbc.query("""
                SELECT c.image_data, c.mime_type, c.original_filename
                FROM hygiene_image_contents c JOIN hygiene_images i ON i.image_id = c.image_id
                WHERE c.image_id = ?
                """, (rs, row) -> new StoredImage(rs.getBytes(1), rs.getString(2), rs.getString(3)), imageId);
        StoredImage image = images.stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Hygiene image " + imageId + " was not found"));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.mimeType()))
                .header("Content-Disposition", ContentDisposition.inline()
                        .filename(image.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(image.bytes());
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

    private InspectionDetailResponse persistAiInspection(long storeId, String itemId, String fileName,
                                                          String contentType, byte[] imageBytes,
                                                          HygieneAiClient.AiResult result) {
        int score = result.score() == null ? 0 : Math.max(0, Math.min(result.score(), 100));
        String inspectionStatus = inspectionStatus(result.grade());
        String summary = clip(analysisSummary(result), 1000);

        KeyHolder inspectionKeys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO hygiene_inspections
                      (store_id, score, status, reviewer, summary, inspected_at)
                    VALUES (?, ?, ?, 'Hygiene AI', ?, CURRENT_TIMESTAMP)
                    """, new String[]{"inspection_id"});
            statement.setLong(1, storeId);
            statement.setInt(2, score);
            statement.setString(3, inspectionStatus);
            statement.setString(4, summary);
            return statement;
        }, inspectionKeys);
        long inspectionId = requiredKey(inspectionKeys, "inspection");

        jdbc.update("""
                INSERT INTO hygiene_check_results
                  (inspection_id, item_name, score, status, memo)
                VALUES (?, ?, ?, ?, ?)
                """, inspectionId, clip(result.shootingItem(), 100), score,
                checkStatus(result.grade()), clip(analysisSummary(result), 500));

        KeyHolder imageKeys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO hygiene_images
                      (inspection_id, image_url, category, analysis_result, uploaded_at)
                    VALUES (?, '', ?, ?, CURRENT_TIMESTAMP)
                    """, new String[]{"image_id"});
            statement.setLong(1, inspectionId);
            statement.setString(2, clip(itemId, 50));
            statement.setString(3, clip(analysisDetails(result), 1000));
            return statement;
        }, imageKeys);
        long imageId = requiredKey(imageKeys, "hygiene image");
        String imageUrl = "/api/hygiene-inspections/images/" + imageId;
        jdbc.update("UPDATE hygiene_images SET image_url = ? WHERE image_id = ?", imageUrl, imageId);
        jdbc.update("""
                INSERT INTO hygiene_image_contents
                  (image_id, image_data, mime_type, original_filename, byte_size)
                VALUES (?, ?, ?, ?, ?)
                """, imageId, imageBytes, contentType, clip(fileName, 255), imageBytes.length);

        if (StringUtils.hasText(result.improvement())) {
            jdbc.update("""
                    INSERT INTO improvement_tasks
                      (inspection_id, title, description, priority, status, due_at, completed_at)
                    VALUES (?, ?, ?, ?, 'OPEN', NULL, NULL)
                    """, inspectionId, clip(result.shootingItem() + " 개선", 200),
                    clip(result.improvement(), 1000),
                    "POOR".equals(result.grade()) ? "URGENT" : "WARNING");
        }
        return inspection(inspectionId);
    }

    private static long requiredKey(KeyHolder keys, String entity) {
        Number generatedId = keys.getKey();
        if (generatedId == null) throw new IllegalStateException("Database did not return a " + entity + " id");
        return generatedId.longValue();
    }

    private static String inspectionStatus(String grade) {
        return switch (grade == null ? "" : grade) {
            case "GOOD" -> "GOOD";
            case "POOR" -> "URGENT";
            default -> "WARNING";
        };
    }

    private static String checkStatus(String grade) {
        return switch (grade == null ? "" : grade) {
            case "GOOD" -> "NORMAL";
            case "FAIR" -> "WARNING";
            case "POOR" -> "URGENT";
            default -> "REVIEW";
        };
    }

    private static String analysisSummary(HygieneAiClient.AiResult result) {
        if (StringUtils.hasText(result.improvement())) return result.improvement();
        if (result.findings() != null && !result.findings().isEmpty()) return String.join(" ", result.findings());
        if (StringUtils.hasText(result.recheckReason())) return result.recheckReason();
        return "Hygiene AI analysis completed";
    }

    private static String analysisDetails(HygieneAiClient.AiResult result) {
        String findings = result.findings() == null ? "" : String.join("; ", result.findings());
        return "grade=" + value(result.grade()) + "; findings=" + findings
                + "; improvement=" + value(result.improvement())
                + "; recheck_reason=" + value(result.recheckReason());
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static String clip(String value, int maxLength) {
        String normalized = value == null ? "" : value;
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
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
    public record AnalyzeInspectionResponse(InspectionDetailResponse inspection,
                                            HygieneAiClient.AiResult aiResult) {}
    private record StoredImage(byte[] bytes, String mimeType, String fileName) {}

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
