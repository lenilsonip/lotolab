import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, Subject } from 'rxjs';
import { vi } from 'vitest';
import { RegisterContestComponent } from './register-contest.component';
import { ContestRegistrationRequest, ContestResponse, LotolabApiService } from './lotolab-api.service';

describe('RegisterContestComponent', () => {
  let registerContest: ReturnType<typeof vi.fn>;
  let response$: Subject<ContestResponse>;

  beforeEach(async () => {
    response$ = new Subject<ContestResponse>();
    registerContest = vi.fn((_request: ContestRegistrationRequest): Observable<ContestResponse> => response$);
    await TestBed.configureTestingModule({
      imports: [RegisterContestComponent],
      providers: [provideRouter([]), { provide: LotolabApiService, useValue: { registerContest } }],
    }).compileComponents();
  });

  it('parses and submits all values, shows success and clears the form', () => {
    const fixture = TestBed.createComponent(RegisterContestComponent);
    const component = fixture.componentInstance as unknown as {
      contestNumber: number | null;
      drawDate: string;
      drawnNumbersInput: string;
      submit: () => void;
      successMessage: () => string;
    };
    component.contestNumber = 3803;
    component.drawDate = '2026-10-31';
    component.drawnNumbersInput = '15, 1 14, 2 13, 3 12, 4 11, 5 10, 6 9, 7 8';

    component.submit();

    expect(registerContest).toHaveBeenCalledWith({
      number: 3803,
      drawDate: '2026-10-31',
      drawnNumbers: Array.from({ length: 15 }, (_, index) => index + 1),
    });
    response$.next({ number: 3803, drawDate: '2026-10-31', drawnNumbers: [] });
    response$.complete();
    expect(component.successMessage()).toBe('Concurso cadastrado com sucesso!');
    expect(component.contestNumber).toBeNull();
    expect(component.drawDate).toBe('');
    expect(component.drawnNumbersInput).toBe('');
  });

  it('rejects invalid numbers locally without calling the API', () => {
    const fixture = TestBed.createComponent(RegisterContestComponent);
    const component = fixture.componentInstance as unknown as {
      contestNumber: number;
      drawDate: string;
      drawnNumbersInput: string;
      submit: () => void;
      errorMessage: () => string;
    };
    component.contestNumber = 3803;
    component.drawDate = '2026-10-31';
    component.drawnNumbersInput = '1, 2, 3';

    component.submit();

    expect(registerContest).not.toHaveBeenCalled();
    expect(component.errorMessage()).toContain('exatamente 15');
  });

  it('shows the API conflict message', () => {
    const fixture = TestBed.createComponent(RegisterContestComponent);
    const component = fixture.componentInstance as unknown as {
      contestNumber: number;
      drawDate: string;
      drawnNumbersInput: string;
      submit: () => void;
      errorMessage: () => string;
    };
    component.contestNumber = 3803;
    component.drawDate = '2026-10-31';
    component.drawnNumbersInput = Array.from({ length: 15 }, (_, index) => index + 1).join(' ');
    registerContest.mockReturnValueOnce(new Observable((subscriber) => subscriber.error({
      status: 409,
      error: { message: 'O concurso 3803 já existe com conteúdo diferente' },
    })));

    component.submit();

    expect(component.errorMessage()).toContain('já existe com conteúdo diferente');
  });
});
