CREATE TABLE recommendations (
    recommendation_id   BIGSERIAL PRIMARY KEY,
    session_id          BIGINT NOT NULL
    REFERENCES scan_sessions(session_id) ON DELETE CASCADE,
    metric_type         VARCHAR(30) NOT NULL,
    severity            VARCHAR(20) NOT NULL,
    title               VARCHAR(200) NOT NULL,
    biomechanical_cause TEXT,
    exercise            TEXT,
    ergonomic_tip       TEXT,
    is_blocked          BOOLEAN NOT NULL DEFAULT FALSE,
    disclaimer_required BOOLEAN NOT NULL DEFAULT FALSE,
    detected_value      VARCHAR(50),
    normal_range        VARCHAR(50),
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_recommendations_session
    ON recommendations(session_id);

CREATE INDEX idx_recommendations_severity
    ON recommendations(severity);

CREATE INDEX idx_recommendations_metric_type
    ON recommendations(metric_type);