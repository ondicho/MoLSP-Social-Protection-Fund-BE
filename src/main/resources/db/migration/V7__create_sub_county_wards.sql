CREATE TABLE wards (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    sub_county_id INT NOT NULL,
    name VARCHAR(150) NOT NULL,
    CONSTRAINT fk_ward_sub_county FOREIGN KEY (sub_county_id) REFERENCES sub_counties(id),
    CONSTRAINT uk_ward_sub_county_name UNIQUE (sub_county_id, name)
);
