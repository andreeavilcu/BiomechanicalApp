import numpy as np
import mediapipe as mp
import cv2
import open3d as o3d


class AI_Pose_Estimator:
    def __init__(self):
        print("--> [AI] Initializing Brute-Force Scaling Engine...")
        self.mp_pose = mp.solutions.pose
        self.pose = self.mp_pose.Pose(
            static_image_mode=True,
            model_complexity=2,
            enable_segmentation=False,
            min_detection_confidence=0.3,
        )
        print("   [AI] System Ready.")

    def render_snapshot(self, points, image_size=1024):
        """ Transformă punctele 3D în imagine 2D pentru AI """
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

        valid_mask = (u_px >= 0) & (u_px < image_size) & (v_px >= 0) & (v_px < image_size)
        u_valid = u_px[valid_mask]
        v_valid = v_px[valid_mask]

        if len(u_valid) == 0:
            return None, None

        for i in range(len(u_valid)):
            cv2.circle(img, (u_valid[i], v_valid[i]), 5, (0, 0, 0), -1)

        img = cv2.GaussianBlur(img, (5, 5), 0)

        params = {
            "scale": scale,
            "center_u": center_u,
            "center_v": center_v,
            "image_size": image_size,
        }
        return img, params

    def get_rotation_matrices(self):
        matrices = []
        labels = []

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

        import math

        cos45 = math.cos(math.radians(45))
        sin45 = math.sin(math.radians(45))

        matrices.append(
            np.array([
                [cos45, 0, sin45],
                [0, 1, 0],
                [-sin45, 0, cos45],
            ])
        )
        labels.append("Rot_Y_45")

        matrices.append(
            np.array([
                [cos45, 0, -sin45],
                [0, 1, 0],
                [sin45, 0, cos45],
            ])
        )
        labels.append("Rot_Y_-45")

        return zip(matrices, labels)

    def predict(self, pcd, real_height_meters=1.75):
        points_original = np.asarray(pcd.points)
        global_center = np.mean(points_original, axis=0)
        points_centered = points_original - global_center

        best_score = 0
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
            print(f"      Saved debug_{label}.png for inspection")

            results = self.pose.process(img)
            if results.pose_landmarks:
                lms = results.pose_landmarks.landmark
                print(f"      [{label}] Detected {len(lms)} landmarks")
                for idx in [11, 12]:
                    lm = lms[idx]
                    print(
                        f"         Shoulder {idx}: vis={lm.visibility:.2f}, x={lm.x:.2f}, y={lm.y:.2f}"
                    )

                base_score = np.mean([lm.visibility for lm in lms])

                height_in_rotated_space = np.max(points_rotated[:, 1]) - np.min(points_rotated[:, 1])
                width_in_rotated_space = np.max(points_rotated[:, 0]) - np.min(points_rotated[:, 0])
                aspect_ratio = height_in_rotated_space / (width_in_rotated_space + 0.001)

                if aspect_ratio > 2.0:
                    orientation_bonus = 0.5
                    print(f"         ✅ VERTICAL orientation detected! Aspect={aspect_ratio:.2f}")
                elif aspect_ratio > 1.5:
                    orientation_bonus = 0.2
                    print(f"         ⚠️  Semi-vertical. Aspect={aspect_ratio:.2f}")
                else:
                    orientation_bonus = -0.3
                    print(f"         ❌ HORIZONTAL orientation! Aspect={aspect_ratio:.2f}")

                image_vertical_bonus = 0.1 if (lms[0].y < lms[27].y and lms[0].y < lms[28].y) else 0

                score = base_score + orientation_bonus + image_vertical_bonus

                print(
                    f"         Final score: {score:.3f} (base={base_score:.2f}, orient={orientation_bonus:.2f})"
                )

                if score > best_score:
                    best_score = score
                    best_results = results
                    best_rotation = RotMat
                    best_params = params
                    best_points_rotated = points_rotated

        if not best_results or best_score < 0.4:
            raise Exception("AI could not detect pose.")

        print(f"\n   [AI] Best orientation selected with score: {best_score:.3f}")

        landmarks = best_results.pose_landmarks.landmark
        final_keypoints = {}
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

        res = best_params["image_size"]
        scale = best_params["scale"]
        c_u = best_params["center_u"]
        c_v = best_params["center_v"]

        for name, idx in mapping.items():
            lm = landmarks[idx]
            u_px, v_px = lm.x * res, lm.y * res
            rot_x = (u_px - res / 2) / scale + c_u
            rot_y = c_v - (v_px - res / 2) / scale

            dist_sq = (best_points_rotated[:, 0] - rot_x) ** 2 + (best_points_rotated[:, 1] - rot_y) ** 2
            min_idx = np.argmin(dist_sq)
            rot_z = (
                best_points_rotated[min_idx, 2]
                if dist_sq[min_idx] < 0.01
                else np.mean(best_points_rotated[:, 2])
            )

            point_rot = np.array([rot_x, rot_y, rot_z])
            point_orig = np.dot(point_rot, best_rotation) + global_center

            final_keypoints[name] = {
                "x": float(point_orig[0]),
                "y": float(point_orig[1]),
                "z": float(point_orig[2]),
            }

        min_y = np.min(best_points_rotated[:, 1])
        max_y = np.max(best_points_rotated[:, 1])
        current_height = max_y - min_y

        if current_height <= 0:
            current_height = 1.0

        scaling_factor = real_height_meters / current_height

        print(
            f"   [SCALE] Cloud Height: {current_height:.3f} units -> Target: {real_height_meters:.3f}m"
        )
        print(f"           Factor: {scaling_factor:.4f}")

        for k, v in final_keypoints.items():
            final_keypoints[k] = {
                "x": v["x"] * scaling_factor,
                "y": v["y"] * scaling_factor,
                "z": v["z"] * scaling_factor,
            }

        def mid(p1, p2):
            if p1 in final_keypoints and p2 in final_keypoints:
                return {
                    "x": (final_keypoints[p1]["x"] + final_keypoints[p2]["x"]) / 2,
                    "y": (final_keypoints[p1]["y"] + final_keypoints[p2]["y"]) / 2,
                    "z": (final_keypoints[p1]["z"] + final_keypoints[p2]["z"]) / 2,
                }
            return None

        final_keypoints["neck"] = mid("l_shoulder", "r_shoulder")
        final_keypoints["pelvis"] = mid("l_hip", "r_hip")
        final_keypoints["head"] = final_keypoints.get("nose", final_keypoints.get("neck"))

        final_keypoints["meta"] = {
            "method": "BruteForce_Scaling_v2_VerticalPriority",
            "target_height": real_height_meters,
            "scaling_factor": scaling_factor,
            "best_score": best_score,
        }

        return {k: v for k, v in final_keypoints.items() if v is not None}