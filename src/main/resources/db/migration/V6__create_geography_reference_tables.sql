CREATE TABLE counties (
    id INT NOT NULL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    population_2009 INT NOT NULL,
    population_2019 INT NOT NULL,
    CONSTRAINT uk_counties_name UNIQUE (name),
    CONSTRAINT chk_county_populations CHECK (population_2009 >= 0 AND population_2019 >= 0)
);

CREATE TABLE sub_counties (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    county_id INT NOT NULL,
    name VARCHAR(150) NOT NULL,
    population_2009 INT NOT NULL,
    population_2019 INT NOT NULL,
    CONSTRAINT fk_sub_county_county FOREIGN KEY (county_id) REFERENCES counties(id),
    CONSTRAINT uk_sub_county_county_name UNIQUE (county_id, name),
    CONSTRAINT chk_sub_county_populations CHECK (population_2009 >= 0 AND population_2019 >= 0)
);
