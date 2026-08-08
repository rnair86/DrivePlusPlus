CREATE TABLE user_accounts (
                               id UUID PRIMARY KEY,
                               email VARCHAR(255) NOT NULL UNIQUE,
                               password_hash VARCHAR(255) NOT NULL,
                               role VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN','INSTRUCTOR','STUDENT')),
                               status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','SUSPENDED','ARCHIVED')),
                               created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE student_profiles (
                                  user_id UUID PRIMARY KEY REFERENCES user_accounts(id),
                                  first_name VARCHAR(100) NOT NULL,
                                  last_name VARCHAR(100) NOT NULL,
                                  phone VARCHAR(30),
                                  date_of_birth DATE,
                                  guardian_contact VARCHAR(100),
                                  license_stage VARCHAR(20) NOT NULL DEFAULT 'LEARNER' CHECK (license_stage IN ('LEARNER','PROVISIONAL','FULL'))
);

CREATE TABLE instructor_profiles (
                                     user_id UUID PRIMARY KEY REFERENCES user_accounts(id),
                                     first_name VARCHAR(100) NOT NULL,
                                     last_name VARCHAR(100) NOT NULL,
                                     phone VARCHAR(30),
                                     license_cert_number VARCHAR(50),
                                     years_experience INT NOT NULL DEFAULT 0,
                                     vehicle_assigned VARCHAR(50)
);

CREATE TABLE instructor_specialties (
                                        user_id UUID NOT NULL REFERENCES instructor_profiles(user_id),
                                        specialty VARCHAR(50) NOT NULL,
                                        PRIMARY KEY (user_id, specialty)
);

CREATE INDEX idx_student_profiles_name ON student_profiles (lower(first_name), lower(last_name));