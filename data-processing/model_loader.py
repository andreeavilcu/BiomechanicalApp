import logging
import math
import os

import cv2
import mediapipe as mp
import numpy as np

logger = logging.getLogger(__name__)

DEBUG_DIR = os.path.join(os.path.dirname(__file__), "debug")
os.makedirs(DEBUG_DIR, exist_ok=True)


VOXEL_SIZE = 0.005          
MAX_POINTS = 100_000
RAW_CLOUD_TARGET = 50_000
MIN_POINTS_AFTER_DOWNSAMPLE = 1_000
MIN_DETECTION_CONFIDENCE = 0.3
IMAGE_SIZE = 2048
PLATFORM_N_SLICES = 20
PLATFORM_RATIO_THRESHOLD = 1.6

LANDMARK_MAPPING = {
    "nose": 0, "l_ear": 7, "r_ear": 8,
    "l_shoulder": 11, "r_shoulder": 12,
    "l_hip": 23, "r_hip": 24,
    "l_knee": 25, "r_knee": 26,
    "l_ankle": 27, "r_ankle": 28,
}


# --- Rotation helpers (module-level) ---

def _rotation_y(deg: float) -> np.ndarray:
    r = math.radians(deg)
    c, s = math.cos(r), math.sin(r)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def _rotation_x(deg: float) -> np.ndarray:
    r = math.radians(deg)
    c, s = math.cos(r), math.sin(r)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def _rotation_z(deg: float) -> np.ndarray:
    r = math.radians(deg)
    c, s = math.cos(r), math.sin(r)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def _get_rotation_matrices():
    matrices, labels = [], []
    for angle in [0, 30, -30, 60, -60, 90, -90, 120, -120, 150, -150, 180]:
        matrices.append(_rotation_y(angle))
        labels.append(f"Rot_Y_{angle}")
    for angle in [90, -90, 45, -45]:
        matrices.append(_rotation_x(angle))
        labels.append(f"Rot_X_{angle}")
    for angle in [90, -90]:
        matrices.append(_rotation_z(angle))
        labels.append(f"Rot_Z_{angle}")
    return zip(matrices, labels)



class PoseEstimator:
    def __init__(self):
        logger.info("Initializing Multi-View BruteForce Engine v7")
        self._mp_pose = mp.solutions.pose
        self._pose = self._mp_pose.Pose(
            static_image_mode=True,
            model_complexity=2,
            enable_segmentation=False,
            min_detection_confidence=MIN_DETECTION_CONFIDENCE,
        )
        logger.info("PoseEstimator ready")

    def remove_platform_by_spread_jump(self, points_rotated, n_slices=PLATFORM_N_SLICES, ratio_threshold=PLATFORM_RATIO_THRESHOLD):
        if len(points_rotated) < 200:
            return points_rotated, False

        min_y = np.min(points_rotated[:, 1])
        max_y = np.max(points_rotated[:, 1])
        height = max_y - min_y
        if height <= 0:
            return points_rotated, False

        slice_h = height / n_slices
        spreads = []
        for i in range(n_slices):
            lo = min_y + i * slice_h
            hi = lo + slice_h
            mask = (points_rotated[:, 1] >= lo) & (points_rotated[:, 1] < hi)
            pts = points_rotated[mask]
            if len(pts) < 10:
                spreads.append(0.0)
                continue
            spread = max(
                np.max(pts[:, 0]) - np.min(pts[:, 0]),
                np.max(pts[:, 2]) - np.min(pts[:, 2]),
            )
            spreads.append(float(spread))

        spreads = np.array(spreads)
        mid_lo, mid_hi = int(n_slices * 0.2), int(n_slices * 0.8)
        mid_vals = spreads[mid_lo:mid_hi]
        mid_vals = mid_vals[mid_vals > 0]
        if len(mid_vals) == 0 or np.median(mid_vals) == 0:
            return points_rotated, False

        median_spread = np.median(mid_vals)
        ratios = spreads / median_spread
        logger.debug("Median body spread: %.3fm | Slice ratios: %s", median_spread, [round(r, 1) for r in ratios])

        bottom_cut = 0
        for i in range(n_slices // 4):
            if ratios[i] > ratio_threshold:
                bottom_cut = i + 1

        top_cut = n_slices
        for i in range(n_slices - 1, n_slices * 3 // 4, -1):
            if ratios[i] > ratio_threshold:
                top_cut = i

        if bottom_cut == 0 and top_cut == n_slices:
            logger.debug("No platform detected")
            return points_rotated, False

        cut_lo = min_y + bottom_cut * slice_h
        cut_hi = min_y + top_cut * slice_h
        keep = (points_rotated[:, 1] >= cut_lo) & (points_rotated[:, 1] < cut_hi)
        cleaned = points_rotated[keep]
        pct = (len(points_rotated) - len(cleaned)) / len(points_rotated) * 100

        loc = []
        if bottom_cut > 0:
            loc.append(f"bottom ({bottom_cut} slices)")
        if top_cut < n_slices:
            loc.append(f"top ({n_slices - top_cut} slices)")
        logger.info("Platform detected at %s -> removed %.1f%% of points", ", ".join(loc), pct)
        return cleaned, True

    def render_snapshot(self, points, image_size=IMAGE_SIZE):
        u_coords, v_coords = points[:, 0], points[:, 1]
        min_u, max_u = np.min(u_coords), np.max(u_coords)
        min_v, max_v = np.min(v_coords), np.max(v_coords)

        max_span = max(max_u - min_u, max_v - min_v) or 1.0
        scale = (image_size * 0.85) / max_span
        center_u = (min_u + max_u) / 2
        center_v = (min_v + max_v) / 2

        u_px = ((u_coords - center_u) * scale + image_size / 2).astype(int)
        v_px = (image_size / 2 - (v_coords - center_v) * scale).astype(int)
        valid = (u_px >= 0) & (u_px < image_size) & (v_px >= 0) & (v_px < image_size)

        mask = np.zeros((image_size, image_size), dtype=np.uint8)
        mask[v_px[valid], u_px[valid]] = 255
        kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (11, 11))
        mask = cv2.dilate(mask, kernel)

        img = np.full((image_size, image_size, 3), 255, dtype=np.uint8)
        img[mask > 0] = [0, 0, 0]
        img = cv2.GaussianBlur(img, (5, 5), 0)

        return img, {"scale": scale, "center_u": center_u, "center_v": center_v, "image_size": image_size}

    def _compute_head_up_score(self, landmarks):
        head_y = landmarks[0].y
        feet_y = (landmarks[27].y + landmarks[28].y) / 2.0
        hip_y = (landmarks[23].y + landmarks[24].y) / 2.0
        shoulder_y = (landmarks[11].y + landmarks[12].y) / 2.0

        correct = (head_y < shoulder_y) and (shoulder_y < hip_y) and (hip_y < feet_y)
        inverted = (head_y > shoulder_y) and (shoulder_y > hip_y)
        diff = feet_y - head_y

        if correct and diff > 0.3:
            return 0.8, "HEAD UP - correct anatomy order"
        if correct:
            return 0.4, "HEAD UP - partial order"
        if inverted:
            return -1.0, "HEAD DOWN - inverted"
        if diff > 0.15:
            return 0.2, "HEAD probably UP"
        return -0.3, "HEAD probably DOWN"

    def _preprocess_cloud(self, pcd):
        """Downsample and center the cloud; also prepare the raw subsample for the API response."""
        n_original = len(np.asarray(pcd.points))
        pcd_down = pcd.voxel_down_sample(voxel_size=VOXEL_SIZE)
        points = np.asarray(pcd_down.points)
        n_down = len(points)
        logger.info("Downsampled: %d -> %d points (voxel=%.3fm)", n_original, n_down, VOXEL_SIZE)

        if n_down < MIN_POINTS_AFTER_DOWNSAMPLE:
            logger.warning("Too few points after downsampling, using original")
            points = np.asarray(pcd.points)

        if len(points) > MAX_POINTS:
            np.random.seed(42)
            points = points[np.random.choice(len(points), MAX_POINTS, replace=False)]
            logger.info("Capped to %d points", MAX_POINTS)

        global_center = np.mean(points, axis=0)
        points_centered = points - global_center

        try:
            raw_pts = np.asarray(pcd.points)
            if len(raw_pts) > RAW_CLOUD_TARGET:
                np.random.seed(42)
                raw_pts = raw_pts[np.random.choice(len(raw_pts), RAW_CLOUD_TARGET, replace=False)]
            point_cloud_data = raw_pts.astype(float).tolist()
            logger.info("Raw cloud for response: %d points", len(raw_pts))
        except Exception as e:
            logger.warning("Could not prepare raw cloud: %s", e)
            point_cloud_data = []

        return points_centered, global_center, point_cloud_data

    def _find_best_orientation(self, points_centered):
        """Try all rotation matrices and return the best-scoring pose detection."""
        best_score = -999
        best_results = None
        best_rotation = None
        best_points_rotated = None

        for rot_mat, label in _get_rotation_matrices():
            points_rotated = np.dot(points_centered, rot_mat.T)
            img, _ = self.render_snapshot(points_rotated)
            cv2.imwrite(os.path.join(DEBUG_DIR, f"debug_{label}.png"), img)

            results = self._pose.process(img)
            if not results.pose_landmarks:
                logger.debug("%s: no landmarks", label)
                continue

            lms = results.pose_landmarks.landmark
            h_r = np.max(points_rotated[:, 1]) - np.min(points_rotated[:, 1])
            w_r = np.max(points_rotated[:, 0]) - np.min(points_rotated[:, 0])
            aspect = h_r / (w_r + 0.001)

            if aspect > 2.5:
                orient_bonus = 0.6
            elif aspect > 2.0:
                orient_bonus = 0.5
            elif aspect > 1.5:
                orient_bonus = 0.2
            else:
                orient_bonus = -0.3

            head_up_bonus, head_txt = self._compute_head_up_score(lms)
            base_score = np.mean([lm.visibility for lm in lms])
            score = base_score + orient_bonus + head_up_bonus

            logger.debug(
                "%s | aspect=%.2f | %s | score=%.3f (base=%.2f, orient=%.2f, head=%.2f)",
                label, aspect, head_txt, score, base_score, orient_bonus, head_up_bonus,
            )

            if score > best_score:
                best_score = score
                best_results = results
                best_rotation = rot_mat
                best_points_rotated = points_rotated.copy()

        return best_score, best_results, best_rotation, best_points_rotated

    def _extract_keypoints(self, points_clean, best_rotation, global_center, real_height_meters):
        """Project MediaPipe landmarks back into 3D space with side-view depth correction."""
        img_front, params_front = self.render_snapshot(points_clean)
        cv2.imwrite(os.path.join(DEBUG_DIR, "debug_CLEAN_FRONT.png"), img_front)
        results_front = self._pose.process(img_front)
        if not results_front or not results_front.pose_landmarks:
            raise RuntimeError("MediaPipe failed on cleaned cloud image.")

        lm_front = results_front.pose_landmarks.landmark
        logger.info("Re-detected %d landmarks on clean image", len(lm_front))

        # Rotate 90° around Y so side_x = depth axis
        R_y90 = np.array([[0, 0, 1], [0, 1, 0], [-1, 0, 0]])
        points_side = np.dot(points_clean, R_y90.T)
        img_side, params_side = self.render_snapshot(points_side)
        cv2.imwrite(os.path.join(DEBUG_DIR, "debug_CLEAN_SIDE.png"), img_side)
        results_side = self._pose.process(img_side)
        lm_side = results_side.pose_landmarks.landmark if (results_side and results_side.pose_landmarks) else None
        logger.info("Side view: %s", "landmarks detected" if lm_side else "no landmarks, using nearest-point depth")

        res_f = params_front["image_size"]
        scale_f = params_front["scale"]
        c_u_f, c_v_f = params_front["center_u"], params_front["center_v"]

        final_keypoints = {}
        for name, idx in LANDMARK_MAPPING.items():
            lm = lm_front[idx]
            rot_x = (lm.x * res_f - res_f / 2) / scale_f + c_u_f
            rot_y = c_v_f - (lm.y * res_f - res_f / 2) / scale_f

            dist_sq = (points_clean[:, 0] - rot_x) ** 2 + (points_clean[:, 1] - rot_y) ** 2
            nearby = dist_sq < 0.05
            rot_z_nn = (
                np.median(points_clean[nearby, 2]) if np.any(nearby)
                else np.median(points_clean[np.argsort(dist_sq)[:10], 2])
            )

            if lm_side:
                lm_s = lm_side[idx]
                rot_z_side = (lm_s.x * params_side["image_size"] - params_side["image_size"] / 2) / params_side["scale"] + params_side["center_u"]
                vis = min(1.0, getattr(lm_s, "visibility", 0.5) * 1.5)
                rot_z = vis * rot_z_side + (1.0 - vis) * rot_z_nn
            else:
                rot_z = rot_z_nn

            point_orig = np.dot(np.array([rot_x, rot_y, rot_z]), best_rotation) + global_center
            final_keypoints[name] = {"x": float(point_orig[0]), "y": float(point_orig[1]), "z": float(point_orig[2])}

        min_y, max_y = np.min(points_clean[:, 1]), np.max(points_clean[:, 1])
        current_height = max(max_y - min_y, 1.0)
        scaling_factor = real_height_meters / current_height
        logger.info("Height scale: %.3f -> %.3fm (factor=%.4f)", current_height, real_height_meters, scaling_factor)
        if scaling_factor > 3.0 or scaling_factor < 0.5:
            logger.warning("Unusual scaling factor: %.2f", scaling_factor)

        for k in final_keypoints:
            for ax in ("x", "y", "z"):
                final_keypoints[k][ax] *= scaling_factor

        def mid(p1, p2):
            if p1 in final_keypoints and p2 in final_keypoints:
                return {ax: (final_keypoints[p1][ax] + final_keypoints[p2][ax]) / 2 for ax in ("x", "y", "z")}
            return None

        final_keypoints["neck"] = mid("l_shoulder", "r_shoulder")
        final_keypoints["pelvis"] = mid("l_hip", "r_hip")
        final_keypoints["head"] = final_keypoints.get("nose") or final_keypoints.get("neck")
        final_keypoints["meta"] = {
            "method": "BruteForce_v7_MultiView",
            "target_height": real_height_meters,
            "scaling_factor": float(scaling_factor),
            "side_view_used": lm_side is not None,
        }

        return {k: v for k, v in final_keypoints.items() if v is not None}

    def predict(self, pcd, real_height_meters=1.75):
        points_centered, global_center, point_cloud_data = self._preprocess_cloud(pcd)
        logger.info("Processing for target height: %.2fm", real_height_meters)

        best_score, best_results, best_rotation, best_points_rotated = self._find_best_orientation(points_centered)

        if best_results is None:
            raise RuntimeError("No landmarks detected in any orientation. Try a cleaner scan.")
        if best_score < -1.5:
            raise RuntimeError(f"Best score too low ({best_score:.3f}). Try a cleaner scan.")

        logger.info("Best orientation score: %.3f", best_score)

        points_clean, platform_removed = self.remove_platform_by_spread_jump(best_points_rotated)
        keypoints = self._extract_keypoints(points_clean, best_rotation, global_center, real_height_meters)

        keypoints["meta"]["platform_removed"] = platform_removed
        keypoints["meta"]["best_score"] = float(best_score)
        keypoints["point_cloud"] = point_cloud_data

        return keypoints


AI_Pose_Estimator = PoseEstimator
