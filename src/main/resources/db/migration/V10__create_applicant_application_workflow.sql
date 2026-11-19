CREATE TABLE sexes (
    code VARCHAR(20) NOT NULL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);
INSERT INTO sexes (code, name) VALUES
    ('FEMALE', 'Female'), ('MALE', 'Male'), ('OTHER', 'Other'), ('UNDISCLOSED', 'Undisclosed');

CREATE TABLE marital_statuses (
    code VARCHAR(30) NOT NULL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);
INSERT INTO marital_statuses (code, name) VALUES
    ('SINGLE', 'Single'), ('MARRIED', 'Married'), ('DIVORCED', 'Divorced'),
    ('WIDOWED', 'Widowed'), ('SEPARATED', 'Separated'), ('UNKNOWN', 'Unknown');

CREATE TABLE assistance_programmes (
    code VARCHAR(40) NOT NULL PRIMARY KEY,
    name VARCHAR(120) NOT NULL UNIQUE
);
INSERT INTO assistance_programmes (code, name) VALUES
    ('OVC', 'Orphans and vulnerable children'),
    ('ELDERLY', 'Poor elderly persons'),
    ('PLWD', 'Persons with disability'),
    ('EXTREME_POVERTY', 'Persons in extreme poverty'),
    ('OTHER', 'Other');

CREATE TABLE application_statuses (
    code VARCHAR(30) NOT NULL PRIMARY KEY,
    name VARCHAR(60) NOT NULL UNIQUE
);
INSERT INTO application_statuses (code, name) VALUES
    ('DRAFT', 'Draft'),
    ('SUBMITTED', 'Submitted'),
    ('UNDER_REVIEW', 'Under review'),
    ('APPROVED', 'Approved'),
    ('REJECTED', 'Rejected'),
    ('RETURNED_FOR_CORRECTION', 'Returned for correction');

CREATE TABLE applicants (
    id BINARY(16) NOT NULL PRIMARY KEY,
    first_name VARCHAR(200) NULL,
    middle_name VARCHAR(200) NULL,
    last_name VARCHAR(200) NULL,
    sex_code VARCHAR(20) NULL,
    date_of_birth DATE NULL,
    age INT NULL,
    marital_status_code VARCHAR(30) NULL,
    identity_number VARCHAR(20) NULL,
    sub_county_id INT NULL,
    location VARCHAR(150) NULL,
    sub_location VARCHAR(150) NULL,
    village VARCHAR(150) NULL,
    postal_address VARCHAR(500) NULL,
    physical_address VARCHAR(500) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    created_by VARCHAR(36) NOT NULL,
    updated_by VARCHAR(36) NOT NULL,
    deleted_at TIMESTAMP(6) NULL,
    deleted_by VARCHAR(36) NULL,
    CONSTRAINT uk_applicants_identity_number UNIQUE (identity_number),
    CONSTRAINT chk_applicants_identity_format
        CHECK (identity_number IS NULL OR identity_number REGEXP '^[A-Za-z0-9]{1,20}$'),
    CONSTRAINT chk_applicants_age CHECK (age IS NULL OR age BETWEEN 0 AND 120),
    CONSTRAINT fk_applicants_sex FOREIGN KEY (sex_code) REFERENCES sexes (code),
    CONSTRAINT fk_applicants_marital FOREIGN KEY (marital_status_code) REFERENCES marital_statuses (code),
    CONSTRAINT fk_applicants_sub_county FOREIGN KEY (sub_county_id) REFERENCES sub_counties (id),
    INDEX idx_applicants_names (last_name, first_name),
    INDEX idx_applicants_geography (sub_county_id)
);

CREATE TABLE applicant_phones (
    applicant_id BINARY(16) NOT NULL,
    phone_number VARCHAR(16) NOT NULL,
    PRIMARY KEY (applicant_id, phone_number),
    CONSTRAINT fk_applicant_phones_applicant
        FOREIGN KEY (applicant_id) REFERENCES applicants (id) ON DELETE CASCADE
);

CREATE TABLE social_assistance_applications (
    id BINARY(16) NOT NULL PRIMARY KEY,
    applicant_id BINARY(16) NOT NULL,
    application_date DATE NULL,
    applicant_signed_date DATE NULL,
    official_name VARCHAR(200) NULL,
    official_designation VARCHAR(200) NULL,
    official_signature_reference VARCHAR(200) NULL,
    official_signed_date DATE NULL,
    other_programme VARCHAR(200) NULL,
    status_code VARCHAR(30) NOT NULL,
    decision_reason VARCHAR(1000) NULL,
    source VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    created_by VARCHAR(36) NOT NULL,
    updated_by VARCHAR(36) NOT NULL,
    deleted_at TIMESTAMP(6) NULL,
    deleted_by VARCHAR(36) NULL,
    CONSTRAINT fk_applications_applicant FOREIGN KEY (applicant_id) REFERENCES applicants (id),
    CONSTRAINT fk_applications_status FOREIGN KEY (status_code) REFERENCES application_statuses (code),
    CONSTRAINT chk_applications_source CHECK (source IN ('ASSISTED', 'SELF_SERVICE')),
    CONSTRAINT chk_applications_signature_dates
        CHECK (applicant_signed_date IS NULL OR application_date IS NULL OR applicant_signed_date >= application_date),
    INDEX idx_applications_status_date (status_code, application_date),
    INDEX idx_applications_applicant (applicant_id)
);

CREATE TABLE application_programmes (
    application_id BINARY(16) NOT NULL,
    programme_code VARCHAR(40) NOT NULL,
    PRIMARY KEY (application_id, programme_code),
    CONSTRAINT fk_application_programmes_application
        FOREIGN KEY (application_id) REFERENCES social_assistance_applications (id) ON DELETE CASCADE,
    CONSTRAINT fk_application_programmes_programme
        FOREIGN KEY (programme_code) REFERENCES assistance_programmes (code)
);

CREATE TABLE application_transitions (
    id BINARY(16) NOT NULL PRIMARY KEY,
    application_id BINARY(16) NOT NULL,
    actor_id VARCHAR(36) NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    previous_status VARCHAR(30) NULL,
    new_status VARCHAR(30) NOT NULL,
    reason VARCHAR(1000) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    created_by VARCHAR(36) NOT NULL,
    updated_by VARCHAR(36) NOT NULL,
    deleted_at TIMESTAMP(6) NULL,
    deleted_by VARCHAR(36) NULL,
    CONSTRAINT fk_application_transitions_application
        FOREIGN KEY (application_id) REFERENCES social_assistance_applications (id),
    CONSTRAINT fk_application_transitions_previous
        FOREIGN KEY (previous_status) REFERENCES application_statuses (code),
    CONSTRAINT fk_application_transitions_next
        FOREIGN KEY (new_status) REFERENCES application_statuses (code),
    INDEX idx_application_transitions_app_time (application_id, occurred_at)
);
