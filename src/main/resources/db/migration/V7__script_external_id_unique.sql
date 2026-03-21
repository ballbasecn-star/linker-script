UPDATE ls_script
SET external_id = NULL
WHERE external_id IS NOT NULL
  AND btrim(external_id) = '';

CREATE UNIQUE INDEX IF NOT EXISTS uq_ls_script_source_external_id
    ON ls_script (source_platform, external_id)
    WHERE external_id IS NOT NULL;
