import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SystemStats } from './system-stats';

describe('SystemStats', () => {
  let component: SystemStats;
  let fixture: ComponentFixture<SystemStats>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SystemStats],
    }).compileComponents();

    fixture = TestBed.createComponent(SystemStats);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
