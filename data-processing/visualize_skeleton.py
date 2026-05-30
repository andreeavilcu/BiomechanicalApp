import sys
import os
import open3d as o3d
import numpy as np

from model_loader import AI_Pose_Estimator

FILE_PATH = sys.argv[1] if len(sys.argv) > 1 else "test.ply"
TARGET_HEIGHT_CM = float(sys.argv[2]) if len(sys.argv) > 2 else 180.0

KEYPOINT_KEYS = {
    "nose", "head", "l_ear", "r_ear", "neck",
    "l_shoulder", "r_shoulder", "l_hip", "r_hip",
    "pelvis", "l_knee", "r_knee", "l_ankle", "r_ankle",
}

KEYPOINT_COLORS = {
    "nose":       [0.2, 0.6, 1.0],
    "head":       [0.2, 0.6, 1.0],
    "l_ear":      [0.0, 0.4, 0.9],
    "r_ear":      [0.0, 0.4, 0.9],
    "neck":       [0.9, 0.9, 0.0],
    "l_shoulder": [1.0, 0.5, 0.0],
    "r_shoulder": [1.0, 0.5, 0.0],
    "l_hip":      [0.2, 0.9, 0.2],
    "r_hip":      [0.2, 0.9, 0.2],
    "pelvis":     [0.0, 0.7, 0.0],
    "l_knee":     [0.8, 0.0, 0.8],
    "r_knee":     [0.8, 0.0, 0.8],
    "l_ankle":    [1.0, 0.0, 0.0],
    "r_ankle":    [1.0, 0.0, 0.0],
}

CONNECTIONS = [
    ("head", "neck"),
    ("head", "l_ear"), ("head", "r_ear"),
    ("neck", "l_shoulder"), ("neck", "r_shoulder"),
    ("l_shoulder", "r_shoulder"),
    ("neck", "pelvis"),
    ("pelvis", "l_hip"), ("pelvis", "r_hip"),
    ("l_hip", "l_knee"), ("r_hip", "r_knee"),
    ("l_knee", "l_ankle"), ("r_knee", "r_ankle"),
]


def create_sphere(xyz, color, radius=0.03):
    sphere = o3d.geometry.TriangleMesh.create_sphere(radius=radius)
    sphere.paint_uniform_color(color)
    sphere.translate(xyz)
    return sphere


def create_skeleton_lines(keypoints):
    name_to_idx = {}
    pts = []
    for i, name in enumerate(kp for kp in keypoints if kp in KEYPOINT_KEYS):
        pts.append([keypoints[name]["x"], keypoints[name]["y"], keypoints[name]["z"]])
        name_to_idx[name] = i

    lines = [
        [name_to_idx[a], name_to_idx[b]]
        for a, b in CONNECTIONS
        if a in name_to_idx and b in name_to_idx
    ]
    if not lines:
        return None

    ls = o3d.geometry.LineSet()
    ls.points = o3d.utility.Vector3dVector(pts)
    ls.lines = o3d.utility.Vector2iVector(lines)
    ls.paint_uniform_color([0.0, 1.0, 0.0])
    return ls


def main():
    print("--- 3D SKELETON VISUALIZATION (standalone) ---")
    print(f"File: {FILE_PATH}  |  Height: {TARGET_HEIGHT_CM} cm")

    if not os.path.exists(FILE_PATH):
        print(f"ERROR: File not found: {FILE_PATH}")
        sys.exit(1)

    print("\n[1/3] Loading and processing scan...")
    pcd = o3d.io.read_point_cloud(FILE_PATH)
    if pcd.is_empty():
        print("ERROR: Empty or corrupt .ply file")
        sys.exit(1)

    pcd, _ = pcd.remove_statistical_outlier(nb_neighbors=30, std_ratio=3.0)

    ai = AI_Pose_Estimator()
    keypoints = ai.predict(pcd, real_height_meters=TARGET_HEIGHT_CM / 100.0)

    if "error" in keypoints:
        print(f"ERROR: {keypoints['error']}")
        sys.exit(1)

    meta = keypoints.get("meta", {})
    print(f"\n[2/3] Results:")
    print(f"   Method       : {meta.get('method', 'unknown')}")
    print(f"   Side view    : {meta.get('side_view_used', False)}")
    print(f"   Platform rm  : {meta.get('platform_removed', False)}")
    print(f"   Best score   : {meta.get('best_score', '?'):.3f}")
    print(f"   Scale factor : {meta.get('scaling_factor', '?'):.4f}")
    print()

    for name in sorted(KEYPOINT_KEYS):
        if name in keypoints:
            kp = keypoints[name]
            print(f"   {name:<14} x={kp['x']:+.3f}  y={kp['y']:+.3f}  z={kp['z']:+.3f}")

    print("\n[3/3] Opening 3D window...")
    print("   Controls: left-drag=rotate, scroll=zoom, right-drag=pan, Q=quit")

    pcd_vis = o3d.io.read_point_cloud(FILE_PATH)
    pcd_vis.paint_uniform_color([0.75, 0.75, 0.75])
    pts_arr = np.asarray(pcd_vis.points)
    cloud_height = np.max(pts_arr[:, 2]) - np.min(pts_arr[:, 2])
    if cloud_height > 0:
        sf = (TARGET_HEIGHT_CM / 100.0) / cloud_height
        pcd_vis.scale(sf, center=pcd_vis.get_center())
    pcd_vis = pcd_vis.voxel_down_sample(voxel_size=0.015)

    geometries = [pcd_vis]

    for name, kp in keypoints.items():
        if name not in KEYPOINT_KEYS:
            continue
        xyz = [kp["x"], kp["y"], kp["z"]]
        color = KEYPOINT_COLORS.get(name, [1.0, 0.0, 0.0])
        geometries.append(create_sphere(xyz, color))

    skeleton = create_skeleton_lines(keypoints)
    if skeleton:
        geometries.append(skeleton)

    o3d.visualization.draw_geometries(
        geometries,
        window_name=f"Biomechanics — {os.path.basename(FILE_PATH)} ({TARGET_HEIGHT_CM:.0f}cm)",
        width=1280,
        height=800,
    )


if __name__ == "__main__":
    main()
