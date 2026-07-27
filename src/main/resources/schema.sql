CREATE TABLE IF NOT EXISTS app_users (
    user_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    login_id VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN', 'OWNER')),
    phone VARCHAR(30),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS stores (
    store_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    store_code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    region VARCHAR(100) NOT NULL,
    address VARCHAR(255) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    open_time TIME NOT NULL,
    close_time TIME NOT NULL,
    operation_status VARCHAR(30) NOT NULL,
    opened_on DATE,
    contract_ends_on DATE,
    monthly_sales_target DECIMAL(15, 2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_store_owner FOREIGN KEY (owner_user_id) REFERENCES app_users(user_id)
);

CREATE TABLE IF NOT EXISTS staff_shifts (
    staff_shift_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id BIGINT NOT NULL,
    staff_name VARCHAR(100) NOT NULL,
    staff_role VARCHAR(50) NOT NULL,
    work_date DATE NOT NULL,
    starts_at TIME NOT NULL,
    ends_at TIME NOT NULL,
    status VARCHAR(30) NOT NULL,
    CONSTRAINT fk_shift_store FOREIGN KEY (store_id) REFERENCES stores(store_id)
);

CREATE TABLE IF NOT EXISTS facilities (
    facility_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_facility_store_name UNIQUE (store_id, name),
    CONSTRAINT fk_facility_store FOREIGN KEY (store_id) REFERENCES stores(store_id)
);

CREATE TABLE IF NOT EXISTS facility_checks (
    facility_check_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    facility_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    memo VARCHAR(500),
    checked_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_facility_check FOREIGN KEY (facility_id) REFERENCES facilities(facility_id)
);

CREATE TABLE IF NOT EXISTS menu_items (
    menu_item_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    category VARCHAR(50) NOT NULL,
    price DECIMAL(12, 2) NOT NULL CHECK (price >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS customer_orders (
    customer_order_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id BIGINT NOT NULL,
    channel VARCHAR(30) NOT NULL CHECK (channel IN ('IN_STORE', 'DELIVERY', 'TAKEOUT')),
    ordered_at TIMESTAMP NOT NULL,
    total_amount DECIMAL(15, 2) NOT NULL CHECK (total_amount >= 0),
    status VARCHAR(30) NOT NULL,
    CONSTRAINT fk_customer_order_store FOREIGN KEY (store_id) REFERENCES stores(store_id),
    INDEX idx_customer_orders_store_date (store_id, ordered_at)
);

CREATE TABLE IF NOT EXISTS customer_order_items (
    customer_order_item_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_order_id BIGINT NOT NULL,
    menu_item_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price DECIMAL(12, 2) NOT NULL CHECK (unit_price >= 0),
    CONSTRAINT fk_order_item_order FOREIGN KEY (customer_order_id) REFERENCES customer_orders(customer_order_id),
    CONSTRAINT fk_order_item_menu FOREIGN KEY (menu_item_id) REFERENCES menu_items(menu_item_id)
);

CREATE TABLE IF NOT EXISTS inventory_items (
    inventory_item_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id BIGINT NOT NULL,
    item_name VARCHAR(100) NOT NULL,
    category VARCHAR(50) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    current_quantity DECIMAL(12, 3) NOT NULL DEFAULT 0,
    reorder_level DECIMAL(12, 3) NOT NULL DEFAULT 0,
    unit_price DECIMAL(12, 2) NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_inventory_store_item UNIQUE (store_id, item_name),
    CONSTRAINT fk_inventory_store FOREIGN KEY (store_id) REFERENCES stores(store_id)
);

CREATE TABLE IF NOT EXISTS purchase_orders (
    purchase_order_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id BIGINT NOT NULL,
    order_number VARCHAR(40) NOT NULL UNIQUE,
    status VARCHAR(30) NOT NULL CHECK (status IN ('DRAFT', 'ORDERED', 'SHIPPING', 'RECEIVED', 'CANCELLED')),
    ordered_at TIMESTAMP,
    expected_at TIMESTAMP,
    total_amount DECIMAL(15, 2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_purchase_order_store FOREIGN KEY (store_id) REFERENCES stores(store_id),
    INDEX idx_purchase_orders_store_date (store_id, created_at)
);

CREATE TABLE IF NOT EXISTS purchase_order_items (
    purchase_order_item_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    purchase_order_id BIGINT NOT NULL,
    inventory_item_id BIGINT NOT NULL,
    quantity DECIMAL(12, 3) NOT NULL CHECK (quantity > 0),
    unit_price DECIMAL(12, 2) NOT NULL CHECK (unit_price >= 0),
    CONSTRAINT fk_purchase_item_order FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(purchase_order_id),
    CONSTRAINT fk_purchase_item_inventory FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(inventory_item_id)
);

CREATE TABLE IF NOT EXISTS order_recommendations (
    recommendation_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    inventory_item_id BIGINT NOT NULL,
    recommendation_date DATE NOT NULL,
    expected_usage DECIMAL(12, 3) NOT NULL,
    recommended_quantity DECIMAL(12, 3) NOT NULL,
    risk_level VARCHAR(20) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_recommendation_item_date UNIQUE (inventory_item_id, recommendation_date),
    CONSTRAINT fk_recommendation_inventory FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(inventory_item_id)
);

CREATE TABLE IF NOT EXISTS hygiene_inspections (
    inspection_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id BIGINT NOT NULL,
    score INTEGER NOT NULL CHECK (score BETWEEN 0 AND 100),
    status VARCHAR(30) NOT NULL,
    reviewer VARCHAR(100) NOT NULL,
    summary VARCHAR(1000),
    inspected_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_inspection_store FOREIGN KEY (store_id) REFERENCES stores(store_id),
    INDEX idx_hygiene_store_date (store_id, inspected_at)
);

CREATE TABLE IF NOT EXISTS hygiene_check_results (
    check_result_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    inspection_id BIGINT NOT NULL,
    item_name VARCHAR(100) NOT NULL,
    score INTEGER NOT NULL CHECK (score BETWEEN 0 AND 100),
    status VARCHAR(30) NOT NULL,
    memo VARCHAR(500),
    CONSTRAINT fk_check_result_inspection FOREIGN KEY (inspection_id) REFERENCES hygiene_inspections(inspection_id)
);

CREATE TABLE IF NOT EXISTS hygiene_images (
    image_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    inspection_id BIGINT NOT NULL,
    image_url VARCHAR(1000) NOT NULL,
    category VARCHAR(50) NOT NULL,
    analysis_result VARCHAR(1000),
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_hygiene_image_inspection FOREIGN KEY (inspection_id) REFERENCES hygiene_inspections(inspection_id)
);

CREATE TABLE IF NOT EXISTS improvement_tasks (
    improvement_task_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    inspection_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    priority VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    due_at TIMESTAMP,
    completed_at TIMESTAMP,
    CONSTRAINT fk_improvement_inspection FOREIGN KEY (inspection_id) REFERENCES hygiene_inspections(inspection_id)
);

CREATE TABLE IF NOT EXISTS risk_assessments (
    risk_assessment_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id BIGINT NOT NULL,
    risk_score DECIMAL(5, 2) NOT NULL CHECK (risk_score BETWEEN 0 AND 100),
    risk_level VARCHAR(20) NOT NULL,
    sales_change_rate DECIMAL(7, 2) NOT NULL,
    hygiene_score INTEGER NOT NULL,
    delayed_order_count INTEGER NOT NULL DEFAULT 0,
    complaint_count INTEGER NOT NULL DEFAULT 0,
    main_reason VARCHAR(1000) NOT NULL,
    prediction VARCHAR(1000) NOT NULL,
    recommended_action VARCHAR(1000) NOT NULL,
    assessed_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_risk_store FOREIGN KEY (store_id) REFERENCES stores(store_id),
    INDEX idx_risk_store_date (store_id, assessed_at)
);

CREATE TABLE IF NOT EXISTS board_posts (
    post_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    author_user_id BIGINT NOT NULL,
    store_id BIGINT,
    board_type VARCHAR(20) NOT NULL CHECK (board_type IN ('NOTICE', 'INQUIRY')),
    category VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(30) NOT NULL,
    is_pinned BOOLEAN NOT NULL DEFAULT FALSE,
    is_urgent BOOLEAN NOT NULL DEFAULT FALSE,
    view_count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_post_author FOREIGN KEY (author_user_id) REFERENCES app_users(user_id),
    CONSTRAINT fk_post_store FOREIGN KEY (store_id) REFERENCES stores(store_id),
    INDEX idx_board_type_date (board_type, created_at)
);

CREATE TABLE IF NOT EXISTS board_answers (
    answer_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    author_user_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_answer_post UNIQUE (post_id),
    CONSTRAINT fk_answer_post FOREIGN KEY (post_id) REFERENCES board_posts(post_id),
    CONSTRAINT fk_answer_author FOREIGN KEY (author_user_id) REFERENCES app_users(user_id)
);

CREATE TABLE IF NOT EXISTS notifications (
    notification_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id BIGINT,
    recipient_user_id BIGINT,
    level VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content VARCHAR(1000) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_store FOREIGN KEY (store_id) REFERENCES stores(store_id),
    CONSTRAINT fk_notification_user FOREIGN KEY (recipient_user_id) REFERENCES app_users(user_id)
);
