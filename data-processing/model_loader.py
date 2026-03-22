import numpy as np
import mediapipe as mp
import cv2
import open3d as o3d


class AI_Pose_Estimator:
    def __init__(self):
        print("--> [AI] Initializing Brute-Force Scaling Engine v6...")
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
        print(f"   [PLATFORM] Detected at {', '.join(loc)} → removed {pct:.1f}% of points.")
        return cleaned, True

    def render_snapshot(self, points, image_size=1024):
        u_coords = points[:, 0]
        v_coords = points[:, 1]

        min_u, max_u = np.min(u_coords), np.max(u_coords)
        min_v, max_v = np.min(v_coords), np.max(v_coords)

        span_u = max_u - min_u
        span_v = max_v - min_v
        max_span = max(span_u, span_v)
        if max_span == 0:
            max_span = 1.0

        scale = (image_size * 0.85) / max_span
        center_u = (min_u + max_u) / 2
        center_v = (min_v + max_v) / 2

        u_px = ((u_coords - center_u) * scale + image_size / 2).astype(int)
        v_px = (image_size / 2 - (v_coords - center_v) * scale).astype(int)

        img = np.full((image_size, image_size, 3), 255, dtype=np.uint8)
        valid = (u_px >= 0) & (u_px < image_size) & (v_px >= 0) & (v_px < image_size)
        for u, v in zip(u_px[valid], v_px[valid]):
            cv2.circle(img, (int(u), int(v)), 5, (0, 0, 0), -1)
        img = cv2.GaussianBlur(img, (5, 5), 0)

        params = {"scale": scale, "center_u": center_u, "center_v": center_v, "image_size": image_size}
        return img, params

    def get_rotation_matrices(self):
        matrices, labels = [], []
        matrices.append(np.eye(3))
        labels.append("Original")
        matrices.append(np.array([[1, 0, 0], [0, 0, -1], [0, 1, 0]]))
        labels.append("Rot_X_90")
        matrices.append(np.array([[1, 0, 0], [0, 0, 1], [0, -1, 0]]))
        labels.append("Rot_X_-90")
        matrices.append(np.array([[0, 0, 1], [0, 1, 0], [-1, 0, 0]]))
        labels.append("Rot_Y_90")
        matrices.append(np.array([[0, -1, 0], [1, 0, 0], [0, 0, 1]]))
        labels.append("Rot_Z_90")
        matrices.append(np.array([[1, 0, 0], [0, -1, 0], [0, 0, -1]]))
        labels.append("Rot_X_180")
        matrices.append(np.array([[-1, 0, 0], [0, 1, 0], [0, 0, -1]]))
        labels.append("Rot_Z_180")
        import math
        c, s = math.cos(math.radians(45)), math.sin(math.radians(45))
        matrices.append(np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]]))
        labels.append("Rot_Y_45")
        matrices.append(np.array([[c, 0, -s], [0, 1, 0], [s, 0, c]]))
        labels.append("Rot_Y_-45")
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
        img_clean, params_clean = self.render_snapshot(points_clean)
        if img_clean is None:
            raise Exception("Cannot render cleaned cloud.")

        cv2.imwrite("debug_CLEAN.png", img_clean)

        results_clean = self.pose.process(img_clean)
        if not results_clean or not results_clean.pose_landmarks:
            raise Exception("MediaPipe failed on cleaned cloud image.")

        landmarks = results_clean.pose_landmarks.landmark
        print(f"   [KEYPOINTS] Re-detected on clean image: {len(landmarks)} landmarks")

        mapping = {
            "nose": 0,
            "l_ear": 7,
            "r_ear": 8,
            "l_shoulder": 11,
            "r_shoulder": 12,
            "l_hip": 23,
            "r_hip": 24,
            "l_knee": 25,
            "r_knee": 26,
            "l_ankle": 27,
            "r_ankle": 28,
        }

        res = params_clean["image_size"]
        scale = params_clean["scale"]
        c_u = params_clean["center_u"]
        c_v = params_clean["center_v"]

        final_keypoints = {}
        for name, idx in mapping.items():
            lm = landmarks[idx]
            u_px = lm.x * res
            v_px = lm.y * res

            rot_x = (u_px - res / 2) / scale + c_u
            rot_y = c_v - (v_px - res / 2) / scale

            dist_sq = ((points_clean[:, 0] - rot_x) ** 2 + (points_clean[:, 1] - rot_y) ** 2)
            min_idx = np.argmin(dist_sq)

            SEARCH_RADIUS_SQ = 0.05
            nearby_mask = dist_sq < SEARCH_RADIUS_SQ
            if np.any(nearby_mask):
                rot_z = np.median(points_clean[nearby_mask, 2])
            else:
                nearest_10 = np.argsort(dist_sq)[:10]
                rot_z = np.median(points_clean[nearest_10, 2])

            point_rot = np.array([rot_x, rot_y, rot_z])
            point_orig = np.dot(point_rot, best_rotation) + global_center

            final_keypoints[name] = {"x": float(point_orig[0]), "y": float(point_orig[1]), "z": float(point_orig[2])}

        min_y = np.min(points_clean[:, 1])
        max_y = np.max(points_clean[:, 1])
        current_height = max_y - min_y
        if current_height <= 0:
            current_height = 1.0

        scaling_factor = real_height_meters / current_height
        print(f"   [SCALE] Height (clean): {current_height:.3f} → {real_height_meters:.3f}m  | Factor: {scaling_factor:.4f}")

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
        final_keypoints["head"] = final_keypoints.get("nose", final_keypoints.get("neck"))

        final_keypoints["meta"] = {"method": "BruteForce_v6_CleanReproject", "target_height": real_height_meters, "scaling_factor": scaling_factor}

        return {k: v for k, v in final_keypoints.items() if v is not None}

    def predict(self, pcd, real_height_meters=1.75):
        points_original = np.asarray(pcd.points)
        global_center = np.mean(points_original, axis=0)
        points_centered = points_original - global_center

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

            cv2.imwrite(f"debug_{label}.png", img)
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

        if best_results is None or best_score < 0.3:
            raise Exception(f"AI failed (best_score={best_score:.3f}). Try a cleaner scan.")

        print(f"\n   [AI] Best orientation: score={best_score:.3f}")

        points_clean, platform_removed = self.remove_platform_by_spread_jump(best_points_rotated)

        final_keypoints = self.extract_keypoints_from_clean_cloud(points_clean, best_rotation, global_center, real_height_meters)

        final_keypoints["meta"]["platform_removed"] = platform_removed
        final_keypoints["meta"]["best_score"] = best_score

        return final_keypoints