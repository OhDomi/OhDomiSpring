package com.ohdomi.backend.order;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
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
@RequestMapping("/api/stores/{storeId}")
public class OrderController {
    private final JdbcTemplate jdbc;

    public OrderController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/inventory")
    public List<InventoryResponse> inventory(@PathVariable long storeId) {
        requireStore(storeId);
        return jdbc.query("""
                SELECT inventory_item_id, item_name, category, unit, current_quantity,
                       reorder_level, unit_price, updated_at
                FROM inventory_items WHERE store_id = ? ORDER BY inventory_item_id
                """, (rs, row) -> new InventoryResponse(
                rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getBigDecimal(7),
                rs.getTimestamp(8).toLocalDateTime()), storeId);
    }

    @GetMapping("/order-recommendations")
    public List<OrderRecommendationResponse> recommendations(
            @PathVariable long storeId,
            @RequestParam(required = false) LocalDate date) {
        requireStore(storeId);
        LocalDate recommendationDate = date == null ? LocalDate.now() : date;
        return jdbc.query("""
                SELECT r.recommendation_id, i.inventory_item_id, i.item_name, i.category, i.unit,
                       i.current_quantity, r.expected_usage, r.recommended_quantity, i.unit_price,
                       (r.recommended_quantity * i.unit_price), r.risk_level, r.reason, r.recommendation_date
                FROM order_recommendations r
                JOIN inventory_items i ON i.inventory_item_id = r.inventory_item_id
                WHERE i.store_id = ? AND r.recommendation_date = ? ORDER BY r.recommendation_id
                """, (rs, row) -> new OrderRecommendationResponse(
                rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5),
                rs.getBigDecimal(6), rs.getBigDecimal(7), rs.getBigDecimal(8), rs.getBigDecimal(9),
                rs.getBigDecimal(10), rs.getString(11), rs.getString(12), rs.getObject(13, LocalDate.class)),
                storeId, Date.valueOf(recommendationDate));
    }

    @GetMapping("/purchase-orders")
    public List<PurchaseOrderResponse> purchaseOrders(@PathVariable long storeId) {
        requireStore(storeId);
        return jdbc.query("""
                SELECT po.purchase_order_id, po.order_number, po.status, po.ordered_at,
                       po.expected_at, po.total_amount, po.created_at, COUNT(poi.purchase_order_item_id)
                FROM purchase_orders po
                LEFT JOIN purchase_order_items poi ON poi.purchase_order_id = po.purchase_order_id
                WHERE po.store_id = ?
                GROUP BY po.purchase_order_id, po.order_number, po.status, po.ordered_at,
                         po.expected_at, po.total_amount, po.created_at
                ORDER BY po.created_at DESC
                """, (rs, row) -> new PurchaseOrderResponse(
                rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getTimestamp(4) == null ? null : rs.getTimestamp(4).toLocalDateTime(),
                rs.getTimestamp(5) == null ? null : rs.getTimestamp(5).toLocalDateTime(),
                rs.getBigDecimal(6), rs.getTimestamp(7).toLocalDateTime(), rs.getInt(8)), storeId);
    }

    private void requireStore(long storeId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM stores WHERE store_id = ?", Integer.class, storeId);
        if (count == null || count == 0) throw new ResourceNotFoundException("Store " + storeId + " was not found");
    }

    public record InventoryResponse(long inventoryItemId, String itemName, String category, String unit,
                                    BigDecimal currentQuantity, BigDecimal reorderLevel,
                                    BigDecimal unitPrice, LocalDateTime updatedAt) {}
    public record OrderRecommendationResponse(long recommendationId, long inventoryItemId, String itemName,
                                              String category, String unit, BigDecimal currentQuantity,
                                              BigDecimal expectedUsage, BigDecimal recommendedQuantity,
                                              BigDecimal unitPrice, BigDecimal amount, String riskLevel,
                                              String reason, LocalDate recommendationDate) {}
    public record PurchaseOrderResponse(long purchaseOrderId, String orderNumber, String status,
                                        LocalDateTime orderedAt, LocalDateTime expectedAt,
                                        BigDecimal totalAmount, LocalDateTime createdAt, int itemCount) {}
}
