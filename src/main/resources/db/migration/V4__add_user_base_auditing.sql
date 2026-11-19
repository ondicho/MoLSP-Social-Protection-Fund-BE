-- Historical actors are unknown; preserve that fact rather than attributing them to a user.
ALTER TABLE users
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN created_by VARCHAR(36) NOT NULL DEFAULT 'LEGACY_UNKNOWN',
    ADD COLUMN updated_by VARCHAR(36) NOT NULL DEFAULT 'LEGACY_UNKNOWN',
    ADD COLUMN deleted_at TIMESTAMP(6) NULL,
    ADD COLUMN deleted_by VARCHAR(36) NULL,
    ADD COLUMN first_name VARCHAR(200) NOT NULL DEFAULT '',
    ADD COLUMN middle_name VARCHAR(200) NOT NULL DEFAULT '',
    ADD COLUMN last_name VARCHAR(200) NOT NULL DEFAULT '';

ALTER TABLE users
    ALTER COLUMN created_by DROP DEFAULT,
    ALTER COLUMN updated_by DROP DEFAULT,
    ALTER COLUMN first_name DROP DEFAULT,
    ALTER COLUMN middle_name DROP DEFAULT,
    ALTER COLUMN last_name DROP DEFAULT;
