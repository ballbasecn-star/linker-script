-- Versioned replacement for R__enable_pgvector_if_available.sql
-- Runs once; safely upgrades the embedding column from TEXT to vector(1536) if pgvector is available.

DO $$
DECLARE
    pgvector_ready boolean := true;
BEGIN
    BEGIN
        CREATE EXTENSION IF NOT EXISTS vector;
    EXCEPTION
        WHEN OTHERS THEN
            pgvector_ready := false;
            RAISE WARNING '[LinkScript] pgvector extension is NOT available – vector search will use lexical fallback. Error: %', SQLERRM;
    END;

    IF pgvector_ready THEN
        RAISE NOTICE '[LinkScript] pgvector extension ready, upgrading embedding column to vector(1536)';

        BEGIN
            ALTER TABLE ls_logic_fragment
                ALTER COLUMN embedding TYPE vector(1536)
                USING CASE
                        WHEN embedding IS NULL OR btrim(embedding) = '' THEN NULL
                        ELSE embedding::vector
                      END;
        EXCEPTION
            WHEN OTHERS THEN
                RAISE WARNING '[LinkScript] Unable to convert embedding column to vector: %', SQLERRM;
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
                RAISE NOTICE '[LinkScript] HNSW index created on ls_logic_fragment.embedding';
            EXCEPTION
                WHEN OTHERS THEN
                    RAISE WARNING '[LinkScript] Unable to create pgvector HNSW index: %', SQLERRM;
            END;
        END IF;
    END IF;
END $$;
