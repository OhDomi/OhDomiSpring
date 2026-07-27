INSERT IGNORE INTO app_users (user_id, login_id, password_hash, name, role, phone, active) VALUES
  (1, 'admin', 'pbkdf2_sha256$210000$3cBlLmlAsZY1+yU7/S9MkQ$CzqofniinFBdliHUkEoYwbphhFJx5S1nxYp5CR/lYR4', '본사 운영팀', 'ADMIN', '02-1234-5678', TRUE),
  (2, 'demo', 'pbkdf2_sha256$210000$3cBlLmlAsZY1+yU7/S9MkQ$CzqofniinFBdliHUkEoYwbphhFJx5S1nxYp5CR/lYR4', '김도윤', 'OWNER', '010-4820-1593', TRUE),
  (3, 'seongsu', 'pbkdf2_sha256$210000$3cBlLmlAsZY1+yU7/S9MkQ$CzqofniinFBdliHUkEoYwbphhFJx5S1nxYp5CR/lYR4', '이서준', 'OWNER', '010-7412-8850', TRUE),
  (4, 'jamsil', 'pbkdf2_sha256$210000$3cBlLmlAsZY1+yU7/S9MkQ$CzqofniinFBdliHUkEoYwbphhFJx5S1nxYp5CR/lYR4', '박지우', 'OWNER', '010-3387-2140', TRUE),
  (5, 'yeouido', 'pbkdf2_sha256$210000$3cBlLmlAsZY1+yU7/S9MkQ$CzqofniinFBdliHUkEoYwbphhFJx5S1nxYp5CR/lYR4', '최하늘', 'OWNER', '010-9061-3724', TRUE),
  (6, 'busan', 'pbkdf2_sha256$210000$3cBlLmlAsZY1+yU7/S9MkQ$CzqofniinFBdliHUkEoYwbphhFJx5S1nxYp5CR/lYR4', '정민호', 'OWNER', '010-6112-4409', TRUE);

UPDATE app_users
SET password_hash = 'pbkdf2_sha256$210000$3cBlLmlAsZY1+yU7/S9MkQ$CzqofniinFBdliHUkEoYwbphhFJx5S1nxYp5CR/lYR4'
WHERE password_hash = '{noop}1234';

INSERT IGNORE INTO stores (store_id, owner_user_id, store_code, name, region, address, phone, open_time, close_time, operation_status, opened_on, contract_ends_on, monthly_sales_target) VALUES
  (1, 2, 'ST-GANGNAM', '강남역점', '서울 강남구', '서울 강남구 테헤란로 18길 12', '010-4820-1593', '09:00:00', '22:00:00', 'OPEN', '2023-04-18', '2027-04-17', 45000000),
  (2, 3, 'ST-SEONGSU', '성수점', '서울 성동구', '서울 성동구 연무장길 42', '010-7412-8850', '09:00:00', '22:00:00', 'OPEN', '2022-11-02', '2027-11-01', 47000000),
  (3, 4, 'ST-JAMSIL', '잠실점', '서울 송파구', '서울 송파구 올림픽로 35길 10', '010-3387-2140', '09:00:00', '22:00:00', 'OPEN', '2024-01-12', '2027-01-11', 46000000),
  (4, 5, 'ST-YEOUIDO', '여의도점', '서울 영등포구', '서울 영등포구 국제금융로 8길 16', '010-9061-3724', '09:00:00', '22:00:00', 'OPEN', '2023-08-25', '2026-08-24', 43000000),
  (5, 6, 'ST-BUSAN', '부산서면점', '부산 부산진구', '부산 부산진구 중앙대로 692', '010-6112-4409', '09:00:00', '22:00:00', 'OPEN', '2023-02-10', '2027-02-09', 40000000);

INSERT IGNORE INTO staff_shifts (staff_shift_id, store_id, staff_name, staff_role, work_date, starts_at, ends_at, status) VALUES
  (1, 1, '김민수', '주방', CURRENT_DATE, '09:00:00', '15:00:00', 'CHECKED_IN'),
  (2, 1, '이서연', '홀', CURRENT_DATE, '12:00:00', '18:00:00', 'WORKING'),
  (3, 1, '박지훈', '마감', CURRENT_DATE, '18:00:00', '22:00:00', 'SCHEDULED');

INSERT IGNORE INTO facilities (facility_id, store_id, name, active) VALUES
  (1, 1, '냉장고', TRUE), (2, 1, 'POS 기기', TRUE), (3, 1, '조리대', TRUE), (4, 1, '에어컨', TRUE);
INSERT IGNORE INTO facility_checks (facility_check_id, facility_id, status, memo, checked_at) VALUES
  (1, 1, 'WARNING', '온도 변동이 감지되어 재확인이 필요합니다.', CURRENT_TIMESTAMP),
  (2, 2, 'NORMAL', '결제 및 주문 연동 정상', CURRENT_TIMESTAMP),
  (3, 3, 'CHECK_REQUIRED', '위생 점검 사진 재업로드가 필요합니다.', CURRENT_TIMESTAMP),
  (4, 4, 'NORMAL', '이상 없음', CURRENT_TIMESTAMP);

INSERT IGNORE INTO menu_items (menu_item_id, name, category, price, active) VALUES
  (1, '연어 포케', '시그니처', 11000, TRUE), (2, '참치 마요 덮밥', '덮밥', 9000, TRUE),
  (3, '메밀 소바', '면류', 9000, TRUE), (4, '닭가슴살 샐러드', '샐러드', 11000, TRUE);

INSERT IGNORE INTO customer_orders (customer_order_id, store_id, channel, ordered_at, total_amount, status) VALUES
  (1, 1, 'IN_STORE', CURRENT_TIMESTAMP, 704000, 'COMPLETED'),
  (2, 1, 'DELIVERY', CURRENT_TIMESTAMP, 459000, 'COMPLETED'),
  (3, 1, 'TAKEOUT', CURRENT_TIMESTAMP, 417000, 'COMPLETED'),
  (4, 2, 'IN_STORE', CURRENT_TIMESTAMP, 5140000, 'COMPLETED'),
  (5, 3, 'DELIVERY', CURRENT_TIMESTAMP, 3760000, 'COMPLETED'),
  (6, 4, 'IN_STORE', CURRENT_TIMESTAMP, 4250000, 'COMPLETED'),
  (7, 5, 'DELIVERY', CURRENT_TIMESTAMP, 2980000, 'COMPLETED');
INSERT IGNORE INTO customer_order_items (customer_order_item_id, customer_order_id, menu_item_id, quantity, unit_price) VALUES
  (1, 1, 1, 64, 11000), (2, 2, 2, 51, 9000), (3, 3, 3, 37, 9000),
  (4, 3, 4, 8, 10500);

INSERT IGNORE INTO inventory_items (inventory_item_id, store_id, item_name, category, unit, current_quantity, reorder_level, unit_price) VALUES
  (1, 1, '연어', '수산', 'kg', 22, 38, 18000), (2, 1, '날치알', '토핑', '개', 45, 55, 3500),
  (3, 1, '포장 용기', '소모품', '개', 120, 190, 750), (4, 1, '샐러드 채소', '신선식품', 'kg', 8, 13, 10600),
  (5, 1, '메밀면', '면류', 'kg', 8.5, 6.2, 7800);
INSERT IGNORE INTO order_recommendations (recommendation_id, inventory_item_id, recommendation_date, expected_usage, recommended_quantity, risk_level, reason) VALUES
  (1, 1, CURRENT_DATE, 38, 16, 'SHORTAGE', '점심 피크 시간대 연어 포케 주문 증가 예상'),
  (2, 2, CURRENT_DATE, 55, 10, 'WARNING', '인기 메뉴 주문 증가로 추가 확보 권장'),
  (3, 3, CURRENT_DATE, 190, 70, 'SHORTAGE', '배달앱 주문 비중 상승'),
  (4, 4, CURRENT_DATE, 13, 5, 'WARNING', '저녁 시간대 샐러드 메뉴 판매 증가 예상'),
  (5, 5, CURRENT_DATE, 6.2, 0, 'SAFE', '현재 재고로 예상 수요 대응 가능');
INSERT IGNORE INTO purchase_orders (purchase_order_id, store_id, order_number, status, ordered_at, total_amount, created_at) VALUES
  (1, 1, 'OD-20260721-004', 'DRAFT', NULL, 428500, '2026-07-21 09:00:00'),
  (2, 1, 'OD-20260720-018', 'RECEIVED', '2026-07-20 09:00:00', 612000, '2026-07-20 08:30:00'),
  (3, 1, 'OD-20260719-011', 'SHIPPING', '2026-07-19 10:00:00', 184000, '2026-07-19 09:20:00');
INSERT IGNORE INTO purchase_order_items (purchase_order_item_id, purchase_order_id, inventory_item_id, quantity, unit_price) VALUES
  (1, 1, 1, 16, 18000), (2, 1, 2, 10, 3500), (3, 1, 3, 70, 750), (4, 1, 4, 5, 10600);

INSERT IGNORE INTO hygiene_inspections (inspection_id, store_id, score, status, reviewer, summary, inspected_at) VALUES
  (1, 1, 92, 'GOOD', 'AI 자동 분석', '출입구 주변 정리 상태 확인 필요', '2026-07-21 09:40:00'),
  (2, 1, 88, 'GOOD', 'AI 자동 분석', '전반적으로 양호', '2026-07-20 09:20:00'),
  (3, 1, 81, 'WARNING', '운영관리팀', '바닥 및 홀 정리 필요', '2026-07-19 18:20:00'),
  (4, 2, 94, 'GOOD', 'AI 자동 분석', '특이사항 없음', '2026-07-21 08:55:00'),
  (5, 3, 86, 'WARNING', '운영관리팀', '출입구 주변 정리 필요', '2026-07-20 18:20:00'),
  (6, 4, 91, 'GOOD', 'AI 자동 분석', '특이사항 없음', '2026-07-21 10:15:00'),
  (7, 5, 69, 'URGENT', 'AI 자동 분석', '냉장 보관 상태 및 바닥 청결 재점검 필요', '2026-07-20 16:10:00');
INSERT IGNORE INTO hygiene_check_results (check_result_id, inspection_id, item_name, score, status, memo) VALUES
  (1, 1, '조리대 청결', 96, 'NORMAL', '오염 요소가 발견되지 않았습니다.'),
  (2, 1, '냉장고 보관 상태', 94, 'NORMAL', '식자재 분리 보관 상태가 양호합니다.'),
  (3, 1, '바닥 및 홀 정리', 78, 'WARNING', '출입구 주변 정리가 필요합니다.'),
  (4, 1, '직원 위생 착용', 91, 'NORMAL', '위생모 및 장갑 착용 상태가 양호합니다.');
INSERT IGNORE INTO hygiene_images (image_id, inspection_id, image_url, category, analysis_result, uploaded_at) VALUES
  (1, 1, '/uploads/hygiene/1-counter.jpg', 'COUNTER', '정상', '2026-07-21 09:38:00'),
  (2, 1, '/uploads/hygiene/1-fridge.jpg', 'FRIDGE', '정상', '2026-07-21 09:38:30'),
  (3, 1, '/uploads/hygiene/1-entrance.jpg', 'ENTRANCE', '정리 필요', '2026-07-21 09:39:00');
INSERT IGNORE INTO improvement_tasks (improvement_task_id, inspection_id, title, description, priority, status) VALUES
  (1, 1, '출입구 주변 박스 정리', '홀 입구 근처 적재물로 이동 동선에 방해될 수 있습니다.', 'WARNING', 'OPEN'),
  (2, 1, '바닥 물기 재확인', '점심 피크 전 바닥 미끄럼 위험 여부를 확인하세요.', 'CHECK', 'OPEN');

INSERT IGNORE INTO risk_assessments (risk_assessment_id, store_id, risk_score, risk_level, sales_change_rate, hygiene_score, delayed_order_count, complaint_count, main_reason, prediction, recommended_action, assessed_at) VALUES
  (1, 5, 87, 'HIGH', -7.6, 69, 3, 12, '매출 감소와 위생 점수 하락이 동시에 발생했습니다.', '향후 2주 내 운영 리스크가 높아질 가능성이 큽니다.', '본사 현장 점검 및 운영 상담을 권장합니다.', CURRENT_TIMESTAMP),
  (2, 1, 78, 'HIGH', 8.4, 72, 1, 8, '매출은 양호하지만 위생 점검 이슈가 반복되고 있습니다.', '위생 관리 미흡이 브랜드 신뢰도 리스크로 이어질 수 있습니다.', '조리대 재점검 요청과 점주 안내가 필요합니다.', CURRENT_TIMESTAMP),
  (3, 3, 62, 'WARNING', -2.8, 86, 2, 5, '월 매출 목표 달성률이 낮고 발주 지연이 발생했습니다.', '재고 부족으로 판매 기회 손실 가능성이 있습니다.', '발주 패턴 점검과 매출 개선 가이드를 권장합니다.', CURRENT_TIMESTAMP),
  (4, 2, 18, 'SAFE', 14.2, 94, 0, 1, '매출, 위생, 발주 지표가 모두 안정적입니다.', '단기 운영 리스크가 낮은 상태입니다.', '우수 매장 사례로 운영 노하우를 공유할 수 있습니다.', CURRENT_TIMESTAMP);

INSERT IGNORE INTO board_posts (post_id, author_user_id, store_id, board_type, category, title, content, status, is_pinned, is_urgent, view_count, created_at, updated_at) VALUES
  (1, 1, NULL, 'NOTICE', '메뉴', '7월 신메뉴 출시 안내', '7월 20일부터 여름 한정 메뉴가 추가됩니다. 발주 품목을 확인해 주세요.', 'PUBLISHED', TRUE, FALSE, 48, '2026-07-10 09:00:00', '2026-07-10 09:00:00'),
  (2, 1, NULL, 'NOTICE', '설비', '설비 점검 주기 변경 공지', '냉장고, POS 기기, 조리대 점검 항목을 매주 확인해 주세요.', 'PUBLISHED', FALSE, FALSE, 19, '2026-07-08 14:30:00', '2026-07-08 14:30:00'),
  (3, 1, NULL, 'NOTICE', '위생', '위생 점검 사진 업로드 기준 안내', '조리대, 냉장고, 홀 출입구 사진을 정면에서 촬영해 주세요.', 'PUBLISHED', FALSE, FALSE, 31, '2026-07-06 10:00:00', '2026-07-06 10:00:00'),
  (4, 2, 1, 'INQUIRY', '발주', '연어 발주 추천 수량이 평소보다 높게 나옵니다', '최근 판매량 기준이 반영된 건지 확인 부탁드립니다.', 'ANSWERED', FALSE, FALSE, 12, '2026-07-21 11:00:00', '2026-07-21 11:00:00'),
  (5, 4, 3, 'INQUIRY', '위생', '위생 점검 재촬영 요청 기준이 궁금합니다', '어떤 기준으로 재촬영이 필요한지 확인 부탁드립니다.', 'PENDING', FALSE, FALSE, 9, '2026-07-20 15:00:00', '2026-07-20 15:00:00'),
  (6, 6, 5, 'INQUIRY', '매출', 'POS 매출 데이터 반영 시간이 지연됩니다', '오늘 오전 POS 매출 데이터가 늦게 반영되고 있습니다.', 'PENDING', FALSE, TRUE, 17, '2026-07-20 10:00:00', '2026-07-20 10:00:00');
INSERT IGNORE INTO board_answers (answer_id, post_id, author_user_id, content, created_at, updated_at) VALUES
  (1, 4, 1, 'AI 발주 추천 수량은 최근 판매량과 내일 예상 주문 수를 기준으로 산정됩니다.', '2026-07-21 13:00:00', '2026-07-21 13:00:00');

INSERT IGNORE INTO notifications (notification_id, store_id, recipient_user_id, level, title, content, is_read, created_at) VALUES
  (1, 1, 1, 'URGENT', '강남역점 위생 점검 필요', 'AI 사진 분석에서 조리대 오염 가능성이 감지되었습니다.', FALSE, CURRENT_TIMESTAMP),
  (2, 1, 2, 'WARNING', '연어 재고 부족 예상', '예상 사용량 대비 현재 재고가 부족합니다.', FALSE, CURRENT_TIMESTAMP),
  (3, NULL, 1, 'INFO', '주간 매출 리포트 도착', '전주 대비 전체 매출이 증가했습니다.', FALSE, CURRENT_TIMESTAMP);
