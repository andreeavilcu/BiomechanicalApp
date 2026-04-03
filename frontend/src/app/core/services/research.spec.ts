import { TestBed } from '@angular/core/testing';

import { Research } from './research';

describe('Research', () => {
  let service: Research;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(Research);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
