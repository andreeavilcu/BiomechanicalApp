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
  joint: 0xf0e6d3,
  jointKey: 0xf59e0b,
  ankle: 0xff3d3d,
  pointCloud: 0x7dd3fc,
};

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

  showJoints = true;
  showPointCloud = false;

  pointCloudLoading = false;
  pointCloudLoaded = false;
  pointCloudError = false;

  private renderer!: THREE.WebGLRenderer;
  private scene!: THREE.Scene;
  private camera!: THREE.PerspectiveCamera;
  private controls!: OrbitControls;
  private clock = new THREE.Clock();
  private animationId = 0;

  private jointsGroup = new THREE.Group();
  private pointCloudGroup = new THREE.Group();
  private resizeObserver!: ResizeObserver;

  private glowRings: THREE.Mesh[] = [];
  private autoRotateTimer: ReturnType<typeof setTimeout> | null = null;
  private processedKpMap = new Map<string, THREE.Vector3>();
  private normalizeQuat: THREE.Quaternion | null = null;

  ngAfterViewInit(): void {
    this.initScene();
    this.buildVisualization();
    this.animate();
    this.setupResize();
    if (this.sessionId) {
      this.togglePointCloud();
    }
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
      if (this.sessionId) {
        this.togglePointCloud();
      }
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

    this.renderer = new THREE.WebGLRenderer({ canvas, antialias: true, preserveDrawingBuffer: true });
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

    this.scene.add(this.jointsGroup);
    this.scene.add(this.pointCloudGroup);
    this.pointCloudGroup.visible = false;
  }

  private animate = (): void => {
    this.animationId = requestAnimationFrame(this.animate);
    this.controls.update();

    const t = this.clock.getElapsedTime();
    for (const ring of this.glowRings) {
      const phase = t * 2.2 + ring.position.x * 5;
      const s = 1 + Math.sin(phase) * 0.18;
      ring.scale.setScalar(s);
      (ring.material as THREE.MeshBasicMaterial).opacity = 0.50 + Math.sin(phase) * 0.28;
    }

    this.renderer.render(this.scene, this.camera);
  };

  private computeUprightRotation(kpMap: Map<string, THREE.Vector3>): THREE.Quaternion | null {
    const head = kpMap.get('head') ?? kpMap.get('nose');
    const ankleL = kpMap.get('l_ankle');
    const ankleR = kpMap.get('r_ankle');
    if (!head || (!ankleL && !ankleR)) return null;

    const feet = (ankleL && ankleR)
      ? ankleL.clone().add(ankleR).multiplyScalar(0.5)
      : (ankleL ?? ankleR!).clone();

    const up = head.clone().sub(feet).normalize();
    if (Math.abs(up.y) > 0.85) return null;

    return new THREE.Quaternion().setFromUnitVectors(up, new THREE.Vector3(0, 1, 0));
  }

  private buildVisualization(): void {
    this.clearGroup(this.jointsGroup);
    this.glowRings = [];
    if (!this.keypoints || this.keypoints.length === 0) return;

    const kpMap = new Map<string, THREE.Vector3>();
    for (const kp of this.keypoints) kpMap.set(kp.name, new THREE.Vector3(kp.x, kp.z, -kp.y));

    this.normalizeQuat = this.computeUprightRotation(kpMap);
    if (this.normalizeQuat) {
      for (const v of kpMap.values()) v.applyQuaternion(this.normalizeQuat);
    }

    this.estimateArms(kpMap);
    this.processedKpMap = kpMap;

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

          geometry.applyMatrix4(new THREE.Matrix4().makeRotationX(-Math.PI / 2));

          if (this.normalizeQuat) {
            geometry.applyMatrix4(new THREE.Matrix4().makeRotationFromQuaternion(this.normalizeQuat));
          }

          const sf = this.scanResult?.scalingFactor ?? 1.0;
          if (sf !== 1.0 && sf > 0) {
            geometry.applyMatrix4(new THREE.Matrix4().makeScale(sf, sf, sf));
          }

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
      if (!this.pointCloudLoaded && !this.pointCloudError) {
        this.loadPointCloud();
      } else if (this.pointCloudLoaded) {
        this.fitCameraToPointCloud();
      }
      this.pointCloudGroup.visible = true;
    } else {
      this.pointCloudGroup.visible = false;
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

  private buildJoints(kpMap: Map<string, THREE.Vector3>): void {
    for (const [name, pos] of kpMap) {
      if (SKIP_JOINTS.has(name)) continue;
      const isKey = KEY_JOINTS.has(name);
      const isAnkle = name === 'l_ankle' || name === 'r_ankle';
      const color = isAnkle ? C.ankle : (isKey ? C.jointKey : C.joint);
      const radius = isKey ? 0.025 : 0.016;

      const joint = new THREE.Mesh(
        new THREE.SphereGeometry(radius, 20, 16),
        new THREE.MeshStandardMaterial({
          color, emissive: color, emissiveIntensity: 0.9,
          roughness: 0.15, metalness: 0.2,
        })
      );
      joint.position.copy(pos);
      joint.castShadow = true;
      this.jointsGroup.add(joint);

      const halo = new THREE.Mesh(
        new THREE.SphereGeometry(radius * 1.7, 16, 12),
        new THREE.MeshBasicMaterial({
          color, transparent: true, opacity: isKey ? 0.18 : 0.10,
          depthWrite: false, side: THREE.FrontSide,
        })
      );
      halo.position.copy(pos);
      this.jointsGroup.add(halo);

      const ringInner = isKey ? radius * 1.7 : radius * 1.4;
      const ringOuter = isKey ? radius * 2.3 : radius * 1.9;
      const ringOpacity = isKey ? 0.55 : 0.30;
      const ring = new THREE.Mesh(
        new THREE.RingGeometry(ringInner, ringOuter, 28),
        new THREE.MeshBasicMaterial({
          color, transparent: true, opacity: ringOpacity, side: THREE.DoubleSide, depthWrite: false,
        })
      );
      ring.position.copy(pos);
      ring.lookAt(this.camera.position);
      this.jointsGroup.add(ring);
      this.glowRings.push(ring);

      if (isKey) {
        const ring2 = new THREE.Mesh(
          new THREE.RingGeometry(radius * 2.5, radius * 3.2, 28),
          new THREE.MeshBasicMaterial({
            color, transparent: true, opacity: 0.22, side: THREE.DoubleSide, depthWrite: false,
          })
        );
        ring2.position.copy(pos);
        ring2.lookAt(this.camera.position);
        this.jointsGroup.add(ring2);
        this.glowRings.push(ring2);
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

  toggleJoints(): void { this.showJoints = !this.showJoints; this.jointsGroup.visible = this.showJoints; }

  captureScreenshot(): string {
    this.renderer.render(this.scene, this.camera);
    return this.renderer.domElement.toDataURL('image/png');
  }
}