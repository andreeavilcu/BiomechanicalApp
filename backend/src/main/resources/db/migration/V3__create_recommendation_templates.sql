CREATE TABLE recommendation_templates (
     id                  BIGSERIAL PRIMARY KEY,
     metric_type         VARCHAR(30) NOT NULL,
     severity            VARCHAR(20) NOT NULL,
     title               VARCHAR(200) NOT NULL,
     biomechanical_cause TEXT NOT NULL,
     exercise            TEXT,
     ergonomic_tip       TEXT,
     blocked_ergonomic_tip TEXT,
     normal_range_male   VARCHAR(50),
     normal_range_female VARCHAR(50),
     CONSTRAINT uq_metric_severity UNIQUE (metric_type, severity)
);

INSERT INTO recommendation_templates
(metric_type, severity, title, biomechanical_cause, exercise, ergonomic_tip, blocked_ergonomic_tip, normal_range_male, normal_range_female)
VALUES (
           'FHP', 'MODERATE',
           'Forward Head Posture (Anterior Head Deviation)',
           'Prolonged static flexed postures (e.g. desk work) reduce the force arm of the extensor muscles, transferring peak load onto the posterior structures and intervertebral discs. This can produce exaggerated thoracic posterior curvature (kyphosis) and a ''rounded shoulders'' posture, a condition often associated with disc degeneration or osteoporosis.',
           'Chin Tucks (Cervical Retraction) to activate deep cervical flexors. Gentle extensions while maintaining controlled lumbar lordosis.',
           'Avoid prolonged static postures by introducing active breaks. Adjust your monitor to eye level to reduce neck flexion.',
           NULL,
           '0° - 5°', '0° - 5°'
);


INSERT INTO recommendation_templates
(metric_type, severity, title, biomechanical_cause, exercise, ergonomic_tip, blocked_ergonomic_tip, normal_range_male, normal_range_female)
VALUES (
           'FHP', 'HIGH',
           'Severe Forward Head Posture',
           'Significant anterior head deviation detected. The excessive forward head position dramatically increases the gravitational moment on the cervical spine, overloading the extensor muscles (M. Trapezius) and accelerating intervertebral disc degeneration. This posture is strongly associated with chronic cervical pain and thoracic kyphosis.',
           'Chin Tucks (Cervical Retraction) to activate deep cervical flexors. Gentle extensions while maintaining controlled lumbar lordosis.',
           'Avoid prolonged static postures by introducing active breaks. Adjust your monitor to eye level to reduce neck flexion.',
           'Exercises are blocked due to high risk. Consultation with a physiotherapist is required before starting any corrective program.',
           '0° - 5°', '0° - 5°'
);

INSERT INTO recommendation_templates
(metric_type, severity, title, biomechanical_cause, exercise, ergonomic_tip, blocked_ergonomic_tip, normal_range_male, normal_range_female)
VALUES (
           'Q_ANGLE', 'MODERATE',
           'Elevated Q Angle',
           'The Q angle exceeds the normal physiological range. Males have normal Q angles of 10°-14° and females 15°-17° due to pelvic width differences. Elevated values increase lateral stress on the patellofemoral joint. The common functional cause is weakness of the hip abductors, which allows excessive pelvic movement and internal rotation of the femur (Trendelenburg gait pattern).',
           'Quadriceps isometric exercises and gluteus medius strengthening. Knee stabilization drills with resistance bands.',
           'Avoid deep squats and running on hard surfaces until correction is achieved.',
           NULL,
           '10° - 14°', '15° - 17°'
       );

INSERT INTO recommendation_templates
(metric_type, severity, title, biomechanical_cause, exercise, ergonomic_tip, blocked_ergonomic_tip, normal_range_male, normal_range_female)
VALUES (
           'Q_ANGLE', 'HIGH',
           'Elevated Q Angle / Genu Valgum',
           'The Q angle significantly exceeds normal limits, constituting genu valgum (knock knees). This dramatically increases lateral stress on the patellofemoral joint and elevates risk of chondromalacia patellae and joint instability. The primary functional cause is weakness of the hip abductors, leading to excessive pelvic movement and internal femoral rotation (Trendelenburg gait pattern).',
           'Quadriceps isometric exercises and gluteus medius strengthening. Knee stabilization drills with resistance bands.',
           'Avoid deep squats and running on hard surfaces until correction is achieved.',
           'Deep squats are STRICTLY contraindicated. High-impact dynamic exercises are blocked due to elevated shear forces on the knee.',
           '10° - 14°', '15° - 17°'
);

INSERT INTO recommendation_templates
(metric_type, severity, title, biomechanical_cause, exercise, ergonomic_tip, blocked_ergonomic_tip, normal_range_male, normal_range_female)
VALUES (
           'SHOULDER_ASYMMETRY', 'MODERATE',
           'Shoulder Asymmetry / Scapular Imbalance',
           'Somatic asymmetry can be functional, often resulting from predominant use of a single dominant limb. Biomechanically, a shoulder level difference frequently arises from asymmetric scapular elevation and sustained upper trapezius contraction, or from a more significant spinal malalignment such as scoliosis (lateral deviation in a C or S pattern). Hip abductor weakness can create asymmetric pelvic tilt that propagates upward through the kinetic chain.',
           'Unilateral stretching to release the upper trapezius on the elevated side. Strengthening exercises to balance the scapular force couple.',
           'Avoid carrying weight asymmetrically (e.g. backpack on one shoulder). A full kinetic chain assessment is recommended, including pelvic tilt evaluation.',
           NULL,
           '≤ 1.5 cm', '≤ 1.5 cm'
);

INSERT INTO recommendation_templates
(metric_type, severity, title, biomechanical_cause, exercise, ergonomic_tip, blocked_ergonomic_tip, normal_range_male, normal_range_female)
VALUES (
           'SHOULDER_ASYMMETRY', 'HIGH',
           'Severe Shoulder Asymmetry / Structural Imbalance',
           'Significant shoulder level difference detected, indicating possible structural scoliosis or severe muscular imbalance. The asymmetric scapular elevation creates compensatory movements throughout the kinetic chain, generating cumulative stress on the sacroiliac joint and knees. This level of asymmetry requires professional evaluation to determine if the cause is local or compensatory.',
           'Unilateral stretching to release the upper trapezius on the elevated side. Strengthening exercises to balance the scapular force couple.',
           'Avoid carrying weight asymmetrically (e.g. backpack on one shoulder). A full kinetic chain assessment is recommended, including pelvic tilt evaluation.',
           'Exercises are blocked. Consultation with an orthopedic specialist or physiotherapist is mandatory to rule out structural causes before initiating corrective exercises.',
           '≤ 1.5 cm', '≤ 1.5 cm'
);

INSERT INTO recommendation_templates
(metric_type, severity, title, biomechanical_cause, exercise, ergonomic_tip, blocked_ergonomic_tip, normal_range_male, normal_range_female)
VALUES (
           'GLOBAL', 'LOW',
           'Optimal Biomechanics',
           'All parameters fall within normal physiological limits. No significant postural deviations were detected.',
           NULL,
           'Maintain your current physical activity routine. A follow-up scan is recommended in 90 days to track stability.',
           NULL,
           '0% - 20%', '0% - 20%'
);

INSERT INTO recommendation_templates
(metric_type, severity, title, biomechanical_cause, exercise, ergonomic_tip, blocked_ergonomic_tip, normal_range_male, normal_range_female)
VALUES (
           'GLOBAL', 'MODERATE',
           'Functional Postural Deviations Detected',
           'Postural deviations have been detected that can be corrected through targeted exercises. Active breaks and stretching routines are recommended.',
           NULL,
           'Follow the specific exercises recommended for each deviated metric. Re-evaluation is recommended in 30 days to assess progress.',
           NULL,
           '0% - 20%', '0% - 20%'
);

-- ================================================================
-- GLOBAL — HIGH (Red Zone)
-- ================================================================
INSERT INTO recommendation_templates
(metric_type, severity, title, biomechanical_cause, exercise, ergonomic_tip, blocked_ergonomic_tip, normal_range_male, normal_range_female)
VALUES (
           'GLOBAL', 'HIGH',
           'Biomechanical Risk Alert',
           'Detected values indicate possible structural or pathological changes. This application does not replace medical diagnosis.',
           NULL,
           'Consultation with a physiotherapist or rehabilitation specialist is strongly recommended. High-impact dynamic exercises have been blocked to prevent further injury.',
           'Consultation with a physiotherapist or rehabilitation specialist is strongly recommended. All exercises have been blocked. Do not attempt corrective exercises without professional supervision.',
           '0% - 20%', '0% - 20%'
       );