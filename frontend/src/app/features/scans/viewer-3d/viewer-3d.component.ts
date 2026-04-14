import {
  Component, ElementRef, ViewChild,
  Input, OnChanges, OnDestroy, AfterViewInit, SimpleChanges
} from '@angular/core';
import { CommonModule } from '@angular/common';
import * as THREE from 'three';
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';
import { AnalysisResultDTO } from '../../../core/models/scan.model';

const C = {
  head:       0x4fc3f7,
  torso:      0x29b6f6,
  upperArm:   0x66bb6a,
  forearm:    0xa5d6a7,
  thigh:      0xffa726,
  shin:       0xffcc80,
  joint:      0xffffff,
  jointKey:   0x00e5ff,
  ankle:      0xef5350,
};

const BODY_VOLUMES: [string, string, number, number][] = [
  ['l_shoulder', 'l_elbow',  0.042, C.upperArm],
  ['l_elbow',    'l_wrist',  0.032, C.forearm],
  ['r_shoulder', 'r_elbow',  0.042, C.upperArm],
  ['r_elbow',    'r_wrist',  0.032, C.forearm],
  ['l_hip',      'l_knee',   0.060, C.thigh],
  ['l_knee',     'l_ankle',  0.048, C.shin],
  ['r_hip',      'r_knee',   0.060, C.thigh],
  ['r_knee',     'r_ankle',  0.048, C.shin],
];

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

  showSkeleton = true;
  showJoints   = true;
  showBody     = true;

  private renderer!: THREE.WebGLRenderer;
  private scene!: THREE.Scene;
  private camera!: THREE.PerspectiveCamera;
  private controls!: OrbitControls;
  private clock = new THREE.Clock();
  private animationId = 0;

  private skeletonGroup = new THREE.Group();
  private jointsGroup   = new THREE.Group();
  private bodyGroup     = new THREE.Group();
  private resizeObserver!: ResizeObserver;

  private matCache = new Map<number, THREE.MeshStandardMaterial>();
  private glowRings: THREE.Mesh[] = [];
  private autoRotateTimer: ReturnType<typeof setTimeout> | null = null;
  private processedKpMap = new Map<string, THREE.Vector3>();

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
  }

  ngOnDestroy(): void {
    cancelAnimationFrame(this.animationId);
    this.resizeObserver?.disconnect();
    this.controls?.dispose();
    this.renderer?.dispose();
    if (this.autoRotateTimer) clearTimeout(this.autoRotateTimer);
    this.matCache.forEach(m => m.dispose());
  }

  private initScene(): void {
    const canvas    = this.canvasRef.nativeElement;
    const container = this.containerRef.nativeElement;

    this.scene = new THREE.Scene();
    this.scene.background = new THREE.Color(0x080f1c);
    this.scene.fog = new THREE.Fog(0x080f1c, 8, 18);

    this.camera = new THREE.PerspectiveCamera(
      45, container.clientWidth / container.clientHeight, 0.01, 50
    );
    this.camera.position.set(0, 1.1, 3.0);

    this.renderer = new THREE.WebGLRenderer({ canvas, antialias: true });
    this.renderer.setSize(container.clientWidth, container.clientHeight);
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    this.renderer.shadowMap.enabled = true;
    this.renderer.shadowMap.type = THREE.PCFSoftShadowMap;
    this.renderer.toneMapping = THREE.ACESFilmicToneMapping;
    this.renderer.toneMappingExposure = 1.15;

    this.controls = new OrbitControls(this.camera, canvas);
    this.controls.enableDamping    = true;
    this.controls.dampingFactor    = 0.06;
    this.controls.target.set(0, 0.85, 0);
    this.controls.minDistance      = 0.5;
    this.controls.maxDistance      = 7;
    this.controls.autoRotate       = false;
    this.controls.autoRotateSpeed  = 1.0;
    this.controls.update();

    this.scheduleAutoRotate(2000);
    this.controls.addEventListener('start', () => {
      this.controls.autoRotate = false;
      if (this.autoRotateTimer) clearTimeout(this.autoRotateTimer);
    });
    this.controls.addEventListener('end', () => this.scheduleAutoRotate(4000));

    // Iluminare clinică
    this.scene.add(new THREE.AmbientLight(0x0d1f35, 2.5));
    const key = new THREE.DirectionalLight(0xd0e8ff, 2.4);
    key.position.set(2, 5, 3);
    key.castShadow = true;
    key.shadow.mapSize.set(2048, 2048);
    this.scene.add(key);
    const fill = new THREE.DirectionalLight(0x4fc3f7, 0.7);
    fill.position.set(-3, 2, -2);
    this.scene.add(fill);
    const rim = new THREE.DirectionalLight(0x00e5ff, 0.45);
    rim.position.set(0, 1, -4);
    this.scene.add(rim);
    const bottom = new THREE.PointLight(0x1a3a5c, 2.0, 4);
    bottom.position.set(0, -0.3, 0);
    this.scene.add(bottom);

    const grid = new THREE.GridHelper(5, 25, 0x0d2444, 0x0d2444);
    (grid.material as THREE.Material).opacity = 0.7;
    (grid.material as THREE.Material).transparent = true;
    this.scene.add(grid);
    const innerGrid = new THREE.GridHelper(2, 10, 0x1a4a7a, 0x1a4a7a);
    (innerGrid.material as THREE.Material).opacity = 0.8;
    (innerGrid.material as THREE.Material).transparent = true;
    this.scene.add(innerGrid);
    const ground = new THREE.Mesh(
      new THREE.PlaneGeometry(6, 6),
      new THREE.MeshStandardMaterial({ color: 0x040a12, roughness: 1, transparent: true, opacity: 0.6 })
    );
    ground.rotation.x = -Math.PI / 2;
    ground.position.y = -0.001;
    ground.receiveShadow = true;
    this.scene.add(ground);

    this.scene.add(this.bodyGroup);
    this.scene.add(this.skeletonGroup);
    this.scene.add(this.jointsGroup);
  }

  private buildVisualization(): void {
    this.clearGroup(this.skeletonGroup);
    this.clearGroup(this.jointsGroup);
    this.clearGroup(this.bodyGroup);
    this.glowRings = [];

    if (!this.keypoints?.length) return;

    const kpMap = new Map<string, THREE.Vector3>();
    for (const kp of this.keypoints) {
      kpMap.set(kp.name, new THREE.Vector3(kp.x, kp.z, -kp.y));
    }

    let minY = Infinity;
    kpMap.forEach(p => { if (p.y < minY) minY = p.y; });
    if (isFinite(minY)) kpMap.forEach(p => { p.y -= minY; });

    const targetH = this.scanResult?.targetHeightMeters ?? 1.75;
    const nose    = kpMap.get('nose');
    const lAnkle  = kpMap.get('l_ankle');
    const rAnkle  = kpMap.get('r_ankle');
    const ankleY  = (lAnkle && rAnkle) ? Math.min(lAnkle.y, rAnkle.y) : 0;
    if (nose && nose.y - ankleY > 0.01) {
      const scale = targetH / (nose.y - ankleY);
      kpMap.forEach(p => { p.x *= scale; p.y *= scale; p.z *= scale; });
    }

    let sumX = 0, sumZ = 0;
    kpMap.forEach(p => { sumX += p.x; sumZ += p.z; });
    const cx = sumX / kpMap.size, cz = sumZ / kpMap.size;
    kpMap.forEach(p => { p.x -= cx; p.z -= cz; });

    this.estimateArms(kpMap);
    this.buildBodyVolumes(kpMap);
    this.buildSkeleton(kpMap);
    this.buildJoints(kpMap);
    this.fitCamera(kpMap);
    this.processedKpMap = new Map(kpMap);
  }

  private buildBodyVolumes(kpMap: Map<string, THREE.Vector3>): void {
    this.buildHead(kpMap);
    this.buildTorso(kpMap);
    this.buildRoundCaps(kpMap);
    for (const [from, to, radius, color] of BODY_VOLUMES) {
      this.addCapsule(kpMap, from, to, radius, color);
    }
    this.addFoot(kpMap, 'l_ankle');
    this.addFoot(kpMap, 'r_ankle');
  }

  private buildHead(kpMap: Map<string, THREE.Vector3>): void {
    const nose = kpMap.get('nose'), neck = kpMap.get('neck');
    if (!nose || !neck) return;
    const lEar = kpMap.get('l_ear'), rEar = kpMap.get('r_ear');
    let radius = lEar && rEar
      ? lEar.distanceTo(rEar) * 0.55
      : nose.distanceTo(neck) * 0.55;
    radius = Math.max(0.07, Math.min(radius, 0.16));
    const center = nose.clone().add(neck).multiplyScalar(0.5);
    center.y += 0.015;
    const head = new THREE.Mesh(
      new THREE.SphereGeometry(radius, 22, 18),
      this.getMat(C.head)
    );
    head.position.copy(center);
    head.scale.set(1, 1.1, 0.9);
    head.castShadow = true;
    this.bodyGroup.add(head);
    this.addCapsuleBetween(nose, neck, Math.min(radius * 0.38, 0.044), C.head);
  }

  private buildTorso(kpMap: Map<string, THREE.Vector3>): void {
    const neck = kpMap.get('neck'), pelvis = kpMap.get('pelvis');
    const lS = kpMap.get('l_shoulder'), rS = kpMap.get('r_shoulder');
    const lH = kpMap.get('l_hip'), rH = kpMap.get('r_hip');
    if (!neck || !pelvis || !lS || !rS || !lH || !rH) return;
    const shoulderW = lS.distanceTo(rS) * 0.44;
    const hipW      = lH.distanceTo(rH) * 0.42;
    const torsoDir  = neck.clone().sub(pelvis);
    const length    = torsoDir.length();
    const center    = pelvis.clone().add(neck).multiplyScalar(0.5);
    const torso = new THREE.Mesh(
      new THREE.CylinderGeometry(shoulderW, hipW, length, 14, 1, false),
      this.getMat(C.torso)
    );
    torso.position.copy(center);
    torso.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), torsoDir.normalize());
    torso.scale.z = 0.60;
    torso.castShadow = true;
    this.bodyGroup.add(torso);
  }

  private buildRoundCaps(kpMap: Map<string, THREE.Vector3>): void {
    const caps: [string, number, number][] = [
      ['l_shoulder', 0.056, C.head],  ['r_shoulder', 0.056, C.head],
      ['l_hip',      0.050, C.torso], ['r_hip',      0.050, C.torso],
      ['pelvis',     0.050, C.torso],
    ];
    for (const [name, r, color] of caps) {
      const pos = kpMap.get(name);
      if (!pos) continue;
      const cap = new THREE.Mesh(new THREE.SphereGeometry(r, 14, 12), this.getMat(color));
      cap.position.copy(pos);
      cap.castShadow = true;
      this.bodyGroup.add(cap);
    }
  }

  private addFoot(kpMap: Map<string, THREE.Vector3>, ankleName: string): void {
    const ankle = kpMap.get(ankleName);
    if (!ankle) return;
    const foot = new THREE.Mesh(
      new THREE.BoxGeometry(0.06, 0.03, 0.13),
      this.getMat(C.shin)
    );
    foot.position.set(ankle.x, ankle.y - 0.015, ankle.z + 0.045);
    foot.castShadow = true;
    this.bodyGroup.add(foot);
  }

  private addCapsule(kpMap: Map<string, THREE.Vector3>, fromName: string, toName: string, radius: number, color: number): void {
    const from = kpMap.get(fromName), to = kpMap.get(toName);
    if (from && to) this.addCapsuleBetween(from, to, radius, color);
  }

  private addCapsuleBetween(from: THREE.Vector3, to: THREE.Vector3, radius: number, color: number): void {
    const dir = to.clone().sub(from);
    const length = dir.length();
    if (length < 0.001) return;
    const capsule = new THREE.Mesh(
      new THREE.CapsuleGeometry(radius, length, 4, 14),
      this.getMat(color)
    );
    capsule.position.copy(from).addScaledVector(dir.clone().normalize(), length / 2);
    capsule.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), dir.normalize());
    capsule.castShadow = true;
    this.bodyGroup.add(capsule);
  }

  private buildSkeleton(kpMap: Map<string, THREE.Vector3>): void {
    const mat = new THREE.MeshStandardMaterial({
      color: 0x22c55e, emissive: 0x15803d, emissiveIntensity: 0.5, roughness: 0.3,
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
      const isKey   = KEY_JOINTS.has(name);
      const isAnkle = name === 'l_ankle' || name === 'r_ankle';
      const color   = isAnkle ? C.ankle : (isKey ? C.jointKey : C.joint);
      const radius  = isKey ? 0.026 : 0.018;

      const sphere = new THREE.Mesh(
        new THREE.SphereGeometry(radius, 20, 16),
        new THREE.MeshStandardMaterial({
          color, emissive: color, emissiveIntensity: isKey ? 0.75 : 0.3,
          roughness: 0.2, metalness: 0.3,
        })
      );
      sphere.position.copy(pos);
      sphere.castShadow = true;
      this.jointsGroup.add(sphere);

      if (isKey) {
        const ring = new THREE.Mesh(
          new THREE.SphereGeometry(radius * 1.7, 16, 12),
          new THREE.MeshBasicMaterial({ color, transparent: true, opacity: 0.15, depthWrite: false })
        );
        ring.position.copy(pos);
        this.jointsGroup.add(ring);
        this.glowRings.push(ring);
      }
    }
  }

  private fitCamera(kpMap: Map<string, THREE.Vector3>): void {
  if (!kpMap.size) return;
  const box = new THREE.Box3();
  kpMap.forEach(p => box.expandByPoint(p));
  const center = new THREE.Vector3(), size = new THREE.Vector3();
  box.getCenter(center);
  box.getSize(size);
  const height = size.y;

  this.controls.target.set(center.x, height * 0.55, center.z);
  this.camera.position.set(
    center.x,
    height * 0.55,
    center.z + height * 1.6
  );
  this.controls.update();
}

  private animate = (): void => {
    this.animationId = requestAnimationFrame(this.animate);
    const t = this.clock.getElapsedTime();
    for (const ring of this.glowRings) {
      ring.scale.setScalar(1.0 + Math.sin(t * 2.2) * 0.13);
      (ring.material as THREE.MeshBasicMaterial).opacity = 0.09 + Math.sin(t * 2.2) * 0.07;
    }
    this.controls?.update();
    this.renderer?.render(this.scene, this.camera);
  };

  // ── Utils ──────────────────────────────────────────────────────────────────
  private getMat(color: number): THREE.MeshStandardMaterial {
    if (this.matCache.has(color)) return this.matCache.get(color)!;
    const mat = new THREE.MeshStandardMaterial({
      color, emissive: color, emissiveIntensity: 0.18,
      roughness: 0.55, metalness: 0.08, transparent: true, opacity: 0.88,
    });
    this.matCache.set(color, mat);
    return mat;
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
    const kpMap = new Map<string, THREE.Vector3>();
    for (const kp of this.keypoints) kpMap.set(kp.name, new THREE.Vector3(kp.x, kp.z, -kp.y));
    this.fitCamera(kpMap);
  }

  toggleBody(): void     { this.showBody = !this.showBody; this.bodyGroup.visible = this.showBody; }
  toggleSkeleton(): void { this.showSkeleton = !this.showSkeleton; this.skeletonGroup.visible = this.showSkeleton; }
  toggleJoints(): void   { this.showJoints = !this.showJoints; this.jointsGroup.visible = this.showJoints; }
}