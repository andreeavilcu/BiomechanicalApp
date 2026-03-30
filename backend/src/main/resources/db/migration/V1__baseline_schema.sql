CREATE TABLE IF NOT EXISTS users (
                                     user_id         BIGSERIAL PRIMARY KEY,
                                     email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    first_name      VARCHAR(100),
    last_name       VARCHAR(100),
    date_of_birth   DATE NOT NULL,
    gender          VARCHAR(10),
    height_cm       NUMERIC(5, 2),
    role            VARCHAR(20) NOT NULL DEFAULT 'PATIENT',
    specialization  VARCHAR(150),
    license_number  VARCHAR(50) UNIQUE,
    institution     VARCHAR(200),
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP
    );


CREATE TABLE IF NOT EXISTS scan_sessions (
    session_id          BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(user_id),
    scan_file_path      VARCHAR(500) NOT NULL,
    scan_date           TIMESTAMP NOT NULL DEFAULT NOW(),
    scan_type           VARCHAR(50) DEFAULT 'LIDAR',
    processing_status   VARCHAR(50) DEFAULT 'PENDING',
    error_message       TEXT,
    python_method       VARCHAR(100),
    ai_confidence_score NUMERIC(4, 3),
    target_height_meters NUMERIC(4, 2),
    scaling_factor      NUMERIC(6, 4),
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
    );

CREATE INDEX IF NOT EXISTS idx_scan_sessions_user
    ON scan_sessions(user_id);

CREATE INDEX IF NOT EXISTS idx_scan_sessions_status
    ON scan_sessions(processing_status);


CREATE TABLE IF NOT EXISTS biomechanics_metrics (
    metric_id               BIGSERIAL PRIMARY KEY,
    session_id              BIGINT NOT NULL UNIQUE REFERENCES scan_sessions(session_id),
    q_angle_left            NUMERIC(6, 2),
    q_angle_right           NUMERIC(6, 2),
    fhp_angle               NUMERIC(6, 2),
    fhp_distance_cm         NUMERIC(6, 2),
    shoulder_asymmetry_cm   NUMERIC(6, 2),
    stance_phase_left       NUMERIC(5, 2),
    stance_phase_right      NUMERIC(5, 2),
    swing_phase_left        NUMERIC(5, 2),
    swing_phase_right       NUMERIC(5, 2),
    knee_flexion_max_left   NUMERIC(6, 2),
    knee_flexion_max_right  NUMERIC(6, 2),
    cadence                 INTEGER,
    global_posture_score    NUMERIC(5, 2),
    risk_level              VARCHAR(20),
    created_at              TIMESTAMP NOT NULL DEFAULT NOW()
    );

CREATE INDEX IF NOT EXISTS idx_metrics_session
    ON biomechanics_metrics(session_id);


CREATE TABLE IF NOT EXISTS raw_keypoints (
    keypoint_id     BIGSERIAL PRIMARY KEY,
    session_id      BIGINT NOT NULL UNIQUE REFERENCES scan_sessions(session_id),
    nose_x          NUMERIC(8, 4),
    nose_y          NUMERIC(8, 4),
    nose_z          NUMERIC(8, 4),
    l_ear_x         NUMERIC(8, 4),
    l_ear_y         NUMERIC(8, 4),
    l_ear_z         NUMERIC(8, 4),
    r_ear_x         NUMERIC(8, 4),
    r_ear_y         NUMERIC(8, 4),
    r_ear_z         NUMERIC(8, 4),
    neck_x          NUMERIC(8, 4),
    neck_y          NUMERIC(8, 4),
    neck_z          NUMERIC(8, 4),
    l_shoulder_x    NUMERIC(8, 4),
    l_shoulder_y    NUMERIC(8, 4),
    l_shoulder_z    NUMERIC(8, 4),
    r_shoulder_x    NUMERIC(8, 4),
    r_shoulder_y    NUMERIC(8, 4),
    r_shoulder_z    NUMERIC(8, 4),
    l_hip_x         NUMERIC(8, 4),
    l_hip_y         NUMERIC(8, 4),
    l_hip_z         NUMERIC(8, 4),
    r_hip_x         NUMERIC(8, 4),
    r_hip_y         NUMERIC(8, 4),
    r_hip_z         NUMERIC(8, 4),
    pelvis_x        NUMERIC(8, 4),
    pelvis_y        NUMERIC(8, 4),
    pelvis_z        NUMERIC(8, 4),
    l_knee_x        NUMERIC(8, 4),
    l_knee_y        NUMERIC(8, 4),
    l_knee_z        NUMERIC(8, 4),
    r_knee_x        NUMERIC(8, 4),
    r_knee_y        NUMERIC(8, 4),
    r_knee_z        NUMERIC(8, 4),
    l_ankle_x       NUMERIC(8, 4),
    l_ankle_y       NUMERIC(8, 4),
    l_ankle_z       NUMERIC(8, 4),
    r_ankle_x       NUMERIC(8, 4),
    r_ankle_y       NUMERIC(8, 4),
    r_ankle_z       NUMERIC(8, 4),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
    );


CREATE TABLE IF NOT EXISTS patient_specialist_assignments (
    assignment_id   BIGSERIAL PRIMARY KEY,
    patient_id      BIGINT NOT NULL REFERENCES users(user_id),
    specialist_id   BIGINT NOT NULL REFERENCES users(user_id),
    assigned_date   DATE NOT NULL DEFAULT CURRENT_DATE,
    end_date        DATE,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    referral_reason TEXT,
    clinical_notes  TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_patient_specialist UNIQUE (patient_id, specialist_id)
    );

CREATE INDEX IF NOT EXISTS idx_assignments_patient
    ON patient_specialist_assignments(patient_id);

CREATE INDEX IF NOT EXISTS idx_assignments_specialist
    ON patient_specialist_assignments(specialist_id);