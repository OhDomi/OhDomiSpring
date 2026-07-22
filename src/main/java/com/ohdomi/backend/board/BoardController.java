package com.ohdomi.backend.board;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import com.ohdomi.backend.global.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/board/posts")
public class BoardController {
    private static final String SELECT_POST = """
            SELECT p.post_id, p.board_type, p.category, p.title, p.content, u.name,
                   p.store_id, p.status, p.is_pinned, p.is_urgent, p.view_count,
                   p.created_at, p.updated_at, a.content
            FROM board_posts p
            JOIN app_users u ON u.user_id = p.author_user_id
            LEFT JOIN board_answers a ON a.post_id = p.post_id
            """;

    private final JdbcTemplate jdbc;

    public BoardController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public List<BoardPostResponse> posts(@RequestParam(defaultValue = "NOTICE") String boardType) {
        String normalizedType = boardType.toUpperCase();
        requireBoardType(normalizedType);
        return jdbc.query(SELECT_POST + " WHERE p.board_type = ? ORDER BY p.is_pinned DESC, p.created_at DESC",
                (rs, row) -> mapPost(rs), normalizedType);
    }

    @GetMapping("/{postId}")
    @Transactional
    public BoardPostResponse post(@PathVariable long postId) {
        int changed = jdbc.update("UPDATE board_posts SET view_count = view_count + 1 WHERE post_id = ?", postId);
        if (changed == 0) throw new ResourceNotFoundException("Board post " + postId + " was not found");
        return find(postId);
    }

    @PostMapping
    @Transactional
    public BoardPostResponse create(@Valid @RequestBody CreatePostRequest request) {
        String boardType = request.boardType().toUpperCase();
        requireBoardType(boardType);
        requireUser(request.authorUserId());
        if (request.storeId() != null) requireStore(request.storeId());
        if (boardType.equals("NOTICE") && !isAdmin(request.authorUserId())) {
            throw new IllegalArgumentException("Only an admin can create a notice");
        }

        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO board_posts
                      (author_user_id, store_id, board_type, category, title, content, status,
                       is_pinned, is_urgent, view_count, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, new String[]{"post_id"});
            statement.setLong(1, request.authorUserId());
            if (request.storeId() == null) statement.setNull(2, java.sql.Types.BIGINT);
            else statement.setLong(2, request.storeId());
            statement.setString(3, boardType);
            statement.setString(4, request.category());
            statement.setString(5, request.title());
            statement.setString(6, request.content());
            statement.setString(7, boardType.equals("NOTICE") ? "PUBLISHED" : "PENDING");
            statement.setBoolean(8, request.isPinned());
            statement.setBoolean(9, request.isUrgent());
            return statement;
        }, keys);
        Number id = keys.getKey();
        if (id == null) throw new IllegalStateException("Database did not return a post id");
        return find(id.longValue());
    }

    @PatchMapping("/{postId}/pin")
    @Transactional
    public BoardPostResponse togglePin(@PathVariable long postId) {
        int changed = jdbc.update("""
                UPDATE board_posts SET is_pinned = CASE WHEN is_pinned THEN FALSE ELSE TRUE END,
                updated_at = CURRENT_TIMESTAMP WHERE post_id = ?
                """, postId);
        if (changed == 0) throw new ResourceNotFoundException("Board post " + postId + " was not found");
        return find(postId);
    }

    @PostMapping("/{postId}/answer")
    @Transactional
    public BoardPostResponse answer(@PathVariable long postId, @Valid @RequestBody AnswerRequest request) {
        requireUser(request.authorUserId());
        if (!isAdmin(request.authorUserId())) throw new IllegalArgumentException("Only an admin can answer an inquiry");
        BoardPostResponse post = find(postId);
        if (!post.boardType().equals("INQUIRY")) throw new IllegalArgumentException("Only an inquiry can be answered");
        jdbc.update("""
                MERGE INTO board_answers (post_id, author_user_id, content, created_at, updated_at) KEY(post_id)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, postId, request.authorUserId(), request.content());
        jdbc.update("UPDATE board_posts SET status = 'ANSWERED', updated_at = CURRENT_TIMESTAMP WHERE post_id = ?", postId);
        return find(postId);
    }

    private BoardPostResponse find(long postId) {
        List<BoardPostResponse> result = jdbc.query(SELECT_POST + " WHERE p.post_id = ?",
                (rs, row) -> mapPost(rs), postId);
        if (result.isEmpty()) throw new ResourceNotFoundException("Board post " + postId + " was not found");
        return result.get(0);
    }

    private BoardPostResponse mapPost(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new BoardPostResponse(
                rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                rs.getString(6), (Long) rs.getObject(7), rs.getString(8), rs.getBoolean(9),
                rs.getBoolean(10), rs.getLong(11), rs.getTimestamp(12).toLocalDateTime(),
                rs.getTimestamp(13).toLocalDateTime(), rs.getString(14));
    }

    private void requireBoardType(String boardType) {
        if (!boardType.equals("NOTICE") && !boardType.equals("INQUIRY")) {
            throw new IllegalArgumentException("boardType must be NOTICE or INQUIRY");
        }
    }

    private void requireUser(long userId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM app_users WHERE user_id = ? AND active = TRUE", Integer.class, userId);
        if (count == null || count == 0) throw new ResourceNotFoundException("User " + userId + " was not found");
    }

    private void requireStore(long storeId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM stores WHERE store_id = ?", Integer.class, storeId);
        if (count == null || count == 0) throw new ResourceNotFoundException("Store " + storeId + " was not found");
    }

    private boolean isAdmin(long userId) {
        Boolean result = jdbc.queryForObject("SELECT role = 'ADMIN' FROM app_users WHERE user_id = ?", Boolean.class, userId);
        return Boolean.TRUE.equals(result);
    }

    public record BoardPostResponse(long postId, String boardType, String category, String title,
                                    String content, String authorName, Long storeId, String status,
                                    boolean isPinned, boolean isUrgent, long viewCount,
                                    LocalDateTime createdAt, LocalDateTime updatedAt, String answer) {}

    public record CreatePostRequest(
            @NotNull @Positive Long authorUserId,
            @Positive Long storeId,
            @NotBlank String boardType,
            @NotBlank @Size(max = 50) String category,
            @NotBlank @Size(max = 200) String title,
            @NotBlank String content,
            boolean isPinned,
            boolean isUrgent) {}

    public record AnswerRequest(
            @NotNull @Positive Long authorUserId,
            @NotBlank String content) {}
}
