package com.ohdomi.backend.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.ohdomi.backend.global.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ui")
public class UiDataController {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private final JdbcTemplate jdbc;

    public UiDataController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/stores/{storeId}/overview")
    public Map<String, Object> storeOverview(@PathVariable long storeId) {
        return m("management", storeManagement(storeId), "hygiene", hygiene(storeId),
                "orders", orders(storeId), "sales", sales(storeId));
    }

    @GetMapping("/admin/overview")
    public Map<String, Object> adminOverview() {
        return m("stores", adminStores(), "hygiene", adminHygiene(),
                "sales", adminSales(), "risks", adminRisks());
    }

    @GetMapping("/stores/{storeId}/management")
    public Map<String, Object> storeManagement(@PathVariable long storeId) {
        Map<String, Object> store = one("""
                SELECT s.name, u.name owner_name, s.region, s.address, s.phone, s.open_time,
                       s.close_time, s.operation_status
                FROM stores s JOIN app_users u ON u.user_id = s.owner_user_id WHERE s.store_id = ?
                """, storeId);
        List<Map<String, Object>> facilities = jdbc.query("""
                SELECT f.name, fc.status, fc.checked_at, fc.memo
                FROM facilities f LEFT JOIN facility_checks fc ON fc.facility_check_id =
                  (SELECT MAX(x.facility_check_id) FROM facility_checks x WHERE x.facility_id=f.facility_id)
                WHERE f.store_id=? AND f.active=TRUE ORDER BY f.facility_id
                """, (rs, n) -> m("name", rs.getString(1), "status", statusKo(rs.getString(2)),
                "lastCheckedAt", dateTime(rs.getTimestamp(3)), "memo", rs.getString(4)), storeId);
        List<Map<String, Object>> staff = jdbc.query("""
                SELECT staff_name, staff_role, starts_at, ends_at, status FROM staff_shifts
                WHERE store_id=? AND work_date=(SELECT MAX(work_date) FROM staff_shifts WHERE store_id=?)
                ORDER BY starts_at
                """, (rs, n) -> m("name", rs.getString(1), "role", rs.getString(2),
                "workTime", time(rs.getTime(3).toLocalTime()) + " - " + time(rs.getTime(4).toLocalTime()),
                "status", staffStatusKo(rs.getString(5))), storeId, storeId);
        List<Map<String, Object>> checklist = facilities.stream().map(item -> m(
                "task", item.get("name") + " 운영 상태 확인",
                "checked", "정상".equals(item.get("status")))).toList();
        return m("storeInfo", m("storeName", store.get("name"), "ownerName", store.get("owner_name"),
                        "region", store.get("region"), "address", store.get("address"), "phone", store.get("phone"),
                        "openTime", timeValue(store.get("open_time")),
                        "closeTime", timeValue(store.get("close_time")),
                        "operationStatus", "OPEN".equals(store.get("operation_status")) ? "영업 중" : "영업 종료"),
                "facilityStatus", facilities, "todayStaff", staff, "operationChecklist", checklist);
    }

    @GetMapping("/stores/{storeId}/hygiene")
    public Map<String, Object> hygiene(@PathVariable long storeId) {
        Map<String, Object> latest = one("""
                SELECT inspection_id, score, status, inspected_at FROM hygiene_inspections
                WHERE store_id=? ORDER BY inspected_at DESC LIMIT 1
                """, storeId);
        long inspectionId = ((Number) latest.get("inspection_id")).longValue();
        List<Map<String, Object>> items = jdbc.query("""
                SELECT item_name,status,score,memo FROM hygiene_check_results
                WHERE inspection_id=? ORDER BY check_result_id
                """, (rs, n) -> m("name", rs.getString(1), "status", statusKo(rs.getString(2)),
                "score", rs.getInt(3), "memo", rs.getString(4)), inspectionId);
        List<Map<String, Object>> tasks = jdbc.query("""
                SELECT title,priority,description FROM improvement_tasks
                WHERE inspection_id=? AND status='OPEN' ORDER BY improvement_task_id
                """, (rs, n) -> m("title", rs.getString(1), "priority", priorityKo(rs.getString(2)),
                "description", rs.getString(3)), inspectionId);
        List<Map<String, Object>> recent = jdbc.query("""
                SELECT inspected_at,score,status FROM hygiene_inspections WHERE store_id=?
                ORDER BY inspected_at DESC LIMIT 7
                """, (rs, n) -> m("date", rs.getTimestamp(1).toLocalDateTime().toLocalDate().format(DATE),
                "score", rs.getInt(2), "result", inspectionStatusKo(rs.getString(3))), storeId);
        Integer images = jdbc.queryForObject("SELECT COUNT(*) FROM hygiene_images WHERE inspection_id=?", Integer.class, inspectionId);
        return m("hygieneSummary", m("score", latest.get("score"),
                        "status", inspectionStatusKo((String) latest.get("status")),
                        "lastCheckedAt", dateTime((Timestamp) latest.get("inspected_at")),
                        "uploadedImages", images, "issueCount", tasks.size()),
                "hygieneItems", items, "improvementTasks", tasks, "recentInspections", recent);
    }

    @GetMapping("/stores/{storeId}/orders")
    public Map<String, Object> orders(@PathVariable long storeId) {
        List<Map<String, Object>> recommendations = jdbc.query("""
                SELECT r.recommendation_id,i.item_name,i.category,i.current_quantity,r.expected_usage,
                       r.recommended_quantity,i.unit,i.unit_price,r.risk_level,r.reason
                FROM order_recommendations r JOIN inventory_items i ON i.inventory_item_id=r.inventory_item_id
                WHERE i.store_id=? AND r.recommendation_date=(SELECT MAX(r2.recommendation_date)
                  FROM order_recommendations r2 JOIN inventory_items i2 ON i2.inventory_item_id=r2.inventory_item_id
                  WHERE i2.store_id=?) ORDER BY r.recommendation_id
                """, (rs, n) -> {
            BigDecimal quantity = rs.getBigDecimal(6);
            BigDecimal unitPrice = rs.getBigDecimal(8);
            String unit = rs.getString(7);
            return m("id", rs.getLong(1), "item", rs.getString(2), "category", rs.getString(3),
                    "currentStock", quantity(rs.getBigDecimal(4), unit), "expectedUsage", quantity(rs.getBigDecimal(5), unit),
                    "recommendedQty", quantity(quantity, unit), "unitPrice", won(unitPrice),
                    "amount", won(quantity.multiply(unitPrice)), "risk", riskKo(rs.getString(9)), "reason", rs.getString(10));
        }, storeId, storeId);
        List<Map<String, Object>> recent = jdbc.query("""
                SELECT po.created_at,po.order_number,po.total_amount,po.status,COUNT(poi.purchase_order_item_id)
                FROM purchase_orders po LEFT JOIN purchase_order_items poi ON poi.purchase_order_id=po.purchase_order_id
                WHERE po.store_id=? GROUP BY po.purchase_order_id,po.created_at,po.order_number,po.total_amount,po.status
                ORDER BY po.created_at DESC LIMIT 10
                """, (rs, n) -> m("date", rs.getTimestamp(1).toLocalDateTime().toLocalDate().format(DATE),
                "orderNo", rs.getString(2), "items", rs.getInt(5) + "개 품목", "amount", won(rs.getBigDecimal(3)),
                "status", orderStatusKo(rs.getString(4))), storeId);
        BigDecimal amount = recommendations.stream().map(x -> money((String) x.get("amount")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> sales = aggregate("SELECT COALESCE(SUM(total_amount),0) sales,COUNT(*) orders FROM customer_orders WHERE store_id=?", storeId);
        long required = recommendations.stream().filter(x -> !String.valueOf(x.get("recommendedQty")).startsWith("0")).count();
        List<Map<String, Object>> insights = recommendations.stream().limit(3).map(x -> m(
                "title", x.get("item") + " 발주 분석", "description", x.get("reason"),
                "type", "부족".equals(x.get("risk")) ? "danger" : "positive")).toList();
        return m("orderSummary", m("expectedSales", won(decimal(sales.get("sales"))),
                        "expectedOrders", ((Number) sales.get("orders")).longValue(), "requiredItems", required,
                        "estimatedAmount", won(amount), "autoOrderRate", recommendations.isEmpty() ? 0 : 100),
                "recommendedOrders", recommendations, "recentOrders", recent, "aiOrderInsights", insights);
    }

    @GetMapping("/stores/{storeId}/sales")
    public Map<String, Object> sales(@PathVariable long storeId) {
        Map<String, Object> totals = aggregate("SELECT COALESCE(SUM(total_amount),0) sales,COUNT(*) orders FROM customer_orders WHERE store_id=?", storeId);
        BigDecimal totalSales = decimal(totals.get("sales"));
        long totalOrders = ((Number) totals.get("orders")).longValue();
        List<Map<String, Object>> hourly = jdbc.query("""
                SELECT HOUR(ordered_at),COALESCE(SUM(total_amount),0) FROM customer_orders
                WHERE store_id=? GROUP BY HOUR(ordered_at) ORDER BY HOUR(ordered_at)
                """, (rs, n) -> m("time", String.format("%02d시", rs.getInt(1)),
                "sales", rs.getBigDecimal(2).divide(BigDecimal.valueOf(10_000), 0, RoundingMode.HALF_UP)), storeId);
        List<Map<String, Object>> menus = jdbc.query("""
                SELECT m.name,m.category,SUM(oi.quantity),SUM(oi.quantity*oi.unit_price)
                FROM customer_order_items oi JOIN customer_orders o ON o.customer_order_id=oi.customer_order_id
                JOIN menu_items m ON m.menu_item_id=oi.menu_item_id WHERE o.store_id=?
                GROUP BY m.menu_item_id,m.name,m.category ORDER BY SUM(oi.quantity*oi.unit_price) DESC
                """, (rs, n) -> m("rank", n + 1, "name", rs.getString(1), "category", rs.getString(2),
                "orders", rs.getLong(3), "sales", won(rs.getBigDecimal(4)), "change", "0%"), storeId);
        List<Map<String, Object>> channels = jdbc.query("""
                SELECT channel,SUM(total_amount) FROM customer_orders WHERE store_id=? GROUP BY channel
                """, (rs, n) -> {
            BigDecimal value = rs.getBigDecimal(2);
            int rate = totalSales.signum() == 0 ? 0 : value.multiply(BigDecimal.valueOf(100))
                    .divide(totalSales, 0, RoundingMode.HALF_UP).intValue();
            return m("channel", channelKo(rs.getString(1)), "sales", won(value), "rate", rate);
        }, storeId);
        BigDecimal average = totalOrders == 0 ? BigDecimal.ZERO : totalSales.divide(BigDecimal.valueOf(totalOrders), 0, RoundingMode.HALF_UP);
        return m("salesSummary", m("todaySales", won(totalSales), "todayOrders", totalOrders,
                        "averageOrderPrice", won(average), "monthlySales", won(totalSales),
                        "monthlyTarget", won(storeTarget(storeId)), "targetRate", targetRate(totalSales, storeTarget(storeId))),
                "hourlySales", hourly, "menuRanking", menus, "channelSales", channels,
                "aiInsights", List.of(m("title", "MySQL 매출 데이터 분석", "description",
                        totalOrders + "건의 주문 데이터를 기준으로 집계했습니다.", "type", "info")));
    }

    @GetMapping("/admin/stores")
    public Map<String, Object> adminStores() {
        // 2026-08-08: 216개 실매장 임포트(store_code='KG-%')를 지원하기 위해 두 가지 보강 —
        // (1) store_code를 노출해 프런트가 "임포트" 배지를 붙일 수 있게 함(구분 없이 섞이면
        // 실제 가입 매장처럼 보인다는 리포트). (2) 주문이 아예 없는 매장은 "₩0"이 아니라
        // "데이터 없음"으로 — COALESCE(SUM,0)만 쓰면 "매출이 0원"과 "매출 데이터 자체가
        // 없음"이 구분이 안 돼, 임포트 매장 216개가 전부 매출 0원짜리 매장처럼 보이는 문제.
        List<Map<String, Object>> stores = jdbc.query("""
                SELECT s.store_id,s.name,u.name,s.region,s.address,s.phone,s.contract_ends_on,
                  COALESCE((SELECT SUM(o.total_amount) FROM customer_orders o WHERE o.store_id=s.store_id),0),
                  COALESCE((SELECT h.score FROM hygiene_inspections h WHERE h.store_id=s.store_id ORDER BY h.inspected_at DESC LIMIT 1),0),
                  COALESCE((SELECT r.risk_level FROM risk_assessments r WHERE r.store_id=s.store_id ORDER BY r.assessed_at DESC LIMIT 1),1),
                  (SELECT h.inspected_at FROM hygiene_inspections h WHERE h.store_id=s.store_id ORDER BY h.inspected_at DESC LIMIT 1),
                  COALESCE((SELECT h.summary FROM hygiene_inspections h WHERE h.store_id=s.store_id ORDER BY h.inspected_at DESC LIMIT 1),'특이사항 없음'),
                  s.store_code,
                  (SELECT COUNT(*) FROM customer_orders o WHERE o.store_id=s.store_id),
                  (SELECT r.main_reason FROM risk_assessments r WHERE r.store_id=s.store_id ORDER BY r.assessed_at DESC LIMIT 1)
                FROM stores s JOIN app_users u ON u.user_id=s.owner_user_id ORDER BY s.store_id
                """, (rs, n) -> {
            String storeCode = rs.getString(13);
            boolean imported = storeCode != null && storeCode.startsWith("KG-");
            long orderCount = rs.getLong(14);
            String salesDisplay = orderCount == 0 ? "데이터 없음" : won(rs.getBigDecimal(8));
            return m("name", rs.getString(2), "owner", rs.getString(3), "region", rs.getString(4),
                    "sales", salesDisplay, "monthlySales", salesDisplay,
                    "hygieneScore", rs.getInt(9), "risk", riskLevelKo(rs.getString(10)),
                    "contractStatus", rs.getDate(7).toLocalDate().isBefore(LocalDate.now().plusMonths(6)) ? "재계약 검토" : "정상",
                    "lastInspection", dateTime(rs.getTimestamp(11)), "issue", rs.getString(12),
                    "phone", rs.getString(6), "address", rs.getString(5),
                    "storeCode", storeCode, "source", imported ? "IMPORTED" : "DEMO",
                    "riskReason", rs.getString(15));
        });
        long risks = stores.stream().filter(s -> "높음".equals(s.get("risk"))).count();
        // 2026-08-12: 조치 항목 description이 전부 "본사 확인이 필요한 MySQL 기반 운영
        // 지표입니다." 한 줄로 동일해 데모에서 눈에 띄게 부자연스러웠음 — risk_assessments의
        // main_reason(실제 위험 사유 문장)이 있으면 그걸 쓰고, 없는 매장만 기존 문구로 대체.
        List<Map<String, Object>> actions = stores.stream().filter(s -> !"안전".equals(s.get("risk"))).map(s -> m(
                "store", s.get("name"), "title", s.get("issue"),
                "description", s.get("riskReason") != null ? s.get("riskReason") : "본사 확인이 필요한 MySQL 기반 운영 지표입니다.",
                "priority", "높음".equals(s.get("risk")) ? "긴급" : "주의")).toList();
        // 2026-08-08: "서울특별시" 카드가 여러 개 따로 뜨는 버그 발견·수정 — GROUP BY를
        // region 원본 전체(예: "서울특별시 종로구")로 하고 표시할 때만 시도명만 잘라 썼더니,
        // 시군구가 다르면 전부 별개 그룹으로 집계된 뒤 겉보기 라벨만 같아져 중복처럼 보였다
        // (5개 데모 매장일 땐 서울 4곳이 우연히 다 1개씩이라 눈에 덜 띄었을 뿐, 원래 있던 버그).
        // adminSales()의 지역별 집계가 이미 SUBSTRING_INDEX로 SQL 단에서 시도명만 잘라 GROUP BY
        // 하는 올바른 패턴이라 그대로 재사용.
        List<Map<String, Object>> regions = jdbc.query("""
                SELECT SUBSTRING_INDEX(region,' ',1),COUNT(*) FROM stores
                GROUP BY SUBSTRING_INDEX(region,' ',1) ORDER BY COUNT(*) DESC
                """, (rs, n) -> m("region", rs.getString(1), "stores", rs.getInt(2), "risk", 0));
        return m("adminStoreSummary", m("totalStores", stores.size(), "activeStores", stores.size(),
                        "riskStores", risks, "pendingInspections", 0, "contractExpiring", stores.stream().filter(s -> "재계약 검토".equals(s.get("contractStatus"))).count()),
                "adminStores", stores, "actionRequiredStores", actions, "regionStats", regions);
    }

    @GetMapping("/admin/risks")
    public Map<String, Object> adminRisks() {
        List<Map<String, Object>> risks = jdbc.query("""
                SELECT r.risk_assessment_id,s.name,u.name,s.region,r.risk_level,r.risk_score,
                       r.location_risk_score,r.classification_detail,r.main_reason,r.prediction,
                       r.recommended_action,r.model_version,r.assessed_at
                FROM risk_assessments r JOIN stores s ON s.store_id=r.store_id JOIN app_users u ON u.user_id=s.owner_user_id
                WHERE r.risk_assessment_id=(SELECT MAX(x.risk_assessment_id) FROM risk_assessments x WHERE x.store_id=r.store_id)
                ORDER BY r.risk_score DESC
                """, (rs, n) -> m("riskAssessmentId", rs.getLong(1), "name", rs.getString(2),
                "owner", rs.getString(3), "region", rs.getString(4),
                "riskLevel", riskLevelKo(rs.getString(5)), "riskLevelValue", rs.getInt(5),
                "riskScore", rs.getInt(6), "locationRiskScore", rs.getBigDecimal(7),
                "classificationDetail", rs.getString(8), "mainReason", rs.getString(9),
                "prediction", rs.getString(10), "action", rs.getString(11),
                "modelVersion", rs.getString(12), "assessedAt", dateTime(rs.getTimestamp(13))));
        long high = risks.stream().filter(r -> "높음".equals(r.get("riskLevel"))).count();
        long warning = risks.stream().filter(r -> "주의".equals(r.get("riskLevel"))).count();
        double average = risks.stream().mapToInt(r -> (Integer) r.get("riskScore")).average().orElse(0);
        List<Map<String, Object>> recommendations = risks.stream().filter(r -> !"안전".equals(r.get("riskLevel"))).limit(3)
                .map(r -> m("title", r.get("name") + " 조치 권장", "description", r.get("action"),
                        "priority", "높음".equals(r.get("riskLevel")) ? "긴급" : "주의")).toList();
        return m("riskSummary", m("totalStores", risks.size(), "highRiskStores", high,
                        "warningStores", warning, "stableStores", risks.size() - high - warning, "averageRiskScore", average),
                "riskStores", risks, "riskFactors", jdbc.query("""
                        SELECT f.category,f.shap_contribution,f.evidence FROM risk_factors f
                        JOIN risk_assessments r ON r.risk_assessment_id=f.risk_assessment_id
                        WHERE r.risk_assessment_id=(SELECT MAX(x.risk_assessment_id) FROM risk_assessments x WHERE x.store_id=r.store_id)
                        ORDER BY ABS(f.shap_contribution) DESC LIMIT 4
                        """, (rs, n) -> m("factor", rs.getString(1),
                        "weight", rs.getBigDecimal(2).abs().multiply(BigDecimal.valueOf(100)).intValue(),
                        "description", rs.getString(3))),
                "riskTrend", List.of(m("label", "현재", "high", high, "warning", warning)),
                "aiRecommendations", recommendations);
    }

    @GetMapping("/admin/hygiene")
    public Map<String, Object> adminHygiene() {
        // 2026-08-08: adminStores()와 같은 이유로 store_code 기반 "임포트" 표식을 같이 노출.
        // 2026-08-12: "최신 점검"을 여기만 MAX(inspection_id)로 판단하고 있었음 — 점주 화면
        // (hygiene())과 adminStores()는 둘 다 inspected_at DESC로 판단하는데, 데모 매장은
        // 점검 3건이 ID 오름차순·시간 내림차순으로 들어가 있어(가장 최근 점검이 가장 작은
        // inspection_id) 이 화면만 다른 매장을 "최신"으로 골라 점수·상태가 서로 어긋났음
        // (예: 강남역점이 여기선 81점/주의, 다른 화면에선 92점/양호로 따로 보임). 나머지
        // 두 곳과 같은 기준으로 통일.
        List<Map<String, Object>> stores = jdbc.query("""
                SELECT s.name,u.name,s.region,h.score,h.status,h.inspected_at,h.summary,h.reviewer,h.inspection_id,
                       (SELECT COUNT(*) FROM hygiene_images i WHERE i.inspection_id=h.inspection_id), s.store_code
                FROM hygiene_inspections h JOIN stores s ON s.store_id=h.store_id
                JOIN app_users u ON u.user_id=s.owner_user_id
                WHERE h.inspection_id=(
                    SELECT x.inspection_id FROM hygiene_inspections x
                    WHERE x.store_id=h.store_id ORDER BY x.inspected_at DESC LIMIT 1
                )
                ORDER BY h.score
                """, (rs, n) -> {
            String storeCode = rs.getString(11);
            boolean imported = storeCode != null && storeCode.startsWith("KG-");
            return m("name", rs.getString(1), "owner", rs.getString(2), "region", rs.getString(3),
                    "score", rs.getInt(4), "status", inspectionStatusKo(rs.getString(5)),
                    "lastCheckedAt", dateTime(rs.getTimestamp(6)), "issue", rs.getString(7),
                    "imageCount", rs.getInt(10), "category", "전체 점검", "reviewer", rs.getString(8),
                    "storeCode", storeCode, "source", imported ? "IMPORTED" : "DEMO");
        });
        long checked = stores.size();
        long danger = stores.stream().filter(s -> "긴급".equals(s.get("status"))).count();
        double average = stores.stream().mapToInt(s -> (Integer) s.get("score")).average().orElse(0);
        List<Map<String, Object>> queue = jdbc.query("""
                SELECT s.name,i.category,i.uploaded_at,i.analysis_result,h.status
                FROM hygiene_images i JOIN hygiene_inspections h ON h.inspection_id=i.inspection_id
                JOIN stores s ON s.store_id=h.store_id WHERE h.status<>'GOOD'
                ORDER BY i.uploaded_at DESC LIMIT 10
                """, (rs, n) -> m("store", rs.getString(1), "title", rs.getString(2) + " 점검 사진",
                "uploadedAt", dateTime(rs.getTimestamp(3)), "result", rs.getString(4),
                "status", "URGENT".equals(rs.getString(5)) ? "긴급 검토" : "검토 필요"));
        // 2026-08-12: "매장별 점검 현황"엔 긴급/주의로 뜨는데 "본사 조치 필요 항목"엔 안
        // 보이는 매장이 있다는 리포트 — 원인은 이 목록이 improvement_tasks(수동 등록 개선과제)
        // 테이블만 봤기 때문. hygiene_inspections.status(위 stores 목록과 동일 출처)가 GOOD이
        // 아닌 매장은 전부 포함하도록 바꾸고, 등록된 개선과제가 있으면 그 구체적 내용을,
        // 없으면 점검 자체에서 나온 요약(issue)으로 항목을 만들어 두 화면이 항상 일치하게 함.
        List<Map<String, Object>> taskRows = jdbc.query("""
                SELECT s.name,t.title,t.description,t.priority FROM improvement_tasks t
                JOIN hygiene_inspections h ON h.inspection_id=t.inspection_id JOIN stores s ON s.store_id=h.store_id
                WHERE t.status='OPEN' ORDER BY t.improvement_task_id DESC
                """, (rs, n) -> m("store", rs.getString(1), "title", rs.getString(2),
                "description", rs.getString(3), "priority", priorityKo(rs.getString(4))));
        Map<String, List<Map<String, Object>>> tasksByStore = taskRows.stream()
                .collect(java.util.stream.Collectors.groupingBy(t -> (String) t.get("store")));
        List<Map<String, Object>> actions = new java.util.ArrayList<>();
        for (Map<String, Object> s : stores) {
            if ("양호".equals(s.get("status"))) continue;
            List<Map<String, Object>> tasksForStore = tasksByStore.get(s.get("name"));
            if (tasksForStore != null && !tasksForStore.isEmpty()) {
                for (Map<String, Object> t : tasksForStore) {
                    actions.add(m("store", s.get("name"), "action", t.get("title"),
                            "description", t.get("description"), "priority", t.get("priority")));
                }
            } else {
                actions.add(m("store", s.get("name"), "action", s.get("issue"),
                        "description", "아직 개선과제가 등록되지 않았습니다 — AI 위생 점검 결과 기준입니다. 최근 점검: " + s.get("lastCheckedAt"),
                        "priority", s.get("status")));
            }
        }
        // 2026-08-08: 216개 임포트 매장의 더미 점검(전부 오늘 날짜, CURRENT_TIMESTAMP)이
        // 섞이면 "오늘" 막대가 더미 평균으로 왜곡된다 — 이 추이 차트는 실제 매장 이력을
        // 보려는 목적이라 임포트 매장은 제외.
        List<Map<String, Object>> trend = jdbc.query("""
                SELECT DATE(h.inspected_at),ROUND(AVG(h.score)) FROM hygiene_inspections h
                JOIN stores s ON s.store_id=h.store_id
                WHERE s.store_code NOT LIKE 'KG-%'
                GROUP BY DATE(h.inspected_at) ORDER BY DATE(h.inspected_at) DESC LIMIT 7
                """, (rs, n) -> m("label", rs.getDate(1).toLocalDate().format(DateTimeFormatter.ofPattern("MM-dd")),
                "score", rs.getInt(2)));
        return m("adminHygieneSummary", m("totalStores", storeCount(), "checkedStores", checked,
                        "pendingStores", Math.max(0, storeCount() - checked), "dangerStores", danger, "averageScore", average),
                "hygieneStoreList", stores, "reviewQueue", queue, "hygieneActions", actions, "hygieneTrend", trend);
    }

    @GetMapping("/admin/sales")
    public Map<String, Object> adminSales() {
        Timestamp currentPeriodStart = Timestamp.valueOf(LocalDate.now().minusDays(29).atStartOfDay());
        Timestamp previousPeriodStart = Timestamp.valueOf(LocalDate.now().minusDays(59).atStartOfDay());
        List<Map<String, Object>> ranking = jdbc.query("""
                SELECT s.name,u.name,s.region,
                       COALESCE(SUM(CASE WHEN o.ordered_at >= ? THEN o.total_amount ELSE 0 END),0),
                       SUM(CASE WHEN o.ordered_at >= ? THEN 1 ELSE 0 END),
                       COALESCE(SUM(CASE WHEN o.ordered_at >= ? AND o.ordered_at < ? THEN o.total_amount ELSE 0 END),0),
                       s.address
                FROM stores s JOIN app_users u ON u.user_id=s.owner_user_id
                LEFT JOIN customer_orders o ON o.store_id=s.store_id
                GROUP BY s.store_id,s.name,u.name,s.region,s.address ORDER BY 4 DESC
                """, (rs, n) -> {
            BigDecimal currentSales = rs.getBigDecimal(4);
            BigDecimal previousSales = rs.getBigDecimal(6);
            BigDecimal growth = percentChange(currentSales, previousSales);
            return m("rank", n + 1, "store", rs.getString(1), "owner", rs.getString(2), "region", rs.getString(3),
                    "sales", won(currentSales), "salesAmount", currentSales, "orders", rs.getLong(5) + "건",
                    "growth", signedPercent(growth), "status", growth.signum() < 0 ? "주의" : "양호",
                    "address", rs.getString(7));
        }, currentPeriodStart, currentPeriodStart, previousPeriodStart, currentPeriodStart);
        BigDecimal totalSales = ranking.stream().map(r -> money((String) r.get("sales"))).reduce(BigDecimal.ZERO, BigDecimal::add);
        long orders = jdbc.queryForObject("SELECT COUNT(*) FROM customer_orders", Long.class);
        BigDecimal average = orders == 0 ? BigDecimal.ZERO : totalSales.divide(BigDecimal.valueOf(orders), 0, RoundingMode.HALF_UP);
        List<Map<String, Object>> regions = jdbc.query("""
                SELECT SUBSTRING_INDEX(s.region,' ',1),COUNT(DISTINCT s.store_id),COALESCE(SUM(o.total_amount),0)
                FROM stores s LEFT JOIN customer_orders o ON o.store_id=s.store_id
                GROUP BY SUBSTRING_INDEX(s.region,' ',1) ORDER BY SUM(o.total_amount) DESC
                """, (rs, n) -> m("region", rs.getString(1), "stores", rs.getInt(2), "sales", won(rs.getBigDecimal(3)),
                "growth", "0%", "rate", totalSales.signum() == 0 ? 0 : rs.getBigDecimal(3).multiply(BigDecimal.valueOf(100)).divide(totalSales, 0, RoundingMode.HALF_UP).intValue()));
        List<Map<String, Object>> weak = ranking.stream().filter(r -> "주의".equals(r.get("status"))).map(r -> m(
                "store", r.get("store"), "issue", "매출 변화율 감소", "description", r.get("growth") + "의 변화율이 기록되었습니다.",
                "priority", "주의")).toList();
        return m("adminSalesSummary", m("todayTotalSales", won(totalSales), "monthlyTotalSales", won(totalSales),
                        "totalOrders", orders + "건", "averageOrderPrice", won(average), "growthRate", "0%", "targetRate", 0),
                "monthlySalesTrend", monthlySalesTrend(),
                "regionSales", regions, "storeSalesRanking", ranking, "weakStores", weak,
                "adminSalesInsights", List.of(m("title", "MySQL 통합 매출 분석", "description", ranking.size() + "개 매장의 주문을 집계했습니다.", "type", "info")));
    }

    // 작년 8월부터 당월까지(13개월) 달력 월 단위로 집계(2026-08-12, 5개월→13개월 확장 —
    // 이전엔 당월 하나만 담긴 리스트였음, 2026-08-10). 주문이 없는 달은 0으로 채운다.
    // 두 해에 걸치는 범위라 "8월"만으로는 2025-08/2026-08이 같은 라벨이 되므로 "yy.MM"로 표기.
    private List<Map<String, Object>> monthlySalesTrend() {
        LocalDate start = LocalDate.now().minusMonths(12).withDayOfMonth(1);
        List<Map<String, Object>> rows = jdbc.query("""
                SELECT DATE_FORMAT(ordered_at, '%Y-%m') ym, SUM(total_amount) total
                FROM customer_orders WHERE ordered_at >= ?
                GROUP BY DATE_FORMAT(ordered_at, '%Y-%m')
                """, (rs, n) -> m("ym", rs.getString(1), "total", rs.getBigDecimal(2)),
                Timestamp.valueOf(start.atStartOfDay()));
        Map<String, BigDecimal> byMonth = new java.util.HashMap<>();
        for (Map<String, Object> row : rows) byMonth.put((String) row.get("ym"), (BigDecimal) row.get("total"));

        List<Map<String, Object>> trend = new java.util.ArrayList<>();
        for (int i = 12; i >= 0; i--) {
            LocalDate month = LocalDate.now().minusMonths(i);
            BigDecimal total = byMonth.getOrDefault(month.format(DateTimeFormatter.ofPattern("yyyy-MM")), BigDecimal.ZERO);
            trend.add(m("month", month.format(DateTimeFormatter.ofPattern("yy.MM")), "sales", total.divide(BigDecimal.valueOf(1_000_000), 0, RoundingMode.HALF_UP)));
        }
        return trend;
    }

    private Map<String, Object> one(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Requested MySQL data was not found");
        return rows.get(0);
    }

    private Map<String, Object> aggregate(String sql, Object... args) { return one(sql, args); }
    private BigDecimal storeTarget(long storeId) { return jdbc.queryForObject("SELECT monthly_sales_target FROM stores WHERE store_id=?", BigDecimal.class, storeId); }
    private int storeCount() { return jdbc.queryForObject("SELECT COUNT(*) FROM stores", Integer.class); }
    private int targetRate(BigDecimal sales, BigDecimal target) { return target.signum() == 0 ? 0 : sales.multiply(BigDecimal.valueOf(100)).divide(target, 0, RoundingMode.HALF_UP).intValue(); }
    private static BigDecimal decimal(Object value) { return value instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(value)); }
    private static BigDecimal money(String formatted) { return new BigDecimal(formatted.replace("₩", "").replace(",", "")); }
    private static String won(BigDecimal value) { return "₩" + NumberFormat.getIntegerInstance(Locale.KOREA).format(value.setScale(0, RoundingMode.HALF_UP)); }
    private static String quantity(BigDecimal value, String unit) { return value.stripTrailingZeros().toPlainString() + unit; }
    private static String time(LocalTime value) { return value == null ? "-" : value.format(TIME); }
    private static String timeValue(Object value) {
        if (value instanceof java.sql.Time sqlTime) return time(sqlTime.toLocalTime());
        if (value instanceof LocalTime localTime) return time(localTime);
        return value == null ? "-" : value.toString();
    }
    private static String dateTime(Timestamp value) { return value == null ? "-" : value.toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")); }
    private static String statusKo(String value) { return switch (value == null ? "" : value) { case "NORMAL" -> "정상"; case "WARNING" -> "주의"; case "URGENT" -> "긴급"; case "REVIEW" -> "검토 필요"; default -> "점검 필요"; }; }
    private static String inspectionStatusKo(String value) { return switch (value) { case "GOOD" -> "양호"; case "URGENT" -> "긴급"; default -> "주의"; }; }
    private static String staffStatusKo(String value) { return switch (value) { case "CHECKED_IN" -> "출근 완료"; case "WORKING" -> "근무 중"; default -> "근무 예정"; }; }
    private static String priorityKo(String value) { return switch (value == null ? "" : value) { case "URGENT" -> "긴급"; case "WARNING" -> "주의"; default -> "확인"; }; }
    private static String riskKo(String value) { return switch (value) { case "SHORTAGE" -> "부족"; case "WARNING" -> "주의"; default -> "안전"; }; }
    private static String riskLevelKo(String value) { return switch (value) { case "4", "5", "HIGH" -> "높음"; case "3", "WARNING" -> "주의"; default -> "안전"; }; }
    private static String orderStatusKo(String value) { return switch (value) { case "DRAFT" -> "작성중"; case "SHIPPING" -> "배송중"; case "RECEIVED" -> "입고완료"; default -> value; }; }
    private static String channelKo(String value) { return switch (value) { case "IN_STORE" -> "매장 주문"; case "DELIVERY" -> "배달앱"; default -> "포장 주문"; }; }
    private static String signedPercent(BigDecimal value) { return (value.signum() >= 0 ? "+" : "") + value.stripTrailingZeros().toPlainString() + "%"; }
    private static BigDecimal percentChange(BigDecimal current, BigDecimal previous) {
        if (previous.signum() == 0) return current.signum() == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(100);
        return current.subtract(previous).multiply(BigDecimal.valueOf(100))
                .divide(previous, 1, RoundingMode.HALF_UP);
    }
    private static Map<String, Object> m(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) result.put((String) values[i], values[i + 1]);
        return result;
    }
}
