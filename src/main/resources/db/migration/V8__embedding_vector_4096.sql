DO $$
DECLARE
    pgvector_ready boolean := true;
    embedding_udt text;
BEGIN
    BEGIN
        CREATE EXTENSION IF NOT EXISTS vector;
    EXCEPTION
        WHEN OTHERS THEN
            pgvector_ready := false;
            RAISE WARNING '[LinkScript] pgvector extension is NOT available – keeping existing embedding storage. Error: %',
                SQLERRM;
    END;

    IF NOT pgvector_ready THEN
        RETURN;
    END IF;

    SELECT c.udt_name
    INTO embedding_udt
    FROM information_schema.columns c
    WHERE c.table_schema = current_schema()
      AND c.table_name = 'ls_logic_fragment'
      AND c.column_name = 'embedding';

    IF embedding_udt IS NULL THEN
        RAISE WARNING '[LinkScript] embedding column not found on ls_logic_fragment, skipping resize migration';
        RETURN;
    END IF;

    EXECUTE 'DROP INDEX IF EXISTS idx_ls_logic_fragment_embedding';

    IF embedding_udt <> 'text' THEN
        BEGIN
            EXECUTE $sql$
                ALTER TABLE ls_logic_fragment
                    ALTER COLUMN embedding TYPE TEXT
                    USING CASE
                            WHEN embedding IS NULL THEN NULL
                            ELSE embedding::text
                          END
            $sql$;
        EXCEPTION
            WHEN OTHERS THEN
                RAISE WARNING '[LinkScript] Unable to normalize embedding column to text before resize: %', SQLERRM;
                RETURN;
        END;
    END IF;

    EXECUTE $sql$
        UPDATE ls_logic_fragment
        SET embedding = NULL
        WHERE embedding IS NOT NULL
          AND btrim(embedding) = ''
    $sql$;

    EXECUTE $sql$
        UPDATE ls_logic_fragment
        SET embedding = '['
            || regexp_replace(trim(both '[]' FROM embedding), '\s+', '', 'g')
            || repeat(
                ',0',
                GREATEST(
                    0,
                    4096 - COALESCE(
                        array_length(
                            string_to_array(
                                regexp_replace(trim(both '[]' FROM embedding), '\s+', '', 'g'),
                                ','
                            ),
                            1
                        ),
                        0
                    )
                )
            )
            || ']'
        WHERE embedding IS NOT NULL
          AND btrim(embedding) <> ''
          AND COALESCE(
                array_length(
                    string_to_array(
                        regexp_replace(trim(both '[]' FROM embedding), '\s+', '', 'g'),
                        ','
                    ),
                    1
                ),
                0
            ) < 4096
    $sql$;

    BEGIN
        EXECUTE $sql$
            ALTER TABLE ls_logic_fragment
                ALTER COLUMN embedding TYPE vector(4096)
                USING CASE
                        WHEN embedding IS NULL OR btrim(embedding) = '' THEN NULL
                        ELSE embedding::vector
                      END
        $sql$;
    EXCEPTION
        WHEN OTHERS THEN
            RAISE WARNING '[LinkScript] Unable to resize embedding column to vector(4096): %', SQLERRM;
            RETURN;
    END;

    BEGIN
        EXECUTE '
            CREATE INDEX IF NOT EXISTS idx_ls_logic_fragment_embedding
                ON ls_logic_fragment USING hnsw (embedding vector_cosine_ops)
        ';
    EXCEPTION
        WHEN OTHERS THEN
            RAISE WARNING '[LinkScript] Unable to recreate pgvector HNSW index: %', SQLERRM;
    END;
END $$;
