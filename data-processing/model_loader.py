import numpy as np
import mediapipe as mp
import cv2
import open3d as o3d
import math
import os

DEBUG_DIR = os.path.join(os.path.dirname(__file__), "debug")
os.makedirs(DEBUG_DIR, exist_ok=True)


class AI_Pose_Estimator:
    def __init__(self):
        print("--> [AI] Initializing Multi-View BruteForce Engine v7...")
        self.mp_pose = mp.solutions.pose
        self.pose = self.mp_pose.Pose(
            static_image_mode=True,
            model_complexity=2,
            enable_segmentation=False,
            min_detection_confidence=0.3,
        )
        print("   [AI] System Ready.")

    def remove_platform_by_spread_jump(self, points_rotated, n_slices=20, ratio_threshold=1.6):
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
            spread = max(np.max(pts[:, 0]) - np.min(pts[:, 0]), np.max(pts[:, 2]) - np.min(pts[:, 2]))
            spreads.append(float(spread))

        spreads = np.array(spreads)

        mid_lo = int(n_slices * 0.2)
        mid_hi = int(n_slices * 0.8)
        mid_vals = spreads[mid_lo:mid_hi]
        mid_vals = mid_vals[mid_vals > 0]
        if len(mid_vals) == 0:
            return points_rotated, False

        median_spread = np.median(mid_vals)
        if median_spread == 0:
            return points_rotated, False

        ratios = spreads / median_spread
        print(f"   [PLATFORM] Median body spread: {median_spread:.3f}m")
        print(f"   [PLATFORM] Slice ratios: {[round(r, 1) for r in ratios]}")

        bottom_cut = 0
        for i in range(n_slices // 4):
            if ratios[i] > ratio_threshold:
                bottom_cut = i + 1

        top_cut = n_slices
        for i in range(n_slices - 1, n_slices * 3 // 4, -1):
            if ratios[i] > ratio_threshold:
                top_cut = i

        if bottom_cut == 0 and top_cut == n_slices:
            print("   [PLATFORM] No platform detected.")
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
        print(f"   [PLATFORM] Detected at {', '.join(loc)} -> removed {pct:.1f}% of points.")
        return cleaned, True

    def render_snapshot(self, points, image_size=2048):
        u_coords = points[:, 0]
        v_coords = points[:, 1]

        min_u, max_u = np.min(u_coords), np.max(u_coords)
        min_v, max_v = np.min(v_coords), np.max(v_coords)

        max_span = max(max_u - min_u, max_v - min_v)
        if max_span == 0:
            max_span = 1.0

        scale = (image_size * 0.85) / max_span
        center_u = (min_u + max_u) / 2
        center_v = (min_v + max_v) / 2

        u_px = ((u_coords - center_u) * scale + image_size / 2).astype(int)
        v_px = (image_size / 2 - (v_coords - center_v) * scale).astype(int)

        valid = (u_px >= 0) & (u_px < image_size) & (v_px >= 0) & (v_px < image_size)

        # Vectorized: paint pixels then dilate — replaces the slow per-point cv2.circle loop
        mask = np.zeros((image_size, image_size), dtype=np.uint8)
        mask[v_px[valid], u_px[valid]] = 255
        kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (11, 11))
        mask = cv2.dilate(mask, kernel)

        img = np.full((image_size, image_size, 3), 255, dtype=np.uint8)
        img[mask > 0] = [0, 0, 0]
        img = cv2.GaussianBlur(img, (5, 5), 0)

        params = {"scale": scale, "center_u": center_u, "center_v": center_v, "image_size": image_size}
        return img, params

    def get_rotation_matrices(self):
        matrices, labels = [], []

        def ry(deg):
            r = math.radians(deg)
            c, s = math.cos(r), math.sin(r)
            return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])

        def rx(deg):
            r = math.radians(deg)
            c, s = math.cos(r), math.sin(r)
            return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])

        def rz(deg):
            r = math.radians(deg)
            c, s = math.cos(r), math.sin(r)
            return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])

        # Y-axis rotations every 30° — covers all horizontal body orientations
        for angle in [0, 30, -30, 60, -60, 90, -90, 120, -120, 150, -150, 180]:
            matrices.append(ry(angle))
            labels.append(f"Rot_Y_{angle}")

        # X tilts for lying down / skiing / forward-leaning
        for angle in [90, -90, 45, -45]:
            matrices.append(rx(angle))
            labels.append(f"Rot_X_{angle}")

        # Z tilts for sideways-leaning scans
        for angle in [90, -90]:
            matrices.append(rz(angle))
            labels.append(f"Rot_Z_{angle}")

        return zip(matrices, labels)

    def compute_head_up_score(self, landmarks):
        lms = landmarks
        head_y = lms[0].y
        feet_y = (lms[27].y + lms[28].y) / 2.0
        hip_y = (lms[23].y + lms[24].y) / 2.0
        shoulder_y = (lms[11].y + lms[12].y) / 2.0

        correct = (head_y < shoulder_y) and (shoulder_y < hip_y) and (hip_y < feet_y)
        inverted = (head_y > shoulder_y) and (shoulder_y > hip_y)
        diff = feet_y - head_y

        if correct and diff > 0.3:
            return 0.8, "HEAD UP - correct anatomy order"
        elif correct:
            return 0.4, "HEAD UP - partial order"
        elif inverted:
            return -1.0, "HEAD DOWN - inverted! Large penalty"
        elif diff > 0.15:
            return 0.2, "HEAD probably UP"
        else:
            return -0.3, "HEAD probably DOWN"

    def extract_keypoints_from_clean_cloud(self, points_clean, best_rotation, global_center, real_height_meters):
        # Front view
        img_front, params_front = self.render_snapshot(points_clean)
        cv2.imwrite(os.path.join(DEBUG_DIR, "debug_CLEAN_FRONT.png"), img_front)
        results_front = self.pose.process(img_front)
        if not results_front or not results_front.pose_landmarks:
            raise Exception("MediaPipe failed on cleaned cloud image.")
        lm_front = results_front.pose_landmarks.landmark
        print(f"   [KEYPOINTS] Re-detected on clean image: {len(lm_front)} landmarks")

        # Side view: rotate 90° around Y so that side_x = rot_z (depth becomes horizontal)
        # R_y90 = [[0,0,1],[0,1,0],[-1,0,0]] → points_side = points_clean @ R_y90.T
        # → side_x = rot_z, side_y = rot_y
        R_y90 = np.array([[0, 0, 1], [0, 1, 0], [-1, 0, 0]])
        points_side = np.dot(points_clean, R_y90.T)
        img_side, params_side = self.render_snapshot(points_side)
        cv2.imwrite(os.path.join(DEBUG_DIR, "debug_CLEAN_SIDE.png"), img_side)
        results_side = self.pose.process(img_side)
        lm_side = results_side.pose_landmarks.landmark if (results_side and results_side.pose_landmarks) else None
        print(f"   [SIDE VIEW] {'Landmarks detected — using for depth estimation' if lm_side else 'No landmarks — fallback to nearest-point depth'}")

        mapping = {
            "nose": 0, "l_ear": 7, "r_ear": 8,
            "l_shoulder": 11, "r_shoulder": 12,
            "l_hip": 23, "r_hip": 24,
            "l_knee": 25, "r_knee": 26,
            "l_ankle": 27, "r_ankle": 28,
        }

        res_f = params_front["image_size"]
        scale_f = params_front["scale"]
        c_u_f = params_front["center_u"]
        c_v_f = params_front["center_v"]

        final_keypoints = {}
        for name, idx in mapping.items():
            lm = lm_front[idx]
            rot_x = (lm.x * res_f - res_f / 2) / scale_f + c_u_f
            rot_y = c_v_f - (lm.y * res_f - res_f / 2) / scale_f

            # Nearest-point depth (fallback)
            dist_sq = (points_clean[:, 0] - rot_x) ** 2 + (points_clean[:, 1] - rot_y) ** 2
            nearby = dist_sq < 0.05
            rot_z_nn = (
                np.median(points_clean[nearby, 2]) if np.any(nearby)
                else np.median(points_clean[np.argsort(dist_sq)[:10], 2])
            )

            if lm_side:
                lm_s = lm_side[idx]
                res_s = params_side["image_size"]
                scale_s = params_side["scale"]
                # side_x = rot_z → invert the projection to get rot_z
                rot_z_side = (lm_s.x * res_s - res_s / 2) / scale_s + params_side["center_u"]
                vis = min(1.0, getattr(lm_s, "visibility", 0.5) * 1.5)
                rot_z = vis * rot_z_side + (1.0 - vis) * rot_z_nn
            else:
                rot_z = rot_z_nn

            point_rot = np.array([rot_x, rot_y, rot_z])
            point_orig = np.dot(point_rot, best_rotation) + global_center
            final_keypoints[name] = {
                "x": float(point_orig[0]),
                "y": float(point_orig[1]),
                "z": float(point_orig[2]),
            }

        min_y = np.min(points_clean[:, 1])
        max_y = np.max(points_clean[:, 1])
        current_height = max(max_y - min_y, 1.0)
        scaling_factor = real_height_meters / current_height
        print(f"   [SCALE] Height (clean): {current_height:.3f} -> {real_height_meters:.3f}m  | Factor: {scaling_factor:.4f}")
        if scaling_factor > 3.0 or scaling_factor < 0.5:
            print(f"   [SCALE WARNING] Unusual factor {scaling_factor:.2f}!")

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
        # Voxel downsampling: preserves spatial structure better than random subsampling
        n_original = len(np.asarray(pcd.points))
        voxel_size = 0.005  # 5mm voxels
        pcd_down = pcd.voxel_down_sample(voxel_size=voxel_size)
        points_original = np.asarray(pcd_down.points)
        n_down = len(points_original)
        print(f"   [VOXEL] Downsampled: {n_original} -> {n_down} points (voxel={voxel_size}m)")

        if n_down < 1000:
            print("   [VOXEL] Too few points after downsampling, using original")
            points_original = np.asarray(pcd.points)
            n_down = len(points_original)

        if n_down > 100000:
            np.random.seed(42)
            points_original = points_original[np.random.choice(n_down, 100000, replace=False)]
            print("   [VOXEL] Capped to 100k points")

        global_center = np.mean(points_original, axis=0)
        points_centered = points_original - global_center

        # Raw point cloud for response (kept as random subsample of original)
        try:
            raw_pts = np.asarray(pcd.points)
            n_total = len(raw_pts)
            target_pts = 50000
            if n_total > target_pts:
                np.random.seed(42)
                raw_pts = raw_pts[np.random.choice(n_total, target_pts, replace=False)]
            point_cloud_data = raw_pts.astype(float).tolist()
            print(f"   [POINT_CLOUD] Raw for response: {n_total} -> {len(raw_pts)} points")
        except Exception as pc_e:
            print(f"   [POINT_CLOUD WARNING] {pc_e}")
            point_cloud_data = []

        best_score = -999
        best_results = None
        best_rotation = None
        best_params = None
        best_points_rotated = None

        print(f"   [AI] Processing for target height: {real_height_meters}m")

        for RotMat, label in self.get_rotation_matrices():
            points_rotated = np.dot(points_centered, RotMat.T)
            img, params = self.render_snapshot(points_rotated)
            if img is None:
                continue

            cv2.imwrite(os.path.join(DEBUG_DIR, f"debug_{label}.png"), img)
            results = self.pose.process(img)

            if not results.pose_landmarks:
                print(f"      [{label}] No landmarks detected")
                continue

            lms = results.pose_landmarks.landmark
            print(f"      [{label}] Detected {len(lms)} landmarks")

            h_r = np.max(points_rotated[:, 1]) - np.min(points_rotated[:, 1])
            w_r = np.max(points_rotated[:, 0]) - np.min(points_rotated[:, 0])
            aspect = h_r / (w_r + 0.001)

            if aspect > 2.5:
                orient_bonus, orient_txt = 0.6, f"VERY VERTICAL (aspect={aspect:.2f})"
            elif aspect > 2.0:
                orient_bonus, orient_txt = 0.5, f"VERTICAL (aspect={aspect:.2f})"
            elif aspect > 1.5:
                orient_bonus, orient_txt = 0.2, f"Semi-vertical (aspect={aspect:.2f})"
            else:
                orient_bonus, orient_txt = -0.3, f"HORIZONTAL (aspect={aspect:.2f})"

            head_up_bonus, head_txt = self.compute_head_up_score(lms)
            base_score = np.mean([lm.visibility for lm in lms])
            score = base_score + orient_bonus + head_up_bonus

            for idx in [11, 12]:
                lm = lms[idx]
                print(f"         Shoulder {idx}: vis={lm.visibility:.2f}, x={lm.x:.2f}, y={lm.y:.2f}")
            print(f"         {orient_txt}")
            print(f"         {head_txt}")
            print(f"         Score: {score:.3f} (base={base_score:.2f}, orient={orient_bonus:.2f}, head_up={head_up_bonus:.2f})")

            if score > best_score:
                best_score = score
                best_results = results
                best_rotation = RotMat
                best_params = params
                best_points_rotated = points_rotated.copy()

        if best_results is None:
            raise Exception("AI failed: no landmarks detected in any orientation. Try a cleaner scan.")
        if best_score < -1.5:
            raise Exception(f"AI failed (best_score={best_score:.3f}). Try a cleaner scan.")

        print(f"\n   [AI] Best orientation: score={best_score:.3f}")

        points_clean, platform_removed = self.remove_platform_by_spread_jump(best_points_rotated)
        final_keypoints = self.extract_keypoints_from_clean_cloud(points_clean, best_rotation, global_center, real_height_meters)

        final_keypoints["meta"]["platform_removed"] = platform_removed
        final_keypoints["meta"]["best_score"] = float(best_score)
        final_keypoints["point_cloud"] = point_cloud_data

        return final_keypoints
