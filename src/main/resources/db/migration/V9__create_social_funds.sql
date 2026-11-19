CREATE TABLE social_funds (
    id BINARY(16) NOT NULL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    slug VARCHAR(250) NOT NULL,
    type VARCHAR(20) NOT NULL,
    fund_coverage VARCHAR(20) NOT NULL,
    open_date DATE NOT NULL,
    close_date DATE NOT NULL,
    min_amount DECIMAL(19, 2) NOT NULL,
    max_amount DECIMAL(19, 2) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    created_by VARCHAR(36) NOT NULL,
    updated_by VARCHAR(36) NOT NULL,
    deleted_at TIMESTAMP(6) NULL,
    deleted_by VARCHAR(36) NULL,
    CONSTRAINT uk_social_funds_slug UNIQUE (slug),
    CONSTRAINT chk_social_funds_type CHECK (type IN ('NATIONAL', 'COUNTY')),
    CONSTRAINT chk_social_funds_coverage CHECK (fund_coverage IN ('OPEN', 'PLWD', 'WOMEN', 'YOUTH')),
    CONSTRAINT chk_social_funds_dates CHECK (open_date < close_date),
    CONSTRAINT chk_social_funds_amounts CHECK (min_amount >= 0 AND max_amount >= min_amount),
    INDEX idx_social_funds_name (name),
    INDEX idx_social_funds_type_dates (type, open_date, close_date)
);

CREATE TABLE social_fund_counties (
    fund_id BINARY(16) NOT NULL,
    county_id INT NOT NULL,
    PRIMARY KEY (fund_id, county_id),
    CONSTRAINT fk_social_fund_counties_fund
        FOREIGN KEY (fund_id) REFERENCES social_funds (id),
    CONSTRAINT fk_social_fund_counties_county
        FOREIGN KEY (county_id) REFERENCES counties (id),
    INDEX idx_social_fund_counties_county (county_id)
);
