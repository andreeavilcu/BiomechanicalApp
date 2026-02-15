import requests
import open3d as o3d
import numpy as np
import os

SERVER_URL = 'http://127.0.0.1:5000/process-scan'
FILE_PATH = 'test.ply'
TARGET_HEIGHT_CM = 200

def create_sphere_at_xyz(xyz, color=[1, 0, 0], radius=0.04):
    sphere = o3d.geometry.TriangleMesh.create_sphere(radius=radius)
    sphere.paint_uniform_color(color)
    sphere.translate(xyz)
    return sphere

def create_skeleton_lines(keypoints):
    lines = []
    points = []

    connections = [
        ("head", "neck"),
        ("head", "l_ear"), ("head", "r_ear"),
        ("neck", "l_shoulder"), ("neck", "r_shoulder"),
        ("l_shoulder", "r_shoulder"),
        ("neck", "pelvis"),
        ("pelvis", "l_hip"), ("pelvis", "r_hip"),
        ("l_hip", "l_knee"), ("r_hip", "r_knee"),
        ("l_knee", "l_ankle"), ("r_knee", "r_ankle")
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
    print(f"Target Height: {TARGET_HEIGHT_CM} cm")

    if not os.path.exists(FILE_PATH):
        print(f"File not found: {FILE_PATH}")
        return

    print("1. Sending scan to AI...")
    try:
        with open(FILE_PATH, 'rb') as f:
            payload = {'height': TARGET_HEIGHT_CM}
            files = {'file': f}
            response = requests.post(SERVER_URL, files=files, data=payload)
    except Exception as e:
        print(f"Server error: {e}")
        return

    if response.status_code != 200:
        print("Server returned error:", response.text)
        return

    try:
        data = response.json()
    except Exception as e:
        print(f"Error parsing JSON response: {e}")
        print(f"Response text: {response.text}")
        return

    if data is None:
        print("Error: Server returned empty response")
        return

    print(f"AI responded! Method: {data.get('meta', {}).get('method', 'Unknown')}")

    print("2. Preparing 3D scene...")
    pcd = o3d.io.read_point_cloud(FILE_PATH)
    pcd.paint_uniform_color([0.8, 0.8, 0.8])

    points = np.asarray(pcd.points)
    ranges = np.max(points, axis=0) - np.min(points, axis=0)
    current_height = np.max(ranges)

    target_height_meters = TARGET_HEIGHT_CM / 100.0

    if current_height > 0:
        scale_factor = target_height_meters / current_height
        print(f"   Rescaling local cloud by {scale_factor:.4f} to match skeleton...")
        pcd.scale(scale_factor, center=pcd.get_center())

    print(f"   Original points: {len(pcd.points)}")
    pcd = pcd.voxel_down_sample(voxel_size=0.015)
    print(f"   Points after optimization: {len(pcd.points)}")

    geometries = [pcd]

    print("3. Generating joints...")
    for key, val in data.items():
        if key == "meta":
            continue
        xyz = [val['x'], val['y'], val['z']]
        color = [1, 0, 0]
        if "ear" in key:
            color = [0, 0, 1]
        elif "hip" in key:
            color = [1, 0.5, 0]

        geometries.append(create_sphere_at_xyz(xyz, color=color, radius=0.03))

    print("4. Generating bones...")
    skeleton = create_skeleton_lines(data)
    if skeleton:
        geometries.append(skeleton)

    print("5. Opening window...")
    o3d.visualization.draw_geometries(
        geometries, window_name=f"Result ({TARGET_HEIGHT_CM}cm)", width=1024, height=768
    )


if __name__ == "__main__":
    main()
import requests
import json
import open3d as o3d
import numpy as np
import os

# --- CONFIGURARE ---
SERVER_URL = 'http://127.0.0.1:5000/process-scan'
FILE_PATH = 'test.ply'  # Numele fișierului tău
TARGET_HEIGHT_CM = 200         # AICI scrii înălțimea persoanei (2m = 200cm)

def create_sphere_at_xyz(xyz, color=[1, 0, 0], radius=0.04):
    """ Creează o sferă (articulație) """
    sphere = o3d.geometry.TriangleMesh.create_sphere(radius=radius)
    sphere.paint_uniform_color(color)
    sphere.translate(xyz)
    return sphere

def create_skeleton_lines(keypoints):
    """ Desenează oasele între puncte """
    lines = []
    points = []
    
    # Definire conexiuni anatomice
    connections = [
        ("head", "neck"),
        ("head", "l_ear"), ("head", "r_ear"),
        ("neck", "l_shoulder"), ("neck", "r_shoulder"),
        ("l_shoulder", "r_shoulder"),
        ("neck", "pelvis"),
        ("pelvis", "l_hip"), ("pelvis", "r_hip"),
        ("l_hip", "l_knee"), ("r_hip", "r_knee"),
        ("l_knee", "l_ankle"), ("r_knee", "r_ankle")
    ]

    name_to_index = {}
    current_index = 0

    # Extragem punctele valide
    for name, data in keypoints.items():
        if name in ["meta", "method"]: continue 
        points.append([data['x'], data['y'], data['z']])
        name_to_index[name] = current_index
        current_index += 1

    # Creăm liniile
    line_indices = []
    for start, end in connections:
        if start in name_to_index and end in name_to_index:
            line_indices.append([name_to_index[start], name_to_index[end]])

    if not line_indices: return None

    line_set = o3d.geometry.LineSet()
    line_set.points = o3d.utility.Vector3dVector(points)
    line_set.lines = o3d.utility.Vector2iVector(line_indices)
    line_set.paint_uniform_color([0, 1, 0]) # Verde pentru oase
    return line_set

def main():
    print(f"--- 3D SKELETON VISUALIZATION ---")
    print(f"Target Height: {TARGET_HEIGHT_CM} cm")
    
    if not os.path.exists(FILE_PATH):
        print(f"Nu găsesc fișierul '{FILE_PATH}'.")
        return

    # 1. Trimitem fișierul + ÎNĂLȚIMEA la server
    print("1. Sending scan to AI...")
    try:
        with open(FILE_PATH, 'rb') as f:
            payload = {'height': TARGET_HEIGHT_CM}
            files = {'file': f}
            response = requests.post(SERVER_URL, files=files, data=payload)
    except Exception as e:
        print(f"Server error: {e}")
        return

    if response.status_code != 200:
        print("Server returned error:", response.text)
        return

    try:
        data = response.json()
    except Exception as e:
        print(f"Error parsing JSON response: {e}")
        print(f"Response text: {response.text}")
        return
    
    if data is None:
        print("Error: Server returned empty response")
        return
    
    print(f"AI responded! Method: {data.get('meta', {}).get('method', 'Unknown')}")
    
    # 2. Pregătim scena 3D
    print("2. Preparing 3D scene...")
    pcd = o3d.io.read_point_cloud(FILE_PATH)
    pcd.paint_uniform_color([0.8, 0.8, 0.8]) 
    
    # --- SCALARE LOCALĂ ---
    points = np.asarray(pcd.points)
    ranges = np.max(points, axis=0) - np.min(points, axis=0)
    current_height = np.max(ranges) 
    
    target_height_meters = TARGET_HEIGHT_CM / 100.0
    
    if current_height > 0:
        scale_factor = target_height_meters / current_height
        print(f"   Rescaling local cloud by {scale_factor:.4f} to match skeleton...")
        pcd.scale(scale_factor, center=pcd.get_center())

    # --- OPTIMIZARE VITEZĂ (DOWNSAMPLING) ---
    print(f"   Original points: {len(pcd.points)}")
    
    # voxel_size=0.015 înseamnă că păstrăm un singur punct la fiecare 1.5 cm.
    # Dacă tot se mișcă greu, schimbă 0.015 cu 0.03
    pcd = pcd.voxel_down_sample(voxel_size=0.015)
    
    print(f"   Points after optimization: {len(pcd.points)}")
    # ----------------------------------------

    geometries = [pcd]
    
    # 3. Generăm articulațiile
    print("3. Generating joints...")
    for key, val in data.items():
        if key == "meta": continue
        xyz = [val['x'], val['y'], val['z']]
        
        color = [1, 0, 0] 
        if "ear" in key: color = [0, 0, 1]
        elif "hip" in key: color = [1, 0.5, 0]

        geometries.append(create_sphere_at_xyz(xyz, color=color, radius=0.03)) 

    # 4. Generăm oasele
    print("4. Generating bones...")
    skeleton = create_skeleton_lines(data)
    if skeleton: geometries.append(skeleton)

    print("5. Opening window...")
    o3d.visualization.draw_geometries(geometries, 
                                      window_name=f"Result ({TARGET_HEIGHT_CM}cm)", 
                                      width=1024, height=768)

if __name__ == "__main__":
    main()