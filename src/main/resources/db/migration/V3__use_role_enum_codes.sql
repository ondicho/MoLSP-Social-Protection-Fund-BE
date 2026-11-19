INSERT INTO roles (name) VALUES ('APPLICANT');

ALTER TABLE users ADD COLUMN role_code VARCHAR(50) NULL;

UPDATE users u
JOIN roles r ON r.id = u.role_id
SET u.role_code = r.name;

ALTER TABLE users
    MODIFY COLUMN role_code VARCHAR(50) NOT NULL,
    ADD CONSTRAINT chk_users_role_code
        CHECK (role_code IN ('DATA_ENTRY_OFFICER', 'APPROVER', 'APPLICANT')),
    ADD CONSTRAINT fk_users_role_code FOREIGN KEY (role_code) REFERENCES roles (name);

ALTER TABLE users
    DROP FOREIGN KEY fk_users_role,
    DROP COLUMN role_id;
