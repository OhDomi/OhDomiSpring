package com.ohdomi.backend.store;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import com.ohdomi.backend.global.ResourceNotFoundException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stores")
public class StoreController {
    private final JdbcTemplate jdbc;

    public StoreController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public List<StoreResponse> stores() {
        return jdbc.query("""
                SELECT s.store_id, s.store_code, s.name, u.name, s.region, s.address, s.phone,
                       s.open_time, s.close_time, s.operation_status, s.opened_on,
                       s.contract_ends_on, s.exclusive_area_sqm, s.latitude, s.longitude,
                       s.monthly_sales_target
                FROM stores s JOIN app_users u ON u.user_id = s.owner_user_id
                ORDER BY s.store_id
                """, (rs, row) -> new StoreResponse(
                rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getString(6), rs.getString(7),
                rs.getObject(8, LocalTime.class), rs.getObject(9, LocalTime.class), rs.getString(10),
                rs.getObject(11, LocalDate.class), rs.getObject(12, LocalDate.class),
                rs.getBigDecimal(13), rs.getBigDecimal(14), rs.getBigDecimal(15), rs.getBigDecimal(16)));
    }

    @GetMapping("/{storeId}")
    public StoreResponse store(@PathVariable long storeId) {
        return stores().stream().filter(store -> store.storeId() == storeId).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Store " + storeId + " was not found"));
    }

    @GetMapping("/{storeId}/staff")
    public List<StaffShiftResponse> staff(
            @PathVariable long storeId,
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now()}")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        requireStore(storeId);
        return jdbc.query("""
                SELECT staff_shift_id, staff_name, staff_role, work_date, starts_at, ends_at, status
                FROM staff_shifts WHERE store_id = ? AND work_date = ? ORDER BY starts_at
                """, (rs, row) -> new StaffShiftResponse(
                rs.getLong(1), rs.getString(2), rs.getString(3), rs.getObject(4, LocalDate.class),
                rs.getObject(5, LocalTime.class), rs.getObject(6, LocalTime.class), rs.getString(7)),
                storeId, Date.valueOf(date));
    }

    @GetMapping("/{storeId}/facilities")
    public List<FacilityResponse> facilities(@PathVariable long storeId) {
        requireStore(storeId);
        return jdbc.query("""
                SELECT f.facility_id, f.name, fc.status, fc.memo, fc.checked_at
                FROM facilities f
                LEFT JOIN facility_checks fc ON fc.facility_check_id = (
                    SELECT MAX(fc2.facility_check_id) FROM facility_checks fc2 WHERE fc2.facility_id = f.facility_id)
                WHERE f.store_id = ? AND f.active = TRUE ORDER BY f.facility_id
                """, (rs, row) -> new FacilityResponse(
                rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                timestamp(rs.getTimestamp(5))), storeId);
    }

    @GetMapping("/{storeId}/sales-summary")
    public SalesSummaryResponse salesSummary(
            @PathVariable long storeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        requireStore(storeId);
        if (to.isBefore(from)) throw new IllegalArgumentException("to must be on or after from");
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(total_amount), 0), COUNT(*),
                       COALESCE(AVG(total_amount), 0)
                FROM customer_orders
                WHERE store_id = ? AND ordered_at >= ? AND ordered_at < ? AND status = 'COMPLETED'
                """, (rs, row) -> new SalesSummaryResponse(
                storeId, from, to, rs.getBigDecimal(1), rs.getLong(2), rs.getBigDecimal(3)),
                storeId, Timestamp.valueOf(from.atStartOfDay()), Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
    }

    private void requireStore(long storeId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM stores WHERE store_id = ?", Integer.class, storeId);
        if (count == null || count == 0) throw new ResourceNotFoundException("Store " + storeId + " was not found");
    }

    private static LocalDateTime timestamp(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    public record StoreResponse(long storeId, String storeCode, String storeName, String ownerName,
                                String region, String address, String phone, LocalTime openTime,
                                LocalTime closeTime, String operationStatus, LocalDate openedOn,
                                LocalDate contractEndsOn, BigDecimal exclusiveAreaSqm,
                                BigDecimal latitude, BigDecimal longitude,
                                BigDecimal monthlySalesTarget) {}
    public record StaffShiftResponse(long staffShiftId, String name, String role, LocalDate workDate,
                                     LocalTime startsAt, LocalTime endsAt, String status) {}
    public record FacilityResponse(long facilityId, String name, String status, String memo,
                                   LocalDateTime checkedAt) {}
    public record SalesSummaryResponse(long storeId, LocalDate from, LocalDate to, BigDecimal sales,
                                       long orders, BigDecimal averageOrderAmount) {}
}
