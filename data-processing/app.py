import logging
import os
import tempfile

import open3d as o3d
import numpy as np
from flask import Flask, request, jsonify

from model_loader import AI_Pose_Estimator

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s - %(message)s",
)
logger = logging.getLogger(__name__)

app = Flask(__name__)

logger.info("Loading AI system...")
ai_engine = AI_Pose_Estimator()


def _lateral_extent_at_fraction(h_proj, w_proj, d_proj, frac_from_top, height, window=0.04):
    """Return (half_width, median_depth) for the point cloud slice at a given height fraction."""
    h_max = h_proj.max()
    h_target = h_max - frac_from_top * height
    mask = np.abs(h_proj - h_target) <= window * height
    if mask.sum() < 5:
        return 0.0, float(np.median(d_proj))
    w_slice = w_proj[mask]
    half_width = float((w_slice.max() - w_slice.min()) / 2)
    depth = float(np.median(d_proj[mask]))
    return half_width, depth


def get_heuristic_keypoints(pcd, user_height=1.75):
    points = np.asarray(pcd.points)
    if len(points) < 10:
        return {"error": "Too few points", "meta": {"method": "Heuristic_Fallback_v2"}}

    # 1. PCA — find the principal axis (max variance = body length axis)
    center = np.mean(points, axis=0)
    centered = points - center
    _, _, Vt = np.linalg.svd(centered, full_matrices=False)
    eigenvectors = Vt.T  # columns are principal axes, descending variance

    # Project: col 0 = height axis, col 1 = width axis, col 2 = depth axis
    projected = centered @ eigenvectors
    h_proj = projected[:, 0]
    w_proj = projected[:, 1]
    d_proj = projected[:, 2]

    h_min, h_max = h_proj.min(), h_proj.max()
    raw_height = max(h_max - h_min, 0.1)

    # Scale projected coordinates so the cloud matches the real user height
    scale = user_height / raw_height

    # 2. Sample actual width at anatomical levels
    shoulder_hw, shoulder_d = _lateral_extent_at_fraction(h_proj, w_proj, d_proj, 0.18, raw_height)
    hip_hw, hip_d         = _lateral_extent_at_fraction(h_proj, w_proj, d_proj, 0.50, raw_height)

    # Clamp widths to plausible anatomical ranges
    shoulder_hw = float(np.clip(shoulder_hw, 0.10, 0.30))
    hip_hw      = float(np.clip(hip_hw,      0.08, 0.22))
    knee_hw     = hip_hw * 0.55
    ankle_hw    = hip_hw * 0.40
    ear_hw      = 0.05  # fixed — head width ~10cm

    # 3. Anatomical proportions from top (7.5-head rule)
    #    Format: [height_frac_from_top, width_offset, depth_offset]
    #    width_offset > 0 → right side in PCA space, < 0 → left
    #    (actual left/right in world depends on scan orientation — acceptable for fallback)
    pca_layout = {
        "head":       (0.05,  0.0,        0.0),
        "nose":       (0.07,  0.0,        0.0),
        "l_ear":      (0.07, -ear_hw,     0.0),
        "r_ear":      (0.07,  ear_hw,     0.0),
        "neck":       (0.14,  0.0,        0.0),
        "l_shoulder": (0.18, -shoulder_hw, shoulder_d),
        "r_shoulder": (0.18,  shoulder_hw, shoulder_d),
        "l_hip":      (0.50, -hip_hw,     hip_d),
        "r_hip":      (0.50,  hip_hw,     hip_d),
        "pelvis":     (0.50,  0.0,        hip_d),
        "l_knee":     (0.73, -knee_hw,    hip_d),
        "r_knee":     (0.73,  knee_hw,    hip_d),
        "l_ankle":    (0.95, -ankle_hw,   0.0),
        "r_ankle":    (0.95,  ankle_hw,   0.0),
    }

    # 4. Transform each keypoint from PCA space back to world coordinates
    result = {}
    for name, (frac, w_off, d_off) in pca_layout.items():
        pca_pt = np.array([
            h_max - frac * raw_height,
            w_off / scale,   # undo scaling on lateral offsets
            d_off / scale,
        ])
        world_pt = eigenvectors @ pca_pt + center
        result[name] = {"x": float(world_pt[0]), "y": float(world_pt[1]), "z": float(world_pt[2])}

    result["meta"] = {"method": "Heuristic_Fallback_v2", "target_height": user_height}
    logger.info("Heuristic keypoints computed (shoulder_hw=%.3fm, hip_hw=%.3fm)", shoulder_hw, hip_hw)
    return result


def process_scan(file_path, user_height=1.75):
    logger.info("Processing: %s | target height: %.2fm", os.path.basename(file_path), user_height)

    try:
        pcd = o3d.io.read_point_cloud(file_path)
        if pcd.is_empty():
            return {"error": "Empty or corrupt file"}
        pcd, _ = pcd.remove_statistical_outlier(nb_neighbors=30, std_ratio=3.0)
    except Exception as e:
        return {"error": f"Loading error: {e}"}

    try:
        logger.info("Running AI inference...")
        return ai_engine.predict(pcd, real_height_meters=user_height)
    except Exception as e:
        logger.warning("AI failed: %s. Using heuristic fallback...", e)
        try:
            return get_heuristic_keypoints(pcd, user_height=user_height)
        except Exception as fallback_e:
            return {"error": f"AI failed ({e}) and fallback also failed ({fallback_e})"}


@app.route('/process-scan', methods=['POST'])
def api_endpoint():
    if 'file' not in request.files:
        return jsonify({"error": "No file provided"}), 400

    file = request.files['file']
    if file.filename == '':
        return jsonify({"error": "Empty filename"}), 400

    try:
        height_cm = float(request.form.get('height', 175))
    except ValueError:
        height_cm = 175.0

    user_height_meters = height_cm / 100.0

    with tempfile.NamedTemporaryFile(delete=False, suffix=".ply") as tmp:
        file.save(tmp.name)
        path = tmp.name

    result = process_scan(path, user_height=user_height_meters)
    os.remove(path)

    return jsonify(result)


if __name__ == '__main__':
    logger.info("Server starting at http://0.0.0.0:5000/process-scan")
    app.run(host='0.0.0.0', port=5000, debug=True)
