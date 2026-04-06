import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ScanDetail } from './scan-detail.component';

describe('ScanDetail', () => {
  let component: ScanDetail;
  let fixture: ComponentFixture<ScanDetail>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ScanDetail],
    }).compileComponents();

    fixture = TestBed.createComponent(ScanDetail);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
