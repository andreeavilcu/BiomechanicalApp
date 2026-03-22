import open3d as o3d
import numpy as np
import os
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

    try:
        print("AI: Running inference...")
        keypoints = ai_engine.predict(pcd, real_height_meters=user_height)
        return keypoints

    except Exception as e:
        print(f"AI FAILED: {e}. Using heuristic fallback...")
        try:
            fallback_keypoints = get_heuristic_keypoints(pcd)
            return fallback_keypoints
        except Exception as fallback_e:
            return {"error": f"AI failed ({e}) and fallback also failed ({fallback_e})"}

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