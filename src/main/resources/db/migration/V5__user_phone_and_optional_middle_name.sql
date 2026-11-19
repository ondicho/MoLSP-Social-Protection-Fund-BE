-- Legacy records retain NULL until a real phone is supplied; JPA requires it on every write.
ALTER TABLE users
    ADD COLUMN phone_number VARCHAR(16) NULL,
    MODIFY COLUMN middle_name VARCHAR(200) NULL;

UPDATE users SET middle_name = NULL WHERE TRIM(middle_name) = '';

ALTER TABLE users ADD CONSTRAINT chk_users_phone_e164
    CHECK (phone_number IS NULL OR REGEXP_LIKE(phone_number, '^[+][1-9][0-9]{1,14}$'));
