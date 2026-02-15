import open3d as o3d
import numpy as np
import os
import sys
import tempfile
from flask import Flask, request, jsonify

from model_loader import AI_Pose_Estimator

app = Flask(__name__)

print("INIT: Loading AI system...")
ai_engine = AI_Pose_Estimator()

def get_heuristic_keypoints(pcd):
    points = np.asarray(pcd.points)
    min_z, max_z = np.min(points[:, 2]), np.max(points[:, 2])
    center = np.mean(points, axis=0)
    h = max_z - min_z

    return {
        "head": {"x": float(center[0]), "y": float(center[1]), "z": float(max_z - h * 0.05)},
        "l_ear": {"x": float(center[0] - 0.05), "y": float(center[1]), "z": float(max_z - h * 0.08)},
        "r_ear": {"x": float(center[0] + 0.05), "y": float(center[1]), "z": float(max_z - h * 0.08)},
        "neck": {"x": float(center[0]), "y": float(center[1]), "z": float(max_z - h * 0.15)},
        "l_shoulder": {"x": float(center[0] - 0.15), "y": float(center[1]), "z": float(max_z - h * 0.18)},
        "r_shoulder": {"x": float(center[0] + 0.15), "y": float(center[1]), "z": float(max_z - h * 0.18)},
        "l_hip": {"x": float(center[0] - 0.1), "y": float(center[1]), "z": float(min_z + h * 0.5)},
        "r_hip": {"x": float(center[0] + 0.1), "y": float(center[1]), "z": float(min_z + h * 0.5)},
        "pelvis": {"x": float(center[0]), "y": float(center[1]), "z": float(min_z + h * 0.5)},
        "l_knee": {"x": float(center[0] - 0.1), "y": float(center[1] + 0.1), "z": float(min_z + h * 0.25)},
        "r_knee": {"x": float(center[0] + 0.1), "y": float(center[1] + 0.1), "z": float(min_z + h * 0.25)},
        "l_ankle": {"x": float(center[0] - 0.1), "y": float(center[1]), "z": float(min_z + 0.05)},
        "r_ankle": {"x": float(center[0] + 0.1), "y": float(center[1]), "z": float(min_z + 0.05)},
        "meta": {"method": "Heuristic_Fallback"},
    }

def adaptive_clustering(pcd):
    points = np.asarray(pcd.points)
    num_points = len(points)

    print(f"CLUSTER: Adaptive clustering for {num_points:,} points")

    configs = [
        {"eps": 0.05, "min_pts": 50, "name": "Dense"},
        {"eps": 0.08, "min_pts": 30, "name": "Medium"},
        {"eps": 0.12, "min_pts": 20, "name": "Sparse"},
        {"eps": 0.18, "min_pts": 15, "name": "Very sparse"},
    ]

    best_cluster = None
    best_score = -1

    for config in configs:
        labels = np.array(
            pcd.cluster_dbscan(eps=config["eps"], min_points=config["min_pts"], print_progress=False)
        )

        if labels.max() < 0:
            print(f"  {config['name']}: No clusters found")
            continue

        cluster_sizes = []
        for i in range(labels.max() + 1):
            size = np.sum(labels == i)
            cluster_sizes.append((i, size))

        cluster_sizes.sort(key=lambda x: x[1], reverse=True)
        largest_id, largest_size = cluster_sizes[0]

        candidate = pcd.select_by_index(np.where(labels == largest_id)[0])
        extent = candidate.get_axis_aligned_bounding_box().get_extent()
        height = extent[2]

        if not (0.5 < height < 2.5):
            print(f"  {config['name']}: Invalid height {height:.2f} m")
            continue

        coverage = largest_size / num_points
        height_validity = 1.0 if (1.4 < height < 2.0) else 0.7
        score = coverage * height_validity

        print(f"  {config['name']}: {largest_size:,} pts, H={height:.2f} m, Score={score:.3f}")

        if score > best_score:
            best_score = score
            best_cluster = candidate

    if best_cluster is None:
        print("CLUSTER WARNING: All clustering attempts failed. Using entire cloud.")
        return pcd

    final_extent = best_cluster.get_axis_aligned_bounding_box().get_extent()
    print(f"CLUSTER SUCCESS: Selected cluster {len(np.asarray(best_cluster.points)):,} points")
    print(f"Dimensions: {final_extent[0]:.2f} x {final_extent[1]:.2f} x {final_extent[2]:.2f} m")

    return best_cluster

def process_scan(file_path, user_height=1.75):
    try:
        print("PROCESSING: ", os.path.basename(file_path))
        print(f"  Target Height: {user_height} m")

        pcd = o3d.io.read_point_cloud(file_path)
        if pcd.is_empty():
            return {"error": "Empty or corrupt file"}

        pcd, _ = pcd.remove_statistical_outlier(nb_neighbors=30, std_ratio=3.0)

    except Exception as e:
        return {"error": f"Loading error: {e}"}

    human_cluster = pcd

    try:
        print("AI: Running inference...")
        keypoints = ai_engine.predict(human_cluster, real_height_meters=user_height)
        return keypoints

    except Exception as e:
        print(f"AI FAILED: {e}")
        return {"error": str(e)}

@app.route('/process-scan', methods=['POST'])
def api_endpoint():
    if 'file' not in request.files:
        return jsonify({"error": "No file provided"}), 400

    file = request.files['file']

    try:
        height_cm = float(request.form.get('height', 175))
    except ValueError:
        height_cm = 175.0

    user_height_meters = height_cm / 100.0

    if file.filename == '':
        return jsonify({"error": "Empty filename"}), 400

    with tempfile.NamedTemporaryFile(delete=False, suffix=".ply") as tmp:
        file.save(tmp.name)
        path = tmp.name

    result = process_scan(path, user_height=user_height_meters)
    os.remove(path)

    return jsonify(result)

if __name__ == '__main__':
    print("Server starting at http://127.0.0.1:5000/process-scan")
    app.run(host='0.0.0.0', port=5000, debug=True)

app = Flask(__name__)

print(">>> [INIT] Loading FIXED AI System...")
ai_engine = AI_Pose_Estimator()

def get_heuristic_keypoints(pcd):
    """
    PLAN B: Mathematical estimation in case the AI fails.
    """
    points = np.asarray(pcd.points)
    min_z, max_z = np.min(points[:, 2]), np.max(points[:, 2])
    center = np.mean(points, axis=0)
    h = max_z - min_z
    
    return {
        "head":       {"x": float(center[0]), "y": float(center[1]), "z": float(max_z - h*0.05)},
        "l_ear":      {"x": float(center[0]-0.05), "y": float(center[1]), "z": float(max_z - h*0.08)},
        "r_ear":      {"x": float(center[0]+0.05), "y": float(center[1]), "z": float(max_z - h*0.08)},
        "neck":       {"x": float(center[0]), "y": float(center[1]), "z": float(max_z - h*0.15)},
        "l_shoulder": {"x": float(center[0]-0.15), "y": float(center[1]), "z": float(max_z - h*0.18)},
        "r_shoulder": {"x": float(center[0]+0.15), "y": float(center[1]), "z": float(max_z - h*0.18)},
        "l_hip":      {"x": float(center[0]-0.1), "y": float(center[1]), "z": float(min_z + h*0.5)},
        "r_hip":      {"x": float(center[0]+0.1), "y": float(center[1]), "z": float(min_z + h*0.5)},
        "pelvis":     {"x": float(center[0]), "y": float(center[1]), "z": float(min_z + h*0.5)},
        "l_knee":     {"x": float(center[0]-0.1), "y": float(center[1]+0.1), "z": float(min_z + h*0.25)},
        "r_knee":     {"x": float(center[0]+0.1), "y": float(center[1]+0.1), "z": float(min_z + h*0.25)},
        "l_ankle":    {"x": float(center[0]-0.1), "y": float(center[1]), "z": float(min_z + 0.05)},
        "r_ankle":    {"x": float(center[0]+0.1), "y": float(center[1]), "z": float(min_z + 0.05)},
        "meta":       {"method": "Heuristic_Fallback"}
    }

def adaptive_clustering(pcd):
    """
    CRITICAL FIX: Adaptive clustering for scans of varying quality.
    New strategy:
    1. Try multiple eps configurations
    2. Validate the cluster's dimensions
    3. Return the best candidate
    """
    points = np.asarray(pcd.points)
    num_points = len(points)
    
    print(f"\n   [CLUSTER] Adaptive clustering for {num_points:,} points...")
    
    configs = [
        {"eps": 0.05, "min_pts": 50, "name": "Dense (LiDAR quality)"},
        {"eps": 0.08, "min_pts": 30, "name": "Medium (Good photogrammetry)"},
        {"eps": 0.12, "min_pts": 20, "name": "Sparse (Basic photogrammetry)"},
        {"eps": 0.18, "min_pts": 15, "name": "Very sparse (Low quality)"}
    ]
    
    best_cluster = None
    best_score = -1
    
    for config in configs:
        labels = np.array(pcd.cluster_dbscan(
            eps=config["eps"], 
            min_points=config["min_pts"], 
            print_progress=False
        ))
        
        if labels.max() < 0:
            print(f"      {config['name']}: No clusters found")
            continue
        
        cluster_sizes = []
        for i in range(labels.max() + 1):
            size = np.sum(labels == i)
            cluster_sizes.append((i, size))
        
        cluster_sizes.sort(key=lambda x: x[1], reverse=True)
        largest_id, largest_size = cluster_sizes[0]
        
        candidate = pcd.select_by_index(np.where(labels == largest_id)[0])
        extent = candidate.get_axis_aligned_bounding_box().get_extent()
        height = extent[2]
        
        if not (0.5 < height < 2.5):
            print(f"      {config['name']}: Invalid height {height:.2f}m")
            continue
        
        coverage = largest_size / num_points
        height_validity = 1.0 if (1.4 < height < 2.0) else 0.7
        score = coverage * height_validity
        
        print(f"      {config['name']}: {largest_size:,} pts ({coverage*100:.1f}%), H={height:.2f}m, Score={score:.3f}")
        
        if score > best_score:
            best_score = score
            best_cluster = candidate
    
    if best_cluster is None:
        print(f"   [CLUSTER WARNING] All clustering attempts failed. Using entire cloud.")
        return pcd
    
    final_extent = best_cluster.get_axis_aligned_bounding_box().get_extent()
    print(f"   [CLUSTER SUCCESS] Selected cluster: {len(np.asarray(best_cluster.points)):,} points")
    print(f"                     Dimensions: {final_extent[0]:.2f} x {final_extent[1]:.2f} x {final_extent[2]:.2f} m")
    
    return best_cluster

def process_scan(file_path, user_height=1.75):
    try:
        print(f"\n{'='*70}")
        print(f"[PROCESSING] {os.path.basename(file_path)}")
        print(f"             Target Height: {user_height} m")
        print(f"{'='*70}")
        
        pcd = o3d.io.read_point_cloud(file_path)
        if pcd.is_empty(): 
            return {"error": "Empty or corrupt file"}

        pcd, _ = pcd.remove_statistical_outlier(nb_neighbors=30, std_ratio=3.0)

    except Exception as e:
        return {"error": f"Loading error: {e}"}
    
    # CLUSTERING (optional, if there's a lot of noise around)
    # human_cluster = adaptive_clustering(pcd)
    # If adaptive_clustering is complex,  pass pcd directly if the scan is clean
    human_cluster = pcd 
    
    try:
        print(f"\n{'='*70}")
        print("   [AI] Running BRUTE FORCE inference...")
        
        keypoints = ai_engine.predict(human_cluster, real_height_meters=user_height)
        
        return keypoints

    except Exception as e:
        print(f"\n   [AI FAILED] {e}")
        return {"error": str(e)}

@app.route('/process-scan', methods=['POST'])
def api_endpoint():
    if 'file' not in request.files: 
        return jsonify({"error": "No file provided"}), 400
    
    file = request.files['file']
    
    try:
        height_cm = float(request.form.get('height', 175))
    except ValueError:
        height_cm = 175.0
        
    user_height_meters = height_cm / 100.0 
    
    if file.filename == '':
        return jsonify({"error": "Empty filename"}), 400
    
    with tempfile.NamedTemporaryFile(delete=False, suffix=".ply") as tmp:
        file.save(tmp.name)
        path = tmp.name
    
    result = process_scan(path, user_height=user_height_meters)
    os.remove(path)
    
    return jsonify(result)

if __name__ == '__main__':
    print("\n" + "="*70)
    print(" 🚀 BIOMECHANICAL POSE ESTIMATION SERVER")
    print("="*70)
    print(f" Endpoint: http://127.0.0.1:5000/process-scan")
    
    app.run(host='0.0.0.0', port=5000, debug=True)