package com.ohdomi.backend.store;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import com.ohdomi.backend.global.ConflictException;
import com.ohdomi.backend.global.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/stores")
public class StoreWriteController {
    private final JdbcTemplate jdbc;

    public StoreWriteController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public StoreController.StoreResponse createStore(@Valid @RequestBody StoreRequest request) {
        requireOwner(request.ownerUserId());
        KeyHolder keys = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO stores
                          (owner_user_id, store_code, name, region, address, phone, open_time,
                           close_time, operation_status, opened_on, contract_ends_on,
                           monthly_sales_target, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """, new String[]{"store_id"});
                setStoreFields(statement, request, 1);
                return statement;
            }, keys);
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("Store code " + request.storeCode() + " already exists");
        }
        return findStore(key(keys, "store"));
    }

    @PutMapping("/{storeId}")
    @Transactional
    public StoreController.StoreResponse updateStore(@PathVariable long storeId,
                                                       @Valid @RequestBody StoreRequest request) {
        requireStore(storeId);
        requireOwner(request.ownerUserId());
        try {
            jdbc.update("""
                    UPDATE stores SET owner_user_id=?, store_code=?, name=?, region=?, address=?,
                      phone=?, open_time=?, close_time=?, operation_status=?, opened_on=?,
                      contract_ends_on=?, monthly_sales_target=?, updated_at=CURRENT_TIMESTAMP
                    WHERE store_id=?
                    """, request.ownerUserId(), request.storeCode().trim(), request.name().trim(),
                    request.region().trim(), request.address().trim(), request.phone().trim(),
                    request.openTime(), request.closeTime(), request.operationStatus().toUpperCase(),
                    request.openedOn(), request.contractEndsOn(), request.monthlySalesTarget(), storeId);
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("Store code " + request.storeCode() + " already exists");
        }
        return findStore(storeId);
    }

    @PostMapping("/{storeId}/staff")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public StoreController.StaffShiftResponse createStaffShift(
            @PathVariable long storeId, @Valid @RequestBody StaffShiftRequest request) {
        requireStore(storeId);
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO staff_shifts
                      (store_id, staff_name, staff_role, work_date, starts_at, ends_at, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"staff_shift_id"});
            statement.setLong(1, storeId);
            statement.setString(2, request.name().trim());
            statement.setString(3, request.role().trim());
            statement.setObject(4, request.workDate());
            statement.setObject(5, request.startsAt());
            statement.setObject(6, request.endsAt());
            statement.setString(7, request.status().toUpperCase());
            return statement;
        }, keys);
        long id = key(keys, "staff shift");
        return jdbc.queryForObject("""
                SELECT staff_shift_id, staff_name, staff_role, work_date, starts_at, ends_at, status
                FROM staff_shifts WHERE staff_shift_id=?
                """, (rs, row) -> new StoreController.StaffShiftResponse(
                rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getObject(4, LocalDate.class), rs.getObject(5, LocalTime.class),
                rs.getObject(6, LocalTime.class), rs.getString(7)), id);
    }

    @PostMapping("/{storeId}/facilities")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public FacilityCreatedResponse createFacility(
            @PathVariable long storeId, @Valid @RequestBody FacilityRequest request) {
        requireStore(storeId);
        KeyHolder keys = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO facilities (store_id, name, active) VALUES (?, ?, ?)",
                        new String[]{"facility_id"});
                statement.setLong(1, storeId);
                statement.setString(2, request.name().trim());
                statement.setBoolean(3, request.active() == null || request.active());
                return statement;
            }, keys);
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("Facility " + request.name() + " already exists for store " + storeId);
        }
        return new FacilityCreatedResponse(key(keys, "facility"), storeId, request.name().trim(),
                request.active() == null || request.active());
    }

    @PostMapping("/{storeId}/facilities/{facilityId}/checks")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public FacilityCheckResponse createFacilityCheck(
            @PathVariable long storeId, @PathVariable long facilityId,
            @Valid @RequestBody FacilityCheckRequest request) {
        requireFacility(storeId, facilityId);
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO facility_checks (facility_id, status, memo, checked_at)
                    VALUES (?, ?, ?, ?)
                    """, new String[]{"facility_check_id"});
            statement.setLong(1, facilityId);
            statement.setString(2, request.status().toUpperCase());
            statement.setString(3, request.memo());
            statement.setObject(4, request.checkedAt());
            return statement;
        }, keys);
        return new FacilityCheckResponse(key(keys, "facility check"), facilityId,
                request.status().toUpperCase(), request.memo(), request.checkedAt());
    }

    @PostMapping("/{storeId}/inventory")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public InventoryCreatedResponse createInventoryItem(
            @PathVariable long storeId, @Valid @RequestBody InventoryRequest request) {
        requireStore(storeId);
        KeyHolder keys = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO inventory_items
                          (store_id, item_name, category, unit, current_quantity, reorder_level,
                           unit_price, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                        """, new String[]{"inventory_item_id"});
                statement.setLong(1, storeId);
                setInventoryFields(statement, request, 2);
                return statement;
            }, keys);
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("Inventory item " + request.itemName() + " already exists for store " + storeId);
        }
        return findInventoryItem(storeId, key(keys, "inventory item"));
    }

    @PutMapping("/{storeId}/inventory/{inventoryItemId}")
    @Transactional
    public InventoryCreatedResponse updateInventoryItem(
            @PathVariable long storeId, @PathVariable long inventoryItemId,
            @Valid @RequestBody InventoryRequest request) {
        requireInventoryItem(storeId, inventoryItemId);
        try {
            jdbc.update("""
                    UPDATE inventory_items SET item_name=?, category=?, unit=?, current_quantity=?,
                      reorder_level=?, unit_price=?, updated_at=CURRENT_TIMESTAMP
                    WHERE inventory_item_id=? AND store_id=?
                    """, request.itemName().trim(), request.category().trim(), request.unit().trim(),
                    request.currentQuantity(), request.reorderLevel(), request.unitPrice(),
                    inventoryItemId, storeId);
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("Inventory item " + request.itemName() + " already exists for store " + storeId);
        }
        return findInventoryItem(storeId, inventoryItemId);
    }

    @PostMapping("/{storeId}/customer-orders")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public CustomerOrderResponse createCustomerOrder(
            @PathVariable long storeId, @Valid @RequestBody CustomerOrderRequest request) {
        requireStore(storeId);
        String channel = request.channel().toUpperCase();
        if (!List.of("IN_STORE", "DELIVERY", "TAKEOUT").contains(channel)) {
            throw new IllegalArgumentException("channel must be IN_STORE, DELIVERY, or TAKEOUT");
        }
        request.items().forEach(item -> requireMenuItem(item.menuItemId()));
        BigDecimal total = request.items().stream()
                .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO customer_orders (store_id, channel, ordered_at, total_amount, status)
                    VALUES (?, ?, ?, ?, ?)
                    """, new String[]{"customer_order_id"});
            statement.setLong(1, storeId);
            statement.setString(2, channel);
            statement.setObject(3, request.orderedAt());
            statement.setBigDecimal(4, total);
            statement.setString(5, request.status().toUpperCase());
            return statement;
        }, keys);
        long orderId = key(keys, "customer order");
        request.items().forEach(item -> jdbc.update("""
                INSERT INTO customer_order_items
                  (customer_order_id, menu_item_id, quantity, unit_price)
                VALUES (?, ?, ?, ?)
                """, orderId, item.menuItemId(), item.quantity(), item.unitPrice()));
        return new CustomerOrderResponse(orderId, storeId, channel, request.orderedAt(), total,
                request.status().toUpperCase(), request.items());
    }

    @PostMapping("/{storeId}/purchase-orders")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public PurchaseOrderCreatedResponse createPurchaseOrder(
            @PathVariable long storeId, @Valid @RequestBody PurchaseOrderRequest request) {
        requireStore(storeId);
        String status = request.status().toUpperCase();
        if (!List.of("DRAFT", "ORDERED", "SHIPPING", "RECEIVED", "CANCELLED").contains(status)) {
            throw new IllegalArgumentException("Invalid purchase order status");
        }
        request.items().forEach(item -> requireInventoryItem(storeId, item.inventoryItemId()));
        BigDecimal total = request.items().stream()
                .map(item -> item.unitPrice().multiply(item.quantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        KeyHolder keys = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO purchase_orders
                          (store_id, order_number, status, ordered_at, expected_at, total_amount, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                        """, new String[]{"purchase_order_id"});
                statement.setLong(1, storeId);
                statement.setString(2, request.orderNumber().trim());
                statement.setString(3, status);
                statement.setObject(4, request.orderedAt());
                statement.setObject(5, request.expectedAt());
                statement.setBigDecimal(6, total);
                return statement;
            }, keys);
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("Purchase order " + request.orderNumber() + " already exists");
        }
        long orderId = key(keys, "purchase order");
        request.items().forEach(item -> jdbc.update("""
                INSERT INTO purchase_order_items
                  (purchase_order_id, inventory_item_id, quantity, unit_price)
                VALUES (?, ?, ?, ?)
                """, orderId, item.inventoryItemId(), item.quantity(), item.unitPrice()));
        return new PurchaseOrderCreatedResponse(orderId, storeId, request.orderNumber().trim(), status,
                request.orderedAt(), request.expectedAt(), total, request.items());
    }

    @PostMapping("/{storeId}/order-recommendations")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public RecommendationCreatedResponse createRecommendation(
            @PathVariable long storeId, @Valid @RequestBody RecommendationRequest request) {
        requireInventoryItem(storeId, request.inventoryItemId());
        KeyHolder keys = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO order_recommendations
                          (inventory_item_id, recommendation_date, expected_usage,
                           recommended_quantity, risk_level, reason, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                        """, new String[]{"recommendation_id"});
                statement.setLong(1, request.inventoryItemId());
                statement.setObject(2, request.recommendationDate());
                statement.setBigDecimal(3, request.expectedUsage());
                statement.setBigDecimal(4, request.recommendedQuantity());
                statement.setString(5, request.riskLevel().toUpperCase());
                statement.setString(6, request.reason().trim());
                return statement;
            }, keys);
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("A recommendation already exists for this inventory item and date");
        }
        return new RecommendationCreatedResponse(key(keys, "recommendation"), storeId,
                request.inventoryItemId(), request.recommendationDate(), request.expectedUsage(),
                request.recommendedQuantity(), request.riskLevel().toUpperCase(), request.reason().trim());
    }

    private void setStoreFields(PreparedStatement statement, StoreRequest request, int offset)
            throws java.sql.SQLException {
        statement.setLong(offset, request.ownerUserId());
        statement.setString(offset + 1, request.storeCode().trim());
        statement.setString(offset + 2, request.name().trim());
        statement.setString(offset + 3, request.region().trim());
        statement.setString(offset + 4, request.address().trim());
        statement.setString(offset + 5, request.phone().trim());
        statement.setObject(offset + 6, request.openTime());
        statement.setObject(offset + 7, request.closeTime());
        statement.setString(offset + 8, request.operationStatus().toUpperCase());
        statement.setObject(offset + 9, request.openedOn());
        statement.setObject(offset + 10, request.contractEndsOn());
        statement.setBigDecimal(offset + 11, request.monthlySalesTarget());
    }

    private void setInventoryFields(PreparedStatement statement, InventoryRequest request, int offset)
            throws java.sql.SQLException {
        statement.setString(offset, request.itemName().trim());
        statement.setString(offset + 1, request.category().trim());
        statement.setString(offset + 2, request.unit().trim());
        statement.setBigDecimal(offset + 3, request.currentQuantity());
        statement.setBigDecimal(offset + 4, request.reorderLevel());
        statement.setBigDecimal(offset + 5, request.unitPrice());
    }

    private StoreController.StoreResponse findStore(long storeId) {
        List<StoreController.StoreResponse> stores = jdbc.query("""
                SELECT s.store_id, s.store_code, s.name, u.name, s.region, s.address, s.phone,
                       s.open_time, s.close_time, s.operation_status, s.opened_on,
                       s.contract_ends_on, s.monthly_sales_target
                FROM stores s JOIN app_users u ON u.user_id=s.owner_user_id WHERE s.store_id=?
                """, (rs, row) -> new StoreController.StoreResponse(
                rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getString(6), rs.getString(7),
                rs.getObject(8, LocalTime.class), rs.getObject(9, LocalTime.class), rs.getString(10),
                rs.getObject(11, LocalDate.class), rs.getObject(12, LocalDate.class), rs.getBigDecimal(13)), storeId);
        if (stores.isEmpty()) throw new ResourceNotFoundException("Store " + storeId + " was not found");
        return stores.get(0);
    }

    private InventoryCreatedResponse findInventoryItem(long storeId, long inventoryItemId) {
        List<InventoryCreatedResponse> items = jdbc.query("""
                SELECT inventory_item_id, store_id, item_name, category, unit, current_quantity,
                       reorder_level, unit_price, updated_at
                FROM inventory_items WHERE inventory_item_id=? AND store_id=?
                """, (rs, row) -> new InventoryCreatedResponse(
                rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5),
                rs.getBigDecimal(6), rs.getBigDecimal(7), rs.getBigDecimal(8),
                rs.getTimestamp(9).toLocalDateTime()), inventoryItemId, storeId);
        if (items.isEmpty()) throw new ResourceNotFoundException("Inventory item " + inventoryItemId + " was not found");
        return items.get(0);
    }

    private void requireOwner(long userId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM app_users WHERE user_id=? AND role='OWNER' AND active=TRUE
                """, Integer.class, userId);
        if (count == null || count == 0) {
            throw new ResourceNotFoundException("Active owner " + userId + " was not found");
        }
    }

    private void requireStore(long storeId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM stores WHERE store_id=?", Integer.class, storeId);
        if (count == null || count == 0) throw new ResourceNotFoundException("Store " + storeId + " was not found");
    }

    private void requireFacility(long storeId, long facilityId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM facilities WHERE facility_id=? AND store_id=?",
                Integer.class, facilityId, storeId);
        if (count == null || count == 0) throw new ResourceNotFoundException("Facility " + facilityId + " was not found");
    }

    private void requireInventoryItem(long storeId, long inventoryItemId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory_items WHERE inventory_item_id=? AND store_id=?",
                Integer.class, inventoryItemId, storeId);
        if (count == null || count == 0) {
            throw new ResourceNotFoundException("Inventory item " + inventoryItemId + " was not found");
        }
    }

    private void requireMenuItem(long menuItemId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM menu_items WHERE menu_item_id=? AND active=TRUE",
                Integer.class, menuItemId);
        if (count == null || count == 0) throw new ResourceNotFoundException("Menu item " + menuItemId + " was not found");
    }

    private long key(KeyHolder keys, String resource) {
        Number value = keys.getKey();
        if (value == null) throw new IllegalStateException("Database did not return a " + resource + " id");
        return value.longValue();
    }

    public record StoreRequest(
            @NotNull @Positive Long ownerUserId,
            @NotBlank @Size(max = 30) String storeCode,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 100) String region,
            @NotBlank @Size(max = 255) String address,
            @NotBlank @Size(max = 30) String phone,
            @NotNull LocalTime openTime,
            @NotNull LocalTime closeTime,
            @NotBlank @Size(max = 30) String operationStatus,
            LocalDate openedOn,
            LocalDate contractEndsOn,
            @NotNull @DecimalMin("0") BigDecimal monthlySalesTarget) {}

    public record StaffShiftRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 50) String role,
            @NotNull LocalDate workDate,
            @NotNull LocalTime startsAt,
            @NotNull LocalTime endsAt,
            @NotBlank @Size(max = 30) String status) {}

    public record FacilityRequest(@NotBlank @Size(max = 100) String name, Boolean active) {}
    public record FacilityCreatedResponse(long facilityId, long storeId, String name, boolean active) {}

    public record FacilityCheckRequest(
            @NotBlank @Size(max = 30) String status,
            @Size(max = 500) String memo,
            @NotNull LocalDateTime checkedAt) {}
    public record FacilityCheckResponse(long facilityCheckId, long facilityId, String status,
                                        String memo, LocalDateTime checkedAt) {}

    public record InventoryRequest(
            @NotBlank @Size(max = 100) String itemName,
            @NotBlank @Size(max = 50) String category,
            @NotBlank @Size(max = 20) String unit,
            @NotNull @DecimalMin("0") BigDecimal currentQuantity,
            @NotNull @DecimalMin("0") BigDecimal reorderLevel,
            @NotNull @DecimalMin("0") BigDecimal unitPrice) {}
    public record InventoryCreatedResponse(long inventoryItemId, long storeId, String itemName,
                                           String category, String unit, BigDecimal currentQuantity,
                                           BigDecimal reorderLevel, BigDecimal unitPrice,
                                           LocalDateTime updatedAt) {}

    public record CustomerOrderItemRequest(
            @NotNull @Positive Long menuItemId,
            @Positive int quantity,
            @NotNull @DecimalMin("0") BigDecimal unitPrice) {}
    public record CustomerOrderRequest(
            @NotBlank String channel,
            @NotNull LocalDateTime orderedAt,
            @NotBlank @Size(max = 30) String status,
            @NotEmpty List<@Valid CustomerOrderItemRequest> items) {}
    public record CustomerOrderResponse(long customerOrderId, long storeId, String channel,
                                        LocalDateTime orderedAt, BigDecimal totalAmount, String status,
                                        List<CustomerOrderItemRequest> items) {}

    public record PurchaseOrderItemRequest(
            @NotNull @Positive Long inventoryItemId,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
            @NotNull @DecimalMin("0") BigDecimal unitPrice) {}
    public record PurchaseOrderRequest(
            @NotBlank @Size(max = 40) String orderNumber,
            @NotBlank String status,
            LocalDateTime orderedAt,
            LocalDateTime expectedAt,
            @NotEmpty List<@Valid PurchaseOrderItemRequest> items) {}
    public record PurchaseOrderCreatedResponse(long purchaseOrderId, long storeId, String orderNumber,
                                               String status, LocalDateTime orderedAt,
                                               LocalDateTime expectedAt, BigDecimal totalAmount,
                                               List<PurchaseOrderItemRequest> items) {}

    public record RecommendationRequest(
            @NotNull @Positive Long inventoryItemId,
            @NotNull LocalDate recommendationDate,
            @NotNull @DecimalMin("0") BigDecimal expectedUsage,
            @NotNull @DecimalMin("0") BigDecimal recommendedQuantity,
            @NotBlank @Size(max = 20) String riskLevel,
            @NotBlank @Size(max = 500) String reason) {}
    public record RecommendationCreatedResponse(long recommendationId, long storeId,
                                                long inventoryItemId, LocalDate recommendationDate,
                                                BigDecimal expectedUsage, BigDecimal recommendedQuantity,
                                                String riskLevel, String reason) {}
}
