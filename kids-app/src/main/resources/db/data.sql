
CREATE TABLE apps (
                    id SERIAL PRIMARY KEY,
                    name VARCHAR(255) NOT NULL,
                    version VARCHAR(50),
                    state VARCHAR(20) NOT NULL,
                    retries INT DEFAULT 0,
                    last_error TEXT,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO apps (name, version, state) VALUES
                                          ('App1', '1.0', 'SCHEDULED'),
                                          ('App2', '1.0', 'SCHEDULED'),
                                          ('App3', '1.0', 'ERROR');

select * from apps;

---drop table apps;


---
select * from apps;
--drop table apps;
CREATE TABLE apps (
                    id SERIAL PRIMARY KEY,
                    name VARCHAR(255) NOT NULL,
                    current_version VARCHAR(50),
                    latest_version VARCHAR(50),
                    installed_version VARCHAR(50),
                    state VARCHAR(20) NOT NULL,
                    retries INT DEFAULT 0,
                    last_error TEXT,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO apps (name, current_version,latest_version,installed_version, state, retries) VALUES
                                                                                            ('App1', '1.0', '1.0','','SCHEDULED',0),
                                                                                            ('App2', '1.0','1.0','', 'SCHEDULED',0),
                                                                                            ('App3', '1.0', '1.0','', 'ERROR',0);


--drop table apps;







