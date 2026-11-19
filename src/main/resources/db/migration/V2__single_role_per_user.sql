-- Refuse ambiguous assignments before changing the users table.
ALTER TABLE user_roles
    ADD CONSTRAINT uk_user_roles_single_role UNIQUE (user_id);

ALTER TABLE users ADD COLUMN role_id INT NULL;

UPDATE users u
SET role_id = (SELECT ur.role_id FROM user_roles ur WHERE ur.user_id = u.id);

-- Existing users without a role must be assigned one before this migration.
ALTER TABLE users
    MODIFY COLUMN role_id INT NOT NULL,
    ADD CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles (id);

DROP TABLE user_roles;
