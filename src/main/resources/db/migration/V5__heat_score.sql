ALTER TABLE ls_script ADD COLUMN heat_score DOUBLE PRECISION DEFAULT 0;
ALTER TABLE ls_script ADD COLUMN heat_level VARCHAR(2) DEFAULT 'D';

CREATE INDEX idx_ls_script_heat_level ON ls_script (heat_level);
