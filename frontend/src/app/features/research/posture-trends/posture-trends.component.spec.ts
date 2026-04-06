import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PostureTrends } from './posture-trends.component';

describe('PostureTrends', () => {
  let component: PostureTrends;
  let fixture: ComponentFixture<PostureTrends>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PostureTrends],
    }).compileComponents();

    fixture = TestBed.createComponent(PostureTrends);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
