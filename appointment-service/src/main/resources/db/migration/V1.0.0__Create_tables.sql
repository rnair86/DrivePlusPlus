CREATE TABLE instructor_availability (
    id UUID PRIMARY KEY,
    instructor_id UUID NOT NULL,
    day_of_week VARCHAR(10) NOT NULL CHECK (day_of_week IN ('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY')),
    start_time TIME NOT NULL,
    end_time TIME NOT NULL
);

CREATE TABLE appointments (
    id UUID PRIMARY KEY,
    student_id UUID NOT NULL,
    instructor_id UUID NOT NULL,
    vehicle_id VARCHAR(50),
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    pickup_location VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED' CHECK (status IN ('CONFIRMED','RESCHEDULED','CANCELLED','COMPLETED','NO_SHOW')),
    notes TEXT,
    reminder_sent_at TIMESTAMPTZ
);

CREATE INDEX idx_appointments_instructor_time ON appointments (instructor_id, start_at);
CREATE INDEX idx_appointments_student ON appointments (student_id);
CREATE INDEX idx_appointments_reminder ON appointments (start_at) WHERE reminder_sent_at IS NULL;
