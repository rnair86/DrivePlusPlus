CREATE TABLE invoices (
    id UUID PRIMARY KEY,
    student_id UUID NOT NULL,
    enrollment_id UUID,
    amount NUMERIC(10,2) NOT NULL,
    due_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','PAID','OVERDUE','CANCELLED')),
    reminder_sent_at TIMESTAMPTZ
);

CREATE TABLE payments (
    id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL REFERENCES invoices(id),
    amount NUMERIC(10,2) NOT NULL,
    paid_at TIMESTAMPTZ NOT NULL,
    method VARCHAR(20) NOT NULL
);

CREATE INDEX idx_invoices_student ON invoices (student_id);
CREATE INDEX idx_invoices_due ON invoices (due_date) WHERE status IN ('PENDING','OVERDUE');
