import {
  Component, ElementRef, ViewChild,
  Input, OnChanges, OnDestroy, AfterViewInit, SimpleChanges
} from '@angular/core';
import { CommonModule } from '@angular/common';
import * as THREE from 'three';
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';
import { AnalysisResultDTO } from '../../../core/models/scan.model';

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

const KEYPOINT_COLORS: Record<string, number> = {
  nose: 0xff4444,
  l_ear: 0x4488ff, r_ear: 0x4488ff,
  neck: 0xff4444,
  l_shoulder: 0xff4444, r_shoulder: 0xff4444,
  l_elbow: 0xff4444, r_elbow: 0xff4444,
  l_wrist: 0xff4444, r_wrist: 0xff4444,
  l_hip: 0xff8800, r_hip: 0xff8800,
  pelvis: 0xff8800,
  l_knee: 0xff4444, r_knee: 0xff4444,
  l_ankle: 0xff4444, r_ankle: 0xff4444,
};


const BODY_SEGMENTS: [string, string, number][] = [
  ['l_shoulder', 'l_elbow',  0.040],
  ['l_elbow',    'l_wrist',  0.030],
  ['r_shoulder', 'r_elbow',  0.040],
  ['r_elbow',    'r_wrist',  0.030],
  ['l_hip',      'l_knee',   0.058],
  ['l_knee',     'l_ankle',  0.046],
  ['r_hip',      'r_knee',   0.058],
  ['r_knee',     'r_ankle',  0.046],
];

interface Keypoint3D {
  name: string;
  x: number;
  y: number;
  z: number;
}

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
  showJoints = true;
  showBody = true;

  private renderer!: THREE.WebGLRenderer;
  private scene!: THREE.Scene;
  private camera!: THREE.PerspectiveCamera;
  private controls!: OrbitControls;
  private animationId = 0;
  private skeletonGroup = new THREE.Group();
  private jointsGroup = new THREE.Group();
  private bodyGroup = new THREE.Group();
  private resizeObserver!: ResizeObserver;

  
  private skinMat!: THREE.MeshStandardMaterial;

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
    this.skinMat?.dispose();
  }

  private initScene(): void {
    const canvas = this.canvasRef.nativeElement;
    const container = this.containerRef.nativeElement;

    this.scene = new THREE.Scene();
    this.scene.background = new THREE.Color(0x0f172a);

    this.camera = new THREE.PerspectiveCamera(
      50, container.clientWidth / container.clientHeight, 0.01, 100
    );
    this.camera.position.set(0, 1, 3);

    this.renderer = new THREE.WebGLRenderer({ canvas, antialias: true });
    this.renderer.setSize(container.clientWidth, container.clientHeight);
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    this.renderer.shadowMap.enabled = true;
    this.renderer.shadowMap.type = THREE.PCFSoftShadowMap;

    this.controls = new OrbitControls(this.camera, canvas);
    this.controls.enableDamping = true;
    this.controls.dampingFactor = 0.08;
    this.controls.target.set(0, 0.8, 0);
    this.controls.update();

    const ambient = new THREE.AmbientLight(0xffffff, 0.5);
    this.scene.add(ambient);

    const key = new THREE.DirectionalLight(0xffffff, 1.0);
    key.position.set(3, 6, 4);
    key.castShadow = true;
    this.scene.add(key);

    const fill = new THREE.DirectionalLight(0x8fb4e8, 0.4);
    fill.position.set(-3, 2, -2);
    this.scene.add(fill);

    const grid = new THREE.GridHelper(4, 20, 0x1e293b, 0x1e293b);
    this.scene.add(grid);

   
    this.scene.add(this.bodyGroup);
    this.scene.add(this.skeletonGroup);
    this.scene.add(this.jointsGroup);
  }

  private buildVisualization(): void {
    this.clearGroup(this.skeletonGroup);
    this.clearGroup(this.jointsGroup);
    this.clearGroup(this.bodyGroup);
    this.skinMat?.dispose();

    if (!this.keypoints || this.keypoints.length === 0) return;

    const kpMap = new Map<string, THREE.Vector3>();
    for (const kp of this.keypoints) {
      kpMap.set(kp.name, new THREE.Vector3(kp.x, kp.z, -kp.y));
    }

    let minY = Infinity;
    kpMap.forEach(pos => { if (pos.y < minY) minY = pos.y; });
    if (minY !== Infinity) {
      kpMap.forEach(pos => { pos.y -= minY; });
    }

    const nosePos = kpMap.get('nose');
    const targetH = this.scanResult?.targetHeightMeters ?? null;
    if (nosePos && targetH && nosePos.y > 0) {
      const scale = targetH / nosePos.y;
      kpMap.forEach(pos => { pos.x *= scale; pos.y *= scale; pos.z *= scale; });
    }

    this.estimateArms(kpMap);

    this.skinMat = new THREE.MeshStandardMaterial({
      color: 0x5b9bd5,
      roughness: 0.65,
      metalness: 0.05,
      transparent: true,
      opacity: 0.82,
    });

    this.buildBodyVolumes(kpMap);
    this.buildSkeleton(kpMap);
    this.buildJoints(kpMap);
    this.fitCameraToKeypoints(kpMap);
  }


  private estimateArms(kpMap: Map<string, THREE.Vector3>): void {
    const neck = kpMap.get('neck');
    const pelvis = kpMap.get('pelvis');
    const lShoulder = kpMap.get('l_shoulder');
    const rShoulder = kpMap.get('r_shoulder');
    if (!neck || !pelvis || !lShoulder || !rShoulder) return;

    const down = pelvis.clone().sub(neck).normalize();
    const torsoLen = neck.distanceTo(pelvis);
    const upperArm = torsoLen * 0.55;
    const forearm  = torsoLen * 0.45;

    if (!kpMap.has('l_elbow')) {
      kpMap.set('l_elbow', lShoulder.clone().addScaledVector(down, upperArm));
    }
    if (!kpMap.has('l_wrist')) {
      kpMap.set('l_wrist', kpMap.get('l_elbow')!.clone().addScaledVector(down, forearm));
    }
    if (!kpMap.has('r_elbow')) {
      kpMap.set('r_elbow', rShoulder.clone().addScaledVector(down, upperArm));
    }
    if (!kpMap.has('r_wrist')) {
      kpMap.set('r_wrist', kpMap.get('r_elbow')!.clone().addScaledVector(down, forearm));
    }
  }


  private buildBodyVolumes(kpMap: Map<string, THREE.Vector3>): void {
    this.buildHead(kpMap);
    this.buildTorso(kpMap);
    this.buildShoulderCaps(kpMap);

    for (const [a, b, r] of BODY_SEGMENTS) {
      this.addCapsule(kpMap, a, b, r);
    }

    this.addFoot(kpMap, 'l_ankle');
    this.addFoot(kpMap, 'r_ankle');
  }

  private buildHead(kpMap: Map<string, THREE.Vector3>): void {
    const nose = kpMap.get('nose');
    const neck = kpMap.get('neck');
    const lEar = kpMap.get('l_ear');
    const rEar = kpMap.get('r_ear');
    if (!nose || !neck) return;

    const center = nose.clone().add(neck).multiplyScalar(0.5);
    center.y += 0.015;

    let radius = 0.10;
    if (lEar && rEar) {
      radius = lEar.distanceTo(rEar) * 0.55;
    } else {
      radius = nose.distanceTo(neck) * 0.55;
    }
    radius = Math.max(0.07, Math.min(radius, 0.16));

    const head = new THREE.Mesh(
      new THREE.SphereGeometry(radius, 20, 16),
      this.skinMat
    );
    head.position.copy(center);
    head.scale.set(1, 1.1, 0.9);
    this.bodyGroup.add(head);

    this.addCapsuleBetween(nose, neck, Math.min(radius * 0.38, 0.045));
  }

  private buildTorso(kpMap: Map<string, THREE.Vector3>): void {
    const neck    = kpMap.get('neck');
    const pelvis  = kpMap.get('pelvis');
    const lShoulder = kpMap.get('l_shoulder');
    const rShoulder = kpMap.get('r_shoulder');
    const lHip    = kpMap.get('l_hip');
    const rHip    = kpMap.get('r_hip');
    if (!neck || !pelvis || !lShoulder || !rShoulder || !lHip || !rHip) return;

    const shoulderW = lShoulder.distanceTo(rShoulder) * 0.44;
    const hipW      = lHip.distanceTo(rHip) * 0.42;
    const torsoDir  = neck.clone().sub(pelvis);
    const length    = torsoDir.length();
    const center    = pelvis.clone().add(neck).multiplyScalar(0.5);

    const torso = new THREE.Mesh(
      new THREE.CylinderGeometry(shoulderW, hipW, length, 12, 1, false),
      this.skinMat
    );
    torso.position.copy(center);
    torso.quaternion.setFromUnitVectors(
      new THREE.Vector3(0, 1, 0),
      torsoDir.normalize()
    );
    torso.scale.z = 0.60;
    this.bodyGroup.add(torso);
  }

  private buildShoulderCaps(kpMap: Map<string, THREE.Vector3>): void {
    for (const name of ['l_shoulder', 'r_shoulder']) {
      const pos = kpMap.get(name);
      if (!pos) continue;
      const cap = new THREE.Mesh(
        new THREE.SphereGeometry(0.055, 12, 10),
        this.skinMat
      );
      cap.position.copy(pos);
      this.bodyGroup.add(cap);
    }
    for (const name of ['l_hip', 'r_hip', 'pelvis']) {
      const pos = kpMap.get(name);
      if (!pos) continue;
      const cap = new THREE.Mesh(
        new THREE.SphereGeometry(0.05, 12, 10),
        this.skinMat
      );
      cap.position.copy(pos);
      this.bodyGroup.add(cap);
    }
  }

  private addFoot(kpMap: Map<string, THREE.Vector3>, ankleName: string): void {
    const ankle = kpMap.get(ankleName);
    if (!ankle) return;
    const foot = new THREE.Mesh(
      new THREE.BoxGeometry(0.06, 0.03, 0.13),
      this.skinMat
    );
    foot.position.set(ankle.x, ankle.y - 0.015, ankle.z + 0.045);
    this.bodyGroup.add(foot);
  }

  /** Capsule between two named keypoints (skipped if either is missing). */
  private addCapsule(
    kpMap: Map<string, THREE.Vector3>,
    fromName: string,
    toName: string,
    radius: number
  ): void {
    const from = kpMap.get(fromName);
    const to   = kpMap.get(toName);
    if (!from || !to) return;
    this.addCapsuleBetween(from, to, radius);
  }

  /** Capsule between two explicit positions. */
  private addCapsuleBetween(
    from: THREE.Vector3,
    to: THREE.Vector3,
    radius: number
  ): void {
    const dir    = to.clone().sub(from);
    const length = dir.length();
    if (length < 0.001) return;

    const capsule = new THREE.Mesh(
      new THREE.CapsuleGeometry(radius, length, 4, 12),
      this.skinMat
    );
    capsule.position.copy(from).addScaledVector(dir.normalize(), length / 2);
    capsule.quaternion.setFromUnitVectors(
      new THREE.Vector3(0, 1, 0),
      dir.normalize()
    );
    this.bodyGroup.add(capsule);
  }


  private buildSkeleton(kpMap: Map<string, THREE.Vector3>): void {
    const boneMat = new THREE.MeshStandardMaterial({
      color: 0x22c55e,
      emissive: 0x15803d,
      emissiveIntensity: 0.35,
      roughness: 0.4,
    });

    for (const [startName, endName] of BONE_CONNECTIONS) {
      const start = kpMap.get(startName);
      const end   = kpMap.get(endName);
      if (!start || !end) continue;

      const dir    = end.clone().sub(start);
      const length = dir.length();
      if (length < 0.001) continue;

      const bone = new THREE.Mesh(
        new THREE.CylinderGeometry(0.008, 0.008, length, 6),
        boneMat
      );
      bone.position.copy(start).addScaledVector(dir.normalize(), length / 2);
      bone.quaternion.setFromUnitVectors(
        new THREE.Vector3(0, 1, 0),
        dir.normalize()
      );
      this.skeletonGroup.add(bone);
    }
  }


  private readonly SKIP_JOINTS = new Set(['l_ear', 'r_ear', 'nose']);

  private buildJoints(kpMap: Map<string, THREE.Vector3>): void {
    for (const [name, pos] of kpMap) {
      if (this.SKIP_JOINTS.has(name)) continue;
      const color = KEYPOINT_COLORS[name] ?? 0xff4444;
      const sphere = new THREE.Mesh(
        new THREE.SphereGeometry(0.022, 16, 12),
        new THREE.MeshStandardMaterial({
          color,
          emissive: color,
          emissiveIntensity: 0.4,
          roughness: 0.3,
        })
      );
      sphere.position.copy(pos);
      this.jointsGroup.add(sphere);

    }
  }


  private fitCameraToKeypoints(kpMap: Map<string, THREE.Vector3>): void {
    if (kpMap.size === 0) return;
    const box = new THREE.Box3();
    kpMap.forEach(pos => box.expandByPoint(pos));
    const center = new THREE.Vector3();
    const size   = new THREE.Vector3();
    box.getCenter(center);
    box.getSize(size);
    const maxDim = Math.max(size.x, size.y, size.z);
    this.controls.target.copy(center);
    this.camera.position.set(
      center.x,
      center.y + maxDim * 0.25,
      center.z + maxDim * 1.6
    );
    this.controls.update();
  }


  private clearGroup(group: THREE.Group): void {
    while (group.children.length > 0) {
      const child = group.children[0] as THREE.Mesh | THREE.Line;
      child.geometry?.dispose();
      if (Array.isArray(child.material)) {
        child.material.forEach((m: THREE.Material) => m.dispose());
      } else {
        (child.material as THREE.Material)?.dispose();
      }
      group.remove(child);
    }
  }

  private animate = (): void => {
    this.animationId = requestAnimationFrame(this.animate);
    this.controls?.update();
    this.renderer?.render(this.scene, this.camera);
  };

  private setupResize(): void {
    this.resizeObserver = new ResizeObserver(() => {
      const container = this.containerRef.nativeElement;
      const w = container.clientWidth;
      const h = container.clientHeight;
      this.camera.aspect = w / h;
      this.camera.updateProjectionMatrix();
      this.renderer.setSize(w, h);
    });
    this.resizeObserver.observe(this.containerRef.nativeElement);
  }

  resetCamera(): void {
    this.camera.position.set(0, 1, 3);
    this.controls.target.set(0, 0.8, 0);
    this.controls.update();
  }

  toggleBody(): void {
    this.showBody = !this.showBody;
    this.bodyGroup.visible = this.showBody;
  }

  toggleSkeleton(): void {
    this.showSkeleton = !this.showSkeleton;
    this.skeletonGroup.visible = this.showSkeleton;
  }

  toggleJoints(): void {
    this.showJoints = !this.showJoints;
    this.jointsGroup.visible = this.showJoints;
  }
}
