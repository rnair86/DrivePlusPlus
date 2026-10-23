INSERT INTO templates (id, event_type, channel, subject_template, body_template) VALUES
    (gen_random_uuid(), 'STUDENT_REGISTERED', 'EMAIL', 'Welcome', 'Hi {{recipient}}, your student account is ready.'),
    (gen_random_uuid(), 'INSTRUCTOR_REGISTERED', 'EMAIL', 'Welcome', 'Hi {{recipient}}, your instructor account is ready.'),
    (gen_random_uuid(), 'APPOINTMENT_CREATED', 'EMAIL', 'Lesson booked', 'Hi {{recipient}}, a driving lesson has been booked.'),
    (gen_random_uuid(), 'APPOINTMENT_REMINDER', 'EMAIL', 'Your driving lesson is coming up', 'Hi {{recipient}}, this is a reminder of your upcoming lesson.'),
    (gen_random_uuid(), 'APPOINTMENT_CANCELLED', 'EMAIL', 'Lesson cancelled', 'Hi {{recipient}}, a driving lesson was cancelled.'),
    (gen_random_uuid(), 'TEST_SCHEDULED', 'EMAIL', 'Test scheduled', 'Hi {{recipient}}, a test has been scheduled.'),
    (gen_random_uuid(), 'TEST_REMINDER', 'EMAIL', 'Upcoming test', 'Hi {{recipient}}, your test is coming up.'),
    (gen_random_uuid(), 'TEST_RESULT_PUBLISHED', 'EMAIL', 'Test result', 'Hi {{recipient}}, a test result is available.'),
    (gen_random_uuid(), 'PAYMENT_DUE', 'EMAIL', 'Payment due', 'Hi {{recipient}}, you have a payment due soon.'),
    (gen_random_uuid(), 'PAYMENT_OVERDUE', 'EMAIL', 'Payment overdue', 'Hi {{recipient}}, a payment is overdue.'),
    (gen_random_uuid(), 'PAYMENT_RECEIVED', 'EMAIL', 'Payment received', 'Hi {{recipient}}, a payment was received.');
