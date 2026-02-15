package com.biomechanics.backend.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "raw_keypoints")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RawKeypoints {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "keypoint_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ScanSession scanSession;

    @Column(name = "nose_x", precision = 8, scale = 4)
    private BigDecimal noseX;
    @Column(name = "nose_y", precision = 8, scale = 4)
    private BigDecimal noseY;
    @Column(name = "nose_z", precision = 8, scale = 4)
    private BigDecimal noseZ;

    @Column(name = "l_ear_x", precision = 8, scale = 4)
    private BigDecimal lEarX;
    @Column(name = "l_ear_y", precision = 8, scale = 4)
    private BigDecimal lEarY;
    @Column(name = "l_ear_z", precision = 8, scale = 4)
    private BigDecimal lEarZ;

    @Column(name = "r_ear_x", precision = 8, scale = 4)
    private BigDecimal rEarX;
    @Column(name = "r_ear_y", precision = 8, scale = 4)
    private BigDecimal rEarY;
    @Column(name = "r_ear_z", precision = 8, scale = 4)
    private BigDecimal rEarZ;

    @Column(name = "neck_x", precision = 8, scale = 4)
    private BigDecimal neckX;
    @Column(name = "neck_y", precision = 8, scale = 4)
    private BigDecimal neckY;
    @Column(name = "neck_z", precision = 8, scale = 4)
    private BigDecimal neckZ;

    @Column(name = "l_shoulder_x", precision = 8, scale = 4)
    private BigDecimal lShoulderX;
    @Column(name = "l_shoulder_y", precision = 8, scale = 4)
    private BigDecimal lShoulderY;
    @Column(name = "l_shoulder_z", precision = 8, scale = 4)
    private BigDecimal lShoulderZ;

    @Column(name = "r_shoulder_x", precision = 8, scale = 4)
    private BigDecimal rShoulderX;
    @Column(name = "r_shoulder_y", precision = 8, scale = 4)
    private BigDecimal rShoulderY;
    @Column(name = "r_shoulder_z", precision = 8, scale = 4)
    private BigDecimal rShoulderZ;

    @Column(name = "l_hip_x", precision = 8, scale = 4)
    private BigDecimal lHipX;
    @Column(name = "l_hip_y", precision = 8, scale = 4)
    private BigDecimal lHipY;
    @Column(name = "l_hip_z", precision = 8, scale = 4)
    private BigDecimal lHipZ;

    @Column(name = "r_hip_x", precision = 8, scale = 4)
    private BigDecimal rHipX;
    @Column(name = "r_hip_y", precision = 8, scale = 4)
    private BigDecimal rHipY;
    @Column(name = "r_hip_z", precision = 8, scale = 4)
    private BigDecimal rHipZ;

    @Column(name = "pelvis_x", precision = 8, scale = 4)
    private BigDecimal pelvisX;
    @Column(name = "pelvis_y", precision = 8, scale = 4)
    private BigDecimal pelvisY;
    @Column(name = "pelvis_z", precision = 8, scale = 4)
    private BigDecimal pelvisZ;

    @Column(name = "l_knee_x", precision = 8, scale = 4)
    private BigDecimal lKneeX;
    @Column(name = "l_knee_y", precision = 8, scale = 4)
    private BigDecimal lKneeY;
    @Column(name = "l_knee_z", precision = 8, scale = 4)
    private BigDecimal lKneeZ;

    @Column(name = "r_knee_x", precision = 8, scale = 4)
    private BigDecimal rKneeX;
    @Column(name = "r_knee_y", precision = 8, scale = 4)
    private BigDecimal rKneeY;
    @Column(name = "r_knee_z", precision = 8, scale = 4)
    private BigDecimal rKneeZ;

    @Column(name = "l_ankle_x", precision = 8, scale = 4)
    private BigDecimal lAnkleX;
    @Column(name = "l_ankle_y", precision = 8, scale = 4)
    private BigDecimal lAnkleY;
    @Column(name = "l_ankle_z", precision = 8, scale = 4)
    private BigDecimal lAnkleZ;

    @Column(name = "r_ankle_x", precision = 8, scale = 4)
    private BigDecimal rAnkleX;
    @Column(name = "r_ankle_y", precision = 8, scale = 4)
    private BigDecimal rAnkleY;
    @Column(name = "r_ankle_z", precision = 8, scale = 4)
    private BigDecimal rAnkleZ;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
