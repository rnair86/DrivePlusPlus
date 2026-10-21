CREATE TABLE test_schedules (
    id UUID PRIMARY KEY,
    student_id UUID NOT NULL,
    test_type VARCHAR(20) NOT NULL CHECK (test_type IN ('MOCK_THEORY','MOCK_PRACTICAL','OFFICIAL_THEORY','OFFICIAL_ROAD')),
    scheduled_at TIMESTAMPTZ NOT NULL,
    location VARCHAR(255),
    examiner_or_instructor_id UUID,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED' CHECK (status IN ('SCHEDULED','RESCHEDULED','CANCELLED','COMPLETED')),
    result VARCHAR(10) CHECK (result IN ('PASS','FAIL','PENDING')),
    result_notes TEXT,
    reminder_sent_at TIMESTAMPTZ
);

CREATE INDEX idx_test_schedules_student ON test_schedules (student_id);
CREATE INDEX idx_test_schedules_reminder ON test_schedules (scheduled_at) WHERE reminder_sent_at IS NULL;
