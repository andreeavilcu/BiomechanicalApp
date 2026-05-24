import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Viewer3dComponent } from './viewer-3d.component';

describe('Viewer3dComponent', () => {
  let component: Viewer3dComponent;
  let fixture: ComponentFixture<Viewer3dComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Viewer3dComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(Viewer3dComponent);
    component = fixture.componentInstance;
    // detectChanges() is intentionally omitted — calling it triggers ngAfterViewInit
    // which attempts to create a WebGLRenderer, unavailable in the jsdom test environment.
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
