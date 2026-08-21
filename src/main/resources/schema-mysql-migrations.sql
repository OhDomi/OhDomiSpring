-- Existing databases created before account lockout support do not receive
-- new columns from CREATE TABLE IF NOT EXISTS in schema.sql. These migrations
-- inspect the active schema first so they remain safe on every application start.

SET @ohdomi_missing_column = (
    SELECT COUNT(*) = 0
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'app_users'
      AND column_name = 'failed_login_count'
);
SET @ohdomi_ddl = IF(
    @ohdomi_missing_column,
    'ALTER TABLE app_users ADD COLUMN failed_login_count INT NOT NULL DEFAULT 0',
    'SELECT 1'
);
PREPARE ohdomi_migration FROM @ohdomi_ddl;
EXECUTE ohdomi_migration;
DEALLOCATE PREPARE ohdomi_migration;

SET @ohdomi_missing_column = (
    SELECT COUNT(*) = 0
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'app_users'
      AND column_name = 'locked_until'
);
SET @ohdomi_ddl = IF(
    @ohdomi_missing_column,
    'ALTER TABLE app_users ADD COLUMN locked_until TIMESTAMP NULL DEFAULT NULL',
    'SELECT 1'
);
PREPARE ohdomi_migration FROM @ohdomi_ddl;
EXECUTE ohdomi_migration;
DEALLOCATE PREPARE ohdomi_migration;
