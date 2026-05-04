import {
  Component, ElementRef, ViewChild,
  Input, OnChanges, OnDestroy, AfterViewInit, SimpleChanges, inject
} from '@angular/core';
import { CommonModule } from '@angular/common';
import * as THREE from 'three';
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';
import { PLYLoader } from 'three/examples/jsm/loaders/PLYLoader.js';
import { AnalysisResultDTO } from '../../../core/models/scan.model';
import { ScanService } from '../../../core/services/scan.service';

const C = {
  joint: 0xffffff,
  jointKey: 0x00e5ff,
  ankle: 0xef5350,
  bone: 0x22c55e,
  boneEmissive: 0x15803d,
  pointCloud: 0x7dd3fc,
};

const BONE_CONNECTIONS: [string, string][] = [
  ['neck', 'l_shoulder'], ['neck', 'r_shoulder'],
  ['l_shoulder', 'r_shoulder'],
  ['neck', 'pelvis'],
  ['pelvis', 'l_hip'], ['pelvis', 'r_hip'],
  ['l_hip', 'l_knee'], ['r_hip', 'r_knee'],
  ['l_knee', 'l_ankle'], ['r_knee', 'r_ankle'],
  ['l_shoulder', 'l_elbow'], ['l_elbow', 'l_wrist'],
  ['r_shoulder', 'r_elbow'], ['r_elbow', 'r_wrist'],
];

const KEY_JOINTS = new Set([
  'l_shoulder', 'r_shoulder', 'l_hip', 'r_hip',
  'l_knee', 'r_knee', 'l_ankle', 'r_ankle',
]);
const SKIP_JOINTS = new Set(['l_ear', 'r_ear']);

interface Keypoint3D { name: string; x: number; y: number; z: number; }

@Component({
  selector: 'app-viewer-3d',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './viewer-3d.component.html',
  styleUrl: './viewer-3d.component.scss'
})
export class Viewer3dComponent implements AfterViewInit, OnChanges, OnDestroy {
  @ViewChild('canvas') canvasRef!: ElementRef<HTMLCanvasElement>;
  @ViewChild('viewerContainer') containerRef!: ElementRef<HTMLDivElement>;

  @Input() scanResult: AnalysisResultDTO | null = null;
  @Input() keypoints: Keypoint3D[] = [];
  @Input() sessionId: number | null = null;

  private scanService = inject(ScanService);

  showSkeleton = true;
  showJoints = true;
  showPointCloud = false;

  private savedSkeletonState = { skeleton: true, joints: true };

  pointCloudLoading = false;
  pointCloudLoaded = false;
  pointCloudError = false;

  private renderer!: THREE.WebGLRenderer;
  private scene!: THREE.Scene;
  private camera!: THREE.PerspectiveCamera;
  private controls!: OrbitControls;
  private clock = new THREE.Clock();
  private animationId = 0;

  private skeletonGroup = new THREE.Group();
  private jointsGroup = new THREE.Group();
  private pointCloudGroup = new THREE.Group();
  private resizeObserver!: ResizeObserver;

  private glowRings: THREE.Mesh[] = [];
  private autoRotateTimer: ReturnType<typeof setTimeout> | null = null;
  private processedKpMap = new Map<string, THREE.Vector3>();

  private savedCameraForSkeleton: { pos: THREE.Vector3, target: THREE.Vector3 } | null = null;

  ngAfterViewInit(): void {
    this.initScene();
    this.buildVisualization();
    this.animate();
    this.setupResize();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if ((changes['scanResult'] || changes['keypoints']) && this.scene) {
      this.buildVisualization();
    }
    if (changes['sessionId'] && this.scene) {
      this.clearGroup(this.pointCloudGroup);
      this.pointCloudLoaded = false;
      this.pointCloudError = false;
      this.showPointCloud = false;
    }
  }

  ngOnDestroy(): void {
    cancelAnimationFrame(this.animationId);
    this.resizeObserver?.disconnect();
    this.controls?.dispose();
    this.renderer?.dispose();
    if (this.autoRotateTimer) clearTimeout(this.autoRotateTimer);
  }

  private initScene(): void {
    const canvas = this.canvasRef.nativeElement;
    const container = this.containerRef.nativeElement;

    this.scene = new THREE.Scene();
    this.scene.background = new THREE.Color(0x080f1c);
    this.scene.fog = new THREE.Fog(0x080f1c, 8, 18);

    this.camera = new THREE.PerspectiveCamera(
      45, container.clientWidth / container.clientHeight, 0.01, 100
    );
    this.camera.position.set(0, 1.1, 3.0);

    this.renderer = new THREE.WebGLRenderer({ canvas, antialias: true });
    this.renderer.setSize(container.clientWidth, container.clientHeight);
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    this.renderer.shadowMap.enabled = true;
    this.renderer.shadowMap.type = THREE.PCFSoftShadowMap;

    this.controls = new OrbitControls(this.camera, canvas);
    this.controls.enableDamping = true;
    this.controls.dampingFactor = 0.08;
    this.controls.autoRotate = false;
    this.controls.autoRotateSpeed = 0.6;
    this.controls.addEventListener('start', () => {
      this.controls.autoRotate = false;
      if (this.autoRotateTimer) clearTimeout(this.autoRotateTimer);
    });
    this.controls.addEventListener('end', () => this.scheduleAutoRotate(4000));

    this.scene.add(new THREE.AmbientLight(0xffffff, 0.55));
    const dir = new THREE.DirectionalLight(0xffffff, 1.0);
    dir.position.set(3, 5, 4);
    dir.castShadow = true;
    this.scene.add(dir);
    this.scene.add(new THREE.HemisphereLight(0x4fc3f7, 0x081627, 0.4));

    const grid = new THREE.GridHelper(6, 30, 0x2a4a7a, 0x1a2e4a);
    grid.position.y = -1.05;
    (grid.material as THREE.Material).transparent = true;
    (grid.material as THREE.Material).opacity = 0.35;
    this.scene.add(grid);

    this.scene.add(this.skeletonGroup);
    this.scene.add(this.jointsGroup);
    this.scene.add(this.pointCloudGroup);
    this.pointCloudGroup.visible = false;
  }

  private animate = (): void => {
    this.animationId = requestAnimationFrame(this.animate);
    this.controls.update();

    for (const ring of this.glowRings) {
      const t = this.clock.getElapsedTime();
      const s = 1 + Math.sin(t * 2 + ring.position.x * 5) * 0.1;
      ring.scale.setScalar(s);
      (ring.material as THREE.MeshBasicMaterial).opacity = 0.35 + Math.sin(t * 2 + ring.position.x * 5) * 0.15;
    }

    this.renderer.render(this.scene, this.camera);
  };

  private buildVisualization(): void {
    this.clearGroup(this.skeletonGroup);
    this.clearGroup(this.jointsGroup);
    this.glowRings = [];
    if (!this.keypoints || this.keypoints.length === 0) return;

    const kpMap = new Map<string, THREE.Vector3>();
    for (const kp of this.keypoints) kpMap.set(kp.name, new THREE.Vector3(kp.x, kp.z, -kp.y));

    this.estimateArms(kpMap);
    this.processedKpMap = kpMap;

    this.buildSkeleton(kpMap);
    this.buildJoints(kpMap);

    this.fitCamera(kpMap);
    this.scheduleAutoRotate(2500);
  }

  private loadPointCloud(): void {
    if (!this.sessionId || this.pointCloudLoading || this.pointCloudLoaded) return;

    this.pointCloudLoading = true;
    this.pointCloudError = false;

    this.scanService.getPointCloud(this.sessionId).subscribe({
      next: (buffer: ArrayBuffer) => {
        try {
          const loader = new PLYLoader();
          const geometry = loader.parse(buffer);

          // Aliniere axe cu scheletul: keypoints folosesc (x, z, -y)
          // Echivalent cu o rotatie de -90° in jurul axei X
          geometry.applyMatrix4(new THREE.Matrix4().makeRotationX(-Math.PI / 2));

          geometry.computeBoundingBox();
          geometry.computeBoundingSphere();

          const bbox = geometry.boundingBox!;
          const center = new THREE.Vector3();
          bbox.getCenter(center);
          geometry.translate(-center.x, -center.y, -center.z);
          geometry.computeBoundingBox();
          geometry.computeBoundingSphere();

          const material = new THREE.PointsMaterial({
            color: C.pointCloud,
            size: 0.012,
            sizeAttenuation: true,
            transparent: true,
            opacity: 0.85,
            depthWrite: false,
          });

          const points = new THREE.Points(geometry, material);
          this.pointCloudGroup.add(points);
          this.pointCloudGroup.visible = this.showPointCloud;

          if (this.showPointCloud) {
            this.fitCameraToPointCloud();
          }

          this.pointCloudLoaded = true;
          this.pointCloudLoading = false;
          const positions = geometry.attributes['position'].array as Float32Array;
          console.log(`[POINT_CLOUD] Loaded ${positions.length / 3} raw points`);
        } catch (e) {
          console.error('[POINT_CLOUD] Failed to parse PLY:', e);
          this.pointCloudError = true;
          this.pointCloudLoading = false;
        }
      },
      error: (err) => {
        console.warn('[POINT_CLOUD] Failed to fetch:', err);
        this.pointCloudError = true;
        this.pointCloudLoading = false;
      }
    });
  }

  private fitCameraToPointCloud(): void {
    if (this.pointCloudGroup.children.length === 0) return;
    const points = this.pointCloudGroup.children[0] as THREE.Points;
    const geom = points.geometry as THREE.BufferGeometry;
    if (!geom.boundingSphere) geom.computeBoundingSphere();
    const sphere = geom.boundingSphere!;

    const radius = sphere.radius;
    const dist = radius * 2.2;

    this.camera.position.set(
      sphere.center.x + dist * 0.6,
      sphere.center.y + dist * 0.4,
      sphere.center.z + dist
    );
    this.controls.target.copy(sphere.center);
    this.controls.update();
  }

  togglePointCloud(): void {
    this.showPointCloud = !this.showPointCloud;

    if (this.showPointCloud) {
      this.savedSkeletonState = {
        skeleton: this.showSkeleton,
        joints: this.showJoints,
      };
      this.savedCameraForSkeleton = {
        pos: this.camera.position.clone(),
        target: this.controls.target.clone(),
      };

      this.skeletonGroup.visible = false;
      this.jointsGroup.visible = false;

      if (!this.pointCloudLoaded && !this.pointCloudError) {
        this.loadPointCloud();
      } else if (this.pointCloudLoaded) {
        this.fitCameraToPointCloud();
      }

      this.pointCloudGroup.visible = true;

    } else {
      this.skeletonGroup.visible = this.savedSkeletonState.skeleton;
      this.jointsGroup.visible = this.savedSkeletonState.joints;

      this.pointCloudGroup.visible = false;

      if (this.savedCameraForSkeleton) {
        this.camera.position.copy(this.savedCameraForSkeleton.pos);
        this.controls.target.copy(this.savedCameraForSkeleton.target);
        this.controls.update();
      }
    }
  }


  private fitCamera(kpMap: Map<string, THREE.Vector3>): void {
    const box = new THREE.Box3();
    for (const v of kpMap.values()) box.expandByPoint(v);
    if (box.isEmpty()) return;
    const c = box.getCenter(new THREE.Vector3());
    const s = box.getSize(new THREE.Vector3());
    const dist = Math.max(s.x, s.y, s.z) * 1.8;
    this.camera.position.set(c.x, c.y + 0.1, c.z + dist + 0.5);
    this.controls.target.copy(c);
    this.controls.update();
  }

  private buildSkeleton(kpMap: Map<string, THREE.Vector3>): void {
    const mat = new THREE.MeshStandardMaterial({
      color: C.bone, emissive: C.boneEmissive, emissiveIntensity: 0.5, roughness: 0.3,
    });
    for (const [a, b] of BONE_CONNECTIONS) {
      const start = kpMap.get(a), end = kpMap.get(b);
      if (!start || !end) continue;
      const dir = end.clone().sub(start);
      const length = dir.length();
      if (length < 0.001) continue;
      const bone = new THREE.Mesh(new THREE.CylinderGeometry(0.007, 0.007, length, 6), mat);
      bone.position.copy(start).addScaledVector(dir.clone().normalize(), length / 2);
      bone.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), dir.normalize());
      this.skeletonGroup.add(bone);
    }
  }

  private buildJoints(kpMap: Map<string, THREE.Vector3>): void {
    for (const [name, pos] of kpMap) {
      if (SKIP_JOINTS.has(name)) continue;
      const isKey = KEY_JOINTS.has(name);
      const isAnkle = name === 'l_ankle' || name === 'r_ankle';
      const color = isAnkle ? C.ankle : (isKey ? C.jointKey : C.joint);
      const radius = isKey ? 0.022 : 0.014;

      const joint = new THREE.Mesh(
        new THREE.SphereGeometry(radius, 16, 12),
        new THREE.MeshStandardMaterial({
          color, emissive: color, emissiveIntensity: 0.5,
          roughness: 0.2, metalness: 0.1,
        })
      );
      joint.position.copy(pos);
      joint.castShadow = true;
      this.jointsGroup.add(joint);

      if (isKey) {
        const ring = new THREE.Mesh(
          new THREE.RingGeometry(radius * 1.5, radius * 1.9, 24),
          new THREE.MeshBasicMaterial({
            color, transparent: true, opacity: 0.4, side: THREE.DoubleSide,
          })
        );
        ring.position.copy(pos);
        ring.lookAt(this.camera.position);
        this.jointsGroup.add(ring);
        this.glowRings.push(ring);
      }
    }
  }

  private estimateArms(kpMap: Map<string, THREE.Vector3>): void {
    const neck = kpMap.get('neck'), pelvis = kpMap.get('pelvis');
    const lS = kpMap.get('l_shoulder'), rS = kpMap.get('r_shoulder');
    if (!neck || !pelvis || !lS || !rS) return;
    const down = pelvis.clone().sub(neck).normalize();
    const tL = neck.distanceTo(pelvis);
    if (!kpMap.has('l_elbow')) kpMap.set('l_elbow', lS.clone().addScaledVector(down, tL * 0.55));
    if (!kpMap.has('l_wrist')) kpMap.set('l_wrist', kpMap.get('l_elbow')!.clone().addScaledVector(down, tL * 0.45));
    if (!kpMap.has('r_elbow')) kpMap.set('r_elbow', rS.clone().addScaledVector(down, tL * 0.55));
    if (!kpMap.has('r_wrist')) kpMap.set('r_wrist', kpMap.get('r_elbow')!.clone().addScaledVector(down, tL * 0.45));
  }

  private clearGroup(group: THREE.Group): void {
    while (group.children.length > 0) {
      const child = group.children[0] as THREE.Mesh;
      child.geometry?.dispose();
      if (Array.isArray(child.material)) child.material.forEach((m: THREE.Material) => m.dispose());
      else (child.material as THREE.Material)?.dispose();
      group.remove(child);
    }
  }

  private scheduleAutoRotate(ms: number): void {
    if (this.autoRotateTimer) clearTimeout(this.autoRotateTimer);
    this.autoRotateTimer = setTimeout(() => { this.controls.autoRotate = true; }, ms);
  }

  private setupResize(): void {
    this.resizeObserver = new ResizeObserver(() => {
      const c = this.containerRef.nativeElement;
      this.camera.aspect = c.clientWidth / c.clientHeight;
      this.camera.updateProjectionMatrix();
      this.renderer.setSize(c.clientWidth, c.clientHeight);
    });
    this.resizeObserver.observe(this.containerRef.nativeElement);
  }

  resetCamera(): void {
    if (this.showPointCloud && this.pointCloudLoaded) {
      this.fitCameraToPointCloud();
      return;
    }
    const kpMap = new Map<string, THREE.Vector3>();
    for (const kp of this.keypoints) kpMap.set(kp.name, new THREE.Vector3(kp.x, kp.z, -kp.y));
    this.fitCamera(kpMap);
  }

  toggleSkeleton(): void { this.showSkeleton = !this.showSkeleton; this.skeletonGroup.visible = this.showSkeleton; }
  toggleJoints(): void { this.showJoints = !this.showJoints; this.jointsGroup.visible = this.showJoints; }
}