ALTER TABLE application_programmes
    DROP FOREIGN KEY fk_application_programmes_programme,
    MODIFY COLUMN programme_code VARCHAR(250) NOT NULL,
    ADD CONSTRAINT fk_application_programmes_fund
        FOREIGN KEY (programme_code) REFERENCES social_funds (slug) ON UPDATE CASCADE;
