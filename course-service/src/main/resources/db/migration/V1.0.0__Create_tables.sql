CREATE TABLE courses (
    id UUID PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    course_type VARCHAR(20) NOT NULL CHECK (course_type IN ('THEORY','PRACTICAL','COMBO')),
    duration_hours INT NOT NULL DEFAULT 0,
    price NUMERIC(10,2) NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE class_batches (
    id UUID PRIMARY KEY,
    course_id UUID NOT NULL REFERENCES courses(id),
    instructor_id UUID NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    capacity INT NOT NULL DEFAULT 20,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED' CHECK (status IN ('SCHEDULED','IN_PROGRESS','COMPLETED','CANCELLED'))
);

CREATE TABLE class_sessions (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL REFERENCES class_batches(id),
    session_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    location VARCHAR(200),
    status VARCHAR(20) NOT NULL DEFAULT 'PLANNED' CHECK (status IN ('PLANNED','HELD','CANCELLED'))
);

CREATE TABLE enrollments (
    id UUID PRIMARY KEY,
    student_id UUID NOT NULL,
    batch_id UUID NOT NULL REFERENCES class_batches(id),
    enrolled_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','WITHDRAWN','COMPLETED'))
);

CREATE UNIQUE INDEX uq_active_enrollment ON enrollments (student_id, batch_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_enrollments_batch ON enrollments (batch_id);
CREATE INDEX idx_class_batches_instructor ON class_batches (instructor_id);
