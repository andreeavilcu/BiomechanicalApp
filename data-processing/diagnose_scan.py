import open3d as o3d
import numpy as np
import sys
import os

def diagnose_scan(file_path):
    print("DIAGNOSTIC: 3D scan quality check")

    if not os.path.exists(file_path):
        print(f"ERROR: File not found: {file_path}")
        return False

    print(f"File: {file_path}")
    print(f"Size: {os.path.getsize(file_path) / 1024:.1f} KB")

    try:
        pcd = o3d.io.read_point_cloud(file_path)
        if pcd.is_empty():
            print("ERROR: File is empty or corrupt")
            return False
    except Exception as e:
        print(f"ERROR reading file: {e}")
        return False

    points = np.asarray(pcd.points)
    num_points = len(points)

    print("Basic stats:")
    print(f"  Total points: {num_points:,}")

    bbox = pcd.get_axis_aligned_bounding_box()
    extent = bbox.get_extent()
    volume = np.prod(extent)
    density = num_points / volume if volume > 0 else 0

    print(f"  Dimensions (L x W x H): {extent[0]:.2f} x {extent[1]:.2f} x {extent[2]:.2f} m")
    print(f"  Volume: {volume:.2f} m^3")
    print(f"  Density: {density:.1f} points/m^3")

    height = extent[2]
    print(f"Detected height: {height:.2f} m")

    score = 0
    max_score = 4

    print("Evaluating absolute density:")
    if num_points < 5000:
        print(f"  Very low ({num_points:,} points)")
        score += 0
    elif num_points < 15000:
        print(f"  Low ({num_points:,} points)")
        score += 1
    elif num_points < 30000:
        print(f"  Moderate ({num_points:,} points)")
        score += 2
    else:
        print(f"  High ({num_points:,} points)")
        score += 3

    print("Evaluating relative density (points/m^3):")
    if density < 5000:
        print(f"  Sparse ({density:.0f} p/m^3)")
        score += 0
    elif density < 15000:
        print(f"  Moderate ({density:.0f} p/m^3)")
        score += 1
    else:
        print(f"  Good ({density:.0f} p/m^3)")
        score += 1

    print("Checking clustering:")
    try:
        labels = np.array(pcd.cluster_dbscan(eps=0.3, min_points=30, print_progress=False))
        num_clusters = labels.max() + 1

        if num_clusters == 0:
            print("  No clusters detected")
            score += 0
        else:
            largest_cluster_size = 0
            for i in range(num_clusters):
                cluster_size = np.sum(labels == i)
                if cluster_size > largest_cluster_size:
                    largest_cluster_size = cluster_size

            pct_main_cluster = (largest_cluster_size / num_points) * 100
            print(f"  Clusters found: {num_clusters}")
            print(f"  Main cluster: {largest_cluster_size:,} points ({pct_main_cluster:.1f}%)")

            if pct_main_cluster < 50:
                print("  Main cluster <50% - noisy background")
                score += 0
            elif pct_main_cluster < 70:
                print("  Main cluster ~50-70% - acceptable")
                score += 0.5
            else:
                print("  Clear main cluster")
                score += 1
    except Exception as e:
        print(f"  Clustering failed: {e}")

    print("Final score:")
    print(f"  Score: {score:.1f}/{max_score}")

    if score < 1.5:
        print("VERDICT: Insufficient scan quality")
        return False
    elif score < 3.0:
        print("VERDICT: Moderate quality")
        return True
    else:
        print("VERDICT: Good quality")
        return True

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python diagnose_scan.py <file.ply>")
        sys.exit(1)

    file_path = sys.argv[1]
    success = diagnose_scan(file_path)
    sys.exit(0 if success else 1)
