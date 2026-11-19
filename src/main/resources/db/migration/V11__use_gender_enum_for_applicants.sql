ALTER TABLE applicants
    DROP FOREIGN KEY fk_applicants_sex,
    CHANGE COLUMN sex_code gender VARCHAR(20) NULL,
    ADD CONSTRAINT chk_applicants_gender
        CHECK (gender IS NULL OR gender IN ('MALE', 'FEMALE', 'OTHER', 'UNDISCLOSED'));
