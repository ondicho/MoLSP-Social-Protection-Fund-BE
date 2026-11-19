ALTER TABLE users DROP CHECK chk_users_role_code;

INSERT INTO roles (name) VALUES ('PLATFORM_ADMIN');

ALTER TABLE users
    ADD CONSTRAINT chk_users_role_code
        CHECK (role_code IN ('PLATFORM_ADMIN', 'DATA_ENTRY_OFFICER', 'APPROVER', 'APPLICANT'));
