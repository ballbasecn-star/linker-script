DO $$
BEGIN
    BEGIN
        CREATE EXTENSION IF NOT EXISTS vector;
    EXCEPTION
        WHEN OTHERS THEN
            RAISE NOTICE '[LinkScript] pgvector extension is not available in repeatable compatibility migration: %',
                SQLERRM;
            RETURN;
    END;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'ls_logic_fragment'
          AND column_name = 'embedding'
          AND udt_name = 'vector'
    ) AND NOT EXISTS (
        SELECT 1
        FROM pg_indexes
        WHERE schemaname = current_schema()
          AND indexname = 'idx_ls_logic_fragment_embedding'
    ) THEN
        BEGIN
            EXECUTE '
                CREATE INDEX idx_ls_logic_fragment_embedding
                    ON ls_logic_fragment USING hnsw (embedding vector_cosine_ops)
            ';
        EXCEPTION
            WHEN OTHERS THEN
                RAISE NOTICE '[LinkScript] HNSW index unavailable, falling back to IVFFLAT: %', SQLERRM;
                BEGIN
                    EXECUTE '
                        CREATE INDEX idx_ls_logic_fragment_embedding
                            ON ls_logic_fragment USING ivfflat (embedding vector_cosine_ops)
                            WITH (lists = 100)
                    ';
                EXCEPTION
                    WHEN OTHERS THEN
                        RAISE NOTICE '[LinkScript] Unable to create pgvector index in repeatable migration: %',
                            SQLERRM;
                END;
        END;
    END IF;
END $$;
