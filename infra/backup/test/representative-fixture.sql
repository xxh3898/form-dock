\set ON_ERROR_STOP on

INSERT INTO users (
    id,
    email,
    password_hash,
    display_name,
    role,
    created_at,
    updated_at
) VALUES (
    101,
    'recovery-fixture@example.test',
    '{bcrypt}test-only-non-authenticating-hash',
    '복구 검증 Creator',
    'ADMIN',
    '2026-08-26T00:00:00Z',
    '2026-08-26T00:00:00Z'
);

INSERT INTO surveys (
    id,
    owner_id,
    title,
    description,
    slug,
    privacy_notice,
    status,
    opened_at,
    closed_at,
    deleted_at,
    created_at,
    updated_at
) VALUES (
    201,
    101,
    'Phase 5-B 복구 검증 설문',
    'Disposable recovery fixture',
    'phase-5b-recovery-fixture',
    NULL,
    'OPEN',
    '2026-08-26T00:01:00Z',
    NULL,
    NULL,
    '2026-08-26T00:00:00Z',
    '2026-08-26T00:01:00Z'
);

INSERT INTO questions (
    id,
    survey_id,
    type,
    title,
    description,
    required,
    position,
    scale_min,
    scale_max,
    scale_min_label,
    scale_max_label,
    number_min,
    number_max,
    created_at,
    updated_at
) VALUES (
    301,
    201,
    'SINGLE_CHOICE',
    '복구 후 선택값은 무엇인가요?',
    NULL,
    TRUE,
    0,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    '2026-08-26T00:00:00Z',
    '2026-08-26T00:00:00Z'
);

INSERT INTO question_options (id, question_id, label, position) VALUES
    (401, 301, '보존됨', 0),
    (402, 301, '손실됨', 1);

INSERT INTO survey_responses (
    id,
    survey_id,
    client_submission_id,
    payload_hash,
    submitted_at
) VALUES (
    501,
    201,
    '00000000-0000-0000-0000-000000000501',
    repeat('a', 64),
    '2026-08-26T00:02:00.123456Z'
);

INSERT INTO answers (
    id,
    response_id,
    question_id,
    text_value,
    numeric_value,
    created_at
) VALUES (
    601,
    501,
    301,
    NULL,
    NULL,
    '2026-08-26T00:02:00.123456Z'
);

INSERT INTO answer_options (answer_id, option_id) VALUES (601, 401);
