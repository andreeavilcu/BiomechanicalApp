import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AggregateMetrics } from './aggregate-metrics.component';

describe('AggregateMetrics', () => {
  let component: AggregateMetrics;
  let fixture: ComponentFixture<AggregateMetrics>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AggregateMetrics],
    }).compileComponents();

    fixture = TestBed.createComponent(AggregateMetrics);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
