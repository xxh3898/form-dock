\set ON_ERROR_STOP on

DO $formdock$
BEGIN
    IF (SELECT count(*) FROM users WHERE id = 101 AND email = 'recovery-fixture@example.test') <> 1 THEN
        RAISE EXCEPTION 'Creator recovery fixture mismatch';
    END IF;

    IF (
        SELECT count(*)
        FROM surveys
        WHERE id = 201
          AND owner_id = 101
          AND slug = 'phase-5b-recovery-fixture'
          AND status = 'OPEN'
          AND deleted_at IS NULL
    ) <> 1 THEN
        RAISE EXCEPTION 'Survey recovery fixture mismatch';
    END IF;

    IF (
        SELECT count(*)
        FROM questions q
        JOIN question_options qo ON qo.question_id = q.id
        WHERE q.id = 301
          AND q.survey_id = 201
          AND q.type = 'SINGLE_CHOICE'
          AND q.position = 0
          AND qo.id IN (401, 402)
    ) <> 2 THEN
        RAISE EXCEPTION 'Question recovery fixture mismatch';
    END IF;

    IF (
        SELECT count(*)
        FROM survey_responses sr
        JOIN answers a ON a.response_id = sr.id
        JOIN answer_options ao ON ao.answer_id = a.id
        WHERE sr.id = 501
          AND sr.survey_id = 201
          AND sr.client_submission_id = '00000000-0000-0000-0000-000000000501'
          AND sr.payload_hash = repeat('a', 64)
          AND a.question_id = 301
          AND ao.option_id = 401
    ) <> 1 THEN
        RAISE EXCEPTION 'Response recovery fixture mismatch';
    END IF;
END
$formdock$;
