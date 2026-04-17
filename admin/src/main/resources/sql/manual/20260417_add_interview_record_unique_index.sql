-- Manual migration (run before deploying the HA fix release).
-- Target: ensure unique index exists for interview_record(user_id, session_id, del_flag).

-- 1) Optional pre-check: identify duplicate active rows that would block unique index creation.
--    Resolve duplicates first if any rows are returned.
SELECT user_id, session_id, del_flag, COUNT(*) AS duplicate_count
FROM interview_record
GROUP BY user_id, session_id, del_flag
HAVING COUNT(*) > 1;

-- 2) Create the unique index only when it does not already exist.
SET @schema_name = DATABASE();
SET @index_exists = (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'interview_record'
      AND index_name = 'uk_interview_record_user_session_del'
);

SET @ddl = IF(
    @index_exists = 0,
    'CREATE UNIQUE INDEX uk_interview_record_user_session_del ON interview_record (user_id, session_id, del_flag)',
    'SELECT ''uk_interview_record_user_session_del already exists'' AS message'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

