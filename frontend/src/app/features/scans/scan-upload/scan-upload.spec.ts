import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ScanUpload } from './scan-upload';

describe('ScanUpload', () => {
  let component: ScanUpload;
  let fixture: ComponentFixture<ScanUpload>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ScanUpload],
    }).compileComponents();

    fixture = TestBed.createComponent(ScanUpload);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
