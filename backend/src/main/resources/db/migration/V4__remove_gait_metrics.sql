ALTER TABLE biomechanics_metrics
    DROP COLUMN IF EXISTS stance_phase_left,
    DROP COLUMN IF EXISTS stance_phase_right,
    DROP COLUMN IF EXISTS swing_phase_left,
    DROP COLUMN IF EXISTS swing_phase_right,
    DROP COLUMN IF EXISTS cadence,
    DROP COLUMN IF EXISTS knee_flexion_max_left,
    DROP COLUMN IF EXISTS knee_flexion_max_right;
