import requests
import open3d as o3d
import numpy as np
import os

SERVER_URL = 'http://127.0.0.1:5000/process-scan'
FILE_PATH = 'test.ply'
TARGET_HEIGHT_CM = 180


def create_sphere_at_xyz(xyz, color=[1, 0, 0], radius=0.04):
    sphere = o3d.geometry.TriangleMesh.create_sphere(radius=radius)
    sphere.paint_uniform_color(color)
    sphere.translate(xyz)
    return sphere


def create_skeleton_lines(keypoints):
    points = []
    connections = [
        ("head", "neck"),
        ("head", "l_ear"), ("head", "r_ear"),
        ("neck", "l_shoulder"), ("neck", "r_shoulder"),
        ("l_shoulder", "r_shoulder"),
        ("neck", "pelvis"),
        ("pelvis", "l_hip"), ("pelvis", "r_hip"),
        ("l_hip", "l_knee"), ("r_hip", "r_knee"),
        ("l_knee", "l_ankle"), ("r_knee", "r_ankle"),
    ]

    name_to_index = {}
    current_index = 0

    for name, data in keypoints.items():
        if name in ["meta", "method"]:
            continue
        points.append([data['x'], data['y'], data['z']])
        name_to_index[name] = current_index
        current_index += 1

    line_indices = []
    for start, end in connections:
        if start in name_to_index and end in name_to_index:
            line_indices.append([name_to_index[start], name_to_index[end]])

    if not line_indices:
        return None

    line_set = o3d.geometry.LineSet()
    line_set.points = o3d.utility.Vector3dVector(points)
    line_set.lines = o3d.utility.Vector2iVector(line_indices)
    line_set.paint_uniform_color([0, 1, 0])
    return line_set


def main():
    print("--- 3D SKELETON VISUALIZATION ---")
    print(f"File: {FILE_PATH}, Height: {TARGET_HEIGHT_CM} cm")

    if not os.path.exists(FILE_PATH):
        print(f"File not found: {FILE_PATH}")
        return

    print("1. Sending scan to AI...")
    try:
        with open(FILE_PATH, 'rb') as f:
            response = requests.post(
                SERVER_URL, files={'file': f}, data={'height': TARGET_HEIGHT_CM}
            )
    except Exception as e:
        print(f"Server error: {e}")
        return

    if response.status_code != 200:
        print("Server error:", response.text)
        return

    try:
        data = response.json()
    except Exception as e:
        print(f"JSON parse error: {e}")
        return

    if "error" in data:
        print(f"AI error: {data['error']}")
        return

    print(f"AI responded! Method: {data.get('meta', {}).get('method', 'Unknown')}")

    print("2. Preparing 3D scene...")
    pcd = o3d.io.read_point_cloud(FILE_PATH)
    pcd.paint_uniform_color([0.8, 0.8, 0.8])

    points_arr = np.asarray(pcd.points)
    cloud_height = np.max(points_arr[:, 2]) - np.min(points_arr[:, 2])
    if cloud_height <= 0:
        ranges = np.max(points_arr, axis=0) - np.min(points_arr, axis=0)
        cloud_height = np.max(ranges)

    target_height_meters = TARGET_HEIGHT_CM / 100.0
    scale_factor = target_height_meters / cloud_height
    print(f"   Cloud height: {cloud_height:.3f} → scale: {scale_factor:.4f}")
    pcd.scale(scale_factor, center=pcd.get_center())

    pcd = pcd.voxel_down_sample(voxel_size=0.015)
    print(f"   Points after downsample: {len(pcd.points)}")

    geometries = [pcd]

    print("3. Generating joints...")
    for key, val in data.items():
        if key == "meta":
            continue
        xyz = [val['x'], val['y'], val['z']]
        color = [0, 0, 1] if "ear" in key else ([1, 0.5, 0] if "hip" in key else [1, 0, 0])
        geometries.append(create_sphere_at_xyz(xyz, color=color, radius=0.03))

    print("4. Generating bones...")
    skeleton = create_skeleton_lines(data)
    if skeleton:
        geometries.append(skeleton)

    print("5. Opening 3D window...")
    o3d.visualization.draw_geometries(
        geometries,
        window_name=f"Biomechanics - {FILE_PATH} ({TARGET_HEIGHT_CM}cm)",
        width=1024,
        height=768,
    )


if __name__ == "__main__":
    main()