DO $$
DECLARE
    pgvector_ready boolean := true;
BEGIN
    BEGIN
        CREATE EXTENSION IF NOT EXISTS vector;
    EXCEPTION
        WHEN OTHERS THEN
            pgvector_ready := false;
            RAISE NOTICE 'pgvector extension is not available, keeping lexical fallback mode: %', SQLERRM;
    END;

    IF pgvector_ready THEN
        BEGIN
            ALTER TABLE ls_logic_fragment
                ALTER COLUMN embedding TYPE vector(1536)
                USING CASE
                        WHEN embedding IS NULL OR btrim(embedding) = '' THEN NULL
                        ELSE embedding::vector
                      END;
        EXCEPTION
            WHEN OTHERS THEN
                RAISE NOTICE 'Unable to convert embedding column to vector: %', SQLERRM;
        END;

        IF NOT EXISTS (
            SELECT 1
            FROM pg_indexes
            WHERE schemaname = current_schema()
              AND indexname = 'idx_ls_logic_fragment_embedding'
        ) THEN
            BEGIN
                CREATE INDEX idx_ls_logic_fragment_embedding
                    ON ls_logic_fragment USING hnsw (embedding vector_cosine_ops);
            EXCEPTION
                WHEN OTHERS THEN
                    RAISE NOTICE 'Unable to create pgvector index: %', SQLERRM;
            END;
        END IF;
    END IF;
END $$;
