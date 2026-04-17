# Interview HA Fix Release Notes

## Release Order
1. Run SQL first:
   `admin/src/main/resources/sql/manual/20260417_add_interview_record_unique_index.sql`
2. Deploy backend after SQL is done.

## Database Change
- Target index:
  `uk_interview_record_user_session_del(user_id, session_id, del_flag)`
- Script is repeatable. It creates the index only when missing.

## Pre-check
- Check duplicate keys before creating the index.
- If duplicates exist, clean them first, then run the create-index step.

## Post-release Verification
- Run concurrent `finish/end/save-from-redis` requests and confirm no duplicate record is created.
- Open report page and verify:
  `interviewDirection`, `resumeScore`, `questionCount`, `suggestions`.

