import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { Viewer3dComponent } from './viewer-3d.component';
import { ScanService } from '../../../core/services/scan.service';
import { ProcessingStatus, RiskLevel } from '../../../core/models/scan.model';



function makeVec3() {
  return {
    x: 0, y: 0, z: 0,
    set: vi.fn().mockReturnThis(),
    copy: vi.fn().mockReturnThis(),
    clone: vi.fn().mockReturnThis(),
    addScaledVector: vi.fn().mockReturnThis(),
    sub: vi.fn().mockReturnThis(),
    normalize: vi.fn().mockReturnThis(),
    length: vi.fn().mockReturnValue(1),
    distanceTo: vi.fn().mockReturnValue(1),
    setScalar: vi.fn().mockReturnThis(),
  };
}

function makeGroup() {
  const obj: any = {
    children: [] as any[],
    visible: true,
    position: { y: 0, copy: vi.fn(), x: 0, z: 0 },
    add: vi.fn().mockImplementation(function(c: any) { obj.children.push(c); }),
    remove: vi.fn().mockImplementation(function(c: any) {
      const idx = obj.children.indexOf(c);
      if (idx !== -1) obj.children.splice(idx, 1);
    }),
  };
  return obj;
}

function makeMesh() {
  return {
    position: { copy: vi.fn().mockReturnThis(), addScaledVector: vi.fn().mockReturnThis(), x: 0, y: 0, z: 0 },
    scale: { setScalar: vi.fn() },
    material: { opacity: 0.4, dispose: vi.fn() },
    geometry: { dispose: vi.fn() },
    castShadow: false,
    quaternion: { setFromUnitVectors: vi.fn() },
    lookAt: vi.fn(),
  };
}

vi.mock('three', () => ({
  Clock: vi.fn(function Clock(this: any) { this.getElapsedTime = () => 0; }),
  Group: vi.fn(function Group() { return makeGroup(); }),
  Scene: vi.fn(function Scene(this: any) { this.add = vi.fn(); this.background = null; this.fog = null; }),
  Color: vi.fn(function Color() { return {}; }),
  Fog: vi.fn(function Fog() { return {}; }),
  PerspectiveCamera: vi.fn(function PerspectiveCamera(this: any) {
    this.position = makeVec3();
    this.aspect = 1;
    this.updateProjectionMatrix = vi.fn();
  }),
  WebGLRenderer: vi.fn(function WebGLRenderer(this: any) {
    this.setSize = vi.fn();
    this.setPixelRatio = vi.fn();
    this.render = vi.fn();
    this.dispose = vi.fn();
    this.domElement = document.createElement('canvas');
    this.shadowMap = { enabled: true, type: null };
  }),
  AmbientLight: vi.fn(function AmbientLight() { return {}; }),
  DirectionalLight: vi.fn(function DirectionalLight(this: any) {
    this.position = { set: vi.fn() };
    this.castShadow = false;
  }),
  HemisphereLight: vi.fn(function HemisphereLight() { return {}; }),
  GridHelper: vi.fn(function GridHelper(this: any) {
    this.position = { y: 0 };
    this.material = { transparent: false, opacity: 1 };
  }),
  Vector3: vi.fn(function Vector3(this: any) {
    this.x = 0; this.y = 0; this.z = 0;
    this.set = vi.fn().mockReturnThis();
    this.copy = vi.fn().mockReturnThis();
    this.clone = vi.fn(function(this: any) { return { ...this, clone: vi.fn().mockReturnThis(), copy: vi.fn().mockReturnThis() }; });
    this.addScaledVector = vi.fn().mockReturnThis();
    this.sub = vi.fn().mockReturnThis();
    this.normalize = vi.fn().mockReturnThis();
    this.length = vi.fn().mockReturnValue(1);
    this.distanceTo = vi.fn().mockReturnValue(1);
    this.setScalar = vi.fn().mockReturnThis();
  }),
  Box3: vi.fn(function Box3(this: any) {
    this.expandByPoint = vi.fn();
    this.isEmpty = vi.fn().mockReturnValue(false);
    this.getCenter = vi.fn().mockReturnValue(makeVec3());
    this.getSize = vi.fn().mockReturnValue({ x: 1, y: 2, z: 1 });
  }),
  Mesh: vi.fn(function Mesh() { return makeMesh(); }),
  Points: vi.fn(function Points() {
    return {
      geometry: {
        boundingSphere: { radius: 1, center: makeVec3() },
        computeBoundingSphere: vi.fn(),
      },
    };
  }),
  CylinderGeometry: vi.fn(function CylinderGeometry() { return {}; }),
  SphereGeometry: vi.fn(function SphereGeometry() { return {}; }),
  RingGeometry: vi.fn(function RingGeometry() { return {}; }),
  BufferGeometry: vi.fn(function BufferGeometry(this: any) {
    this.applyMatrix4 = vi.fn();
    this.computeBoundingBox = vi.fn();
    this.computeBoundingSphere = vi.fn();
    this.translate = vi.fn();
    this.boundingBox = { getCenter: vi.fn().mockReturnValue(makeVec3()) };
    this.boundingSphere = { radius: 1, center: makeVec3() };
    this.attributes = { position: { array: new Float32Array([0, 0, 0]) } };
  }),
  MeshStandardMaterial: vi.fn(function MeshStandardMaterial(this: any) { this.dispose = vi.fn(); }),
  MeshBasicMaterial: vi.fn(function MeshBasicMaterial(this: any) { this.dispose = vi.fn(); this.opacity = 0; }),
  PointsMaterial: vi.fn(function PointsMaterial() { return {}; }),
  Matrix4: vi.fn(function Matrix4(this: any) {
    this.makeRotationX = vi.fn().mockReturnThis();
  }),
  PCFSoftShadowMap: 1,
  FrontSide: 0,
  DoubleSide: 2,
}));

vi.mock('three/examples/jsm/controls/OrbitControls.js', () => ({
  OrbitControls: vi.fn(function OrbitControls(this: any) {
    this.enableDamping = true;
    this.dampingFactor = 0;
    this.autoRotate = false;
    this.autoRotateSpeed = 0;
    this.target = { copy: vi.fn().mockReturnThis(), clone: vi.fn().mockReturnValue(makeVec3()) };
    this.update = vi.fn();
    this.dispose = vi.fn();
    this.addEventListener = vi.fn();
  }),
}));

vi.mock('three/examples/jsm/loaders/PLYLoader.js', () => ({
  PLYLoader: vi.fn(function PLYLoader(this: any) {
    this.parse = vi.fn().mockReturnValue({
      applyMatrix4: vi.fn(),
      computeBoundingBox: vi.fn(),
      computeBoundingSphere: vi.fn(),
      translate: vi.fn(),
      boundingBox: { getCenter: vi.fn().mockReturnValue(makeVec3()) },
      boundingSphere: { radius: 1, center: makeVec3() },
      attributes: { position: { array: new Float32Array([0, 0, 0]) } },
    });
  }),
}));



const makeResult = () => ({
  sessionId: 1, scanDate: '2025-01-01T10:00:00', status: ProcessingStatus.COMPLETED,
  errorMessage: null, processingMethod: 'LIDAR', aiConfidenceScore: 0.9, scalingFactor: 1,
  qAngleLeft: 10, qAngleRight: 10, fhpAngle: 5, fhpDistanceCm: 2, shoulderAsymmetryCm: 1,
  stancePhaseLeft: 60, stancePhaseRight: 60, cadence: 100,
  globalPostureScore: 80, riskLevel: RiskLevel.LOW,
  recommendations: [], globalFeedback: '', medicalDisclaimer: false,
  evolution: null, keypoints: [], targetHeightMeters: null,
});

const makeKeypoints = () => [
  { name: 'neck', x: 0, y: 1.5, z: 0 },
  { name: 'pelvis', x: 0, y: 0.9, z: 0 },
  { name: 'l_shoulder', x: -0.2, y: 1.4, z: 0 },
  { name: 'r_shoulder', x: 0.2, y: 1.4, z: 0 },
  { name: 'l_hip', x: -0.1, y: 0.9, z: 0 },
  { name: 'r_hip', x: 0.1, y: 0.9, z: 0 },
  { name: 'l_knee', x: -0.1, y: 0.5, z: 0 },
  { name: 'r_knee', x: 0.1, y: 0.5, z: 0 },
  { name: 'l_ankle', x: -0.1, y: 0.1, z: 0 },
  { name: 'r_ankle', x: 0.1, y: 0.1, z: 0 },
];

describe('Viewer3dComponent', () => {
  let component: Viewer3dComponent;
  let fixture: ComponentFixture<Viewer3dComponent>;
  let mockScanService: { getSession: ReturnType<typeof vi.fn>; getPointCloud: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    mockScanService = {
      getSession: vi.fn().mockReturnValue(of(makeResult())),
      getPointCloud: vi.fn().mockReturnValue(of(new ArrayBuffer(0))),
    };

    (globalThis as any).ResizeObserver = vi.fn(function ResizeObserver(this: any) {
      this.observe = vi.fn();
      this.disconnect = vi.fn();
    });

    await TestBed.configureTestingModule({
      imports: [Viewer3dComponent],
      providers: [{ provide: ScanService, useValue: mockScanService }],
    }).compileComponents();

    fixture = TestBed.createComponent(Viewer3dComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('initializes with default state', () => {
    expect(component.showJoints).toBe(true);
    expect(component.showPointCloud).toBe(false);
    expect(component.pointCloudLoading).toBe(false);
    expect(component.pointCloudLoaded).toBe(false);
  });

  it('ngAfterViewInit initializes scene and builds visualization', () => {
    component.keypoints = makeKeypoints();
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('toggleJoints toggles showJoints', () => {
    fixture.detectChanges();
    component.toggleJoints();
    expect(component.showJoints).toBe(false);
    component.toggleJoints();
    expect(component.showJoints).toBe(true);
  });

  it('togglePointCloud enables point cloud and triggers load', () => {
    fixture.detectChanges();
    component.sessionId = 1;
    component.showPointCloud = false;
    (component as any).pointCloudLoaded = false;
    (component as any).pointCloudError = false;
    component.togglePointCloud();
    expect(component.showPointCloud).toBe(true);
  });

  it('togglePointCloud turns off point cloud and restores state', () => {
    fixture.detectChanges();
    component.showPointCloud = true;
    (component as any).savedSkeletonState = { skeleton: true, joints: true };
    (component as any).savedCameraForSkeleton = {
      pos: { copy: vi.fn() },
      target: { copy: vi.fn() },
    };
    component.togglePointCloud();
    expect(component.showPointCloud).toBe(false);
  });

  it('togglePointCloud shows already loaded point cloud without reloading', () => {
    fixture.detectChanges();
    component.showPointCloud = false;
    (component as any).pointCloudLoaded = true;
    component.togglePointCloud();
    expect(mockScanService.getPointCloud).not.toHaveBeenCalled();
  });

  it('resetCamera with point cloud loaded calls fitCameraToPointCloud', () => {
    fixture.detectChanges();
    component.showPointCloud = true;
    (component as any).pointCloudLoaded = true;
    const spy = vi.spyOn(component as any, 'fitCameraToPointCloud');
    component.resetCamera();
    expect(spy).toHaveBeenCalled();
  });

  it('resetCamera without point cloud fits camera to keypoints', () => {
    component.keypoints = makeKeypoints();
    fixture.detectChanges();
    component.showPointCloud = false;
    const spy = vi.spyOn(component as any, 'fitCamera');
    component.resetCamera();
    expect(spy).toHaveBeenCalled();
  });

  it('ngOnChanges rebuilds visualization when keypoints change', () => {
    fixture.detectChanges();
    const spy = vi.spyOn(component as any, 'buildVisualization');
    component.ngOnChanges({
      keypoints: { currentValue: makeKeypoints(), previousValue: [], firstChange: false, isFirstChange: () => false }
    });
    expect(spy).toHaveBeenCalled();
  });

  it('ngOnChanges handles sessionId change by clearing point cloud state', () => {
    fixture.detectChanges();
    component.sessionId = 0 as any;
    component.ngOnChanges({
      sessionId: { currentValue: 0, previousValue: 1, firstChange: false, isFirstChange: () => false }
    });
    expect(component.pointCloudLoaded).toBe(false);
    expect(component.pointCloudError).toBe(false);
    expect(component.showPointCloud).toBe(false);
  });

  it('ngOnDestroy cleans up without errors', () => {
    fixture.detectChanges();
    expect(() => component.ngOnDestroy()).not.toThrow();
  });

  it('loads point cloud data from scanService on first toggle', () => {
    fixture.detectChanges();
    component.sessionId = 1;
    component.showPointCloud = false;
    (component as any).pointCloudLoaded = false;
    (component as any).pointCloudError = false;
    component.togglePointCloud();
    expect(mockScanService.getPointCloud).toHaveBeenCalledWith(1);
  });

  it('sets pointCloudError on getPointCloud failure', () => {
    mockScanService.getPointCloud.mockReturnValue(throwError(() => new Error('Network error')));
    fixture.detectChanges();
    component.sessionId = 1;
    component.showPointCloud = false;
    (component as any).pointCloudLoaded = false;
    (component as any).pointCloudError = false;
    component.togglePointCloud();
    expect(component.pointCloudError).toBe(true);
    expect(component.pointCloudLoading).toBe(false);
  });

  it('ngAfterViewInit with sessionId calls togglePointCloud', () => {
    component.sessionId = 1;
    const spy = vi.spyOn(component, 'togglePointCloud').mockImplementation(() => {});
    fixture.detectChanges();
    expect(spy).toHaveBeenCalled();
  });
});
