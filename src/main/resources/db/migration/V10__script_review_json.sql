ALTER TABLE ls_script
    ADD COLUMN IF NOT EXISTS review_json JSONB;
