import { TestBed } from '@angular/core/testing';
import { Observable, Subject } from 'rxjs';
import { vi } from 'vitest';
import { ExperimentAnalysisComponent, parsePredictedNumbers } from './experiment-analysis.component';
import { ExperimentAnalysisRequest, ExperimentAnalysisResponse, LotolabApiService, PredictionAttemptResponse } from './lotolab-api.service';

describe('parsePredictedNumbers', () => {
  it('parses comma- and whitespace-separated numbers and sorts them', () => {
    expect(parsePredictedNumbers('15, 1 14, 2 13, 3 12, 4 11, 5 10, 6 9, 7 8'))
      .toEqual(Array.from({ length: 15 }, (_, index) => index + 1));
  });

  it('rejects the wrong count, duplicates and numbers outside the valid range', () => {
    expect(() => parsePredictedNumbers('1, 2, 3')).toThrow('exatamente 15');
    expect(() => parsePredictedNumbers('1 1 3 4 5 6 7 8 9 10 11 12 13 14 15')).toThrow('distintas');
    expect(() => parsePredictedNumbers('0 2 3 4 5 6 7 8 9 10 11 12 13 14 15')).toThrow('entre 1 e 25');
  });
});

describe('ExperimentAnalysisComponent', () => {
  const validRequest: ExperimentAnalysisRequest = {
    predictedNumbers: Array.from({ length: 15 }, (_, index) => index + 1),
    actualContestNumber: 3802,
  };
  let request$: Subject<ExperimentAnalysisResponse>;
  let analyzeExperiment: ReturnType<typeof vi.fn>;
  let savePredictionAttempt: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    request$ = new Subject<ExperimentAnalysisResponse>();
    analyzeExperiment = vi.fn((_request: ExperimentAnalysisRequest): Observable<ExperimentAnalysisResponse> => request$);
    savePredictionAttempt = vi.fn();
    await TestBed.configureTestingModule({
      imports: [ExperimentAnalysisComponent],
      providers: [{ provide: LotolabApiService, useValue: { analyzeExperiment, savePredictionAttempt } }],
    }).compileComponents();
  });

  it('sends the parsed values, shows loading and displays the AI feedback on success', () => {
    const fixture = TestBed.createComponent(ExperimentAnalysisComponent);
    const component = fixture.componentInstance as unknown as {
      predictedNumbersInput: string;
      actualContestNumber: number;
      analyze: () => void;
      loading: () => boolean;
    };
    component.predictedNumbersInput = '15, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14';
    component.actualContestNumber = 3802;

    component.analyze();

    expect(analyzeExperiment).toHaveBeenCalledWith(validRequest);
    expect(component.loading()).toBe(true);
    request$.next({ promptText: 'Prompt para análise.', aiFeedback: 'Resultado da análise da IA.\nSegunda linha.' });
    request$.complete();
    fixture.detectChanges();
    expect(component.loading()).toBe(false);
    expect((fixture.nativeElement as HTMLElement).querySelector<HTMLElement>('.ai-feedback')?.textContent)
      .toContain('Resultado da análise da IA.\nSegunda linha.');
  });

  it('displays the prompt for manual use when the API has no Gemini key', () => {
    const fixture = TestBed.createComponent(ExperimentAnalysisComponent);
    const component = fixture.componentInstance as unknown as {
      predictedNumbersInput: string; actualContestNumber: number; analyze: () => void;
    };
    component.predictedNumbersInput = validRequest.predictedNumbers.join(' ');
    component.actualContestNumber = validRequest.actualContestNumber;
    component.analyze();
    request$.next({ promptText: 'Prompt para usar manualmente.', aiFeedback: '' });
    request$.complete();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector<HTMLTextAreaElement>('#analysisPrompt')?.value)
      .toBe('Prompt para usar manualmente.');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('GEMINI_API_KEY');
  });

  it('shows a friendly message for bad request and missing contest responses', () => {
    const fixture = TestBed.createComponent(ExperimentAnalysisComponent);
    const component = fixture.componentInstance as unknown as {
      predictedNumbersInput: string;
      actualContestNumber: number;
      analyze: () => void;
      errorMessage: () => string;
    };
    component.predictedNumbersInput = validRequest.predictedNumbers.join(', ');
    component.actualContestNumber = validRequest.actualContestNumber;

    analyzeExperiment.mockReturnValueOnce(new Observable((subscriber) => subscriber.error({ status: 400 })));
    component.analyze();
    expect(component.errorMessage()).toContain('rejeitou a solicitação');

    analyzeExperiment.mockReturnValueOnce(new Observable((subscriber) => subscriber.error({ status: 404 })));
    component.analyze();
    expect(component.errorMessage()).toContain('não foi encontrado');

    analyzeExperiment.mockReturnValueOnce(new Observable((subscriber) => subscriber.error({ status: 502 })));
    component.analyze();
    expect(component.errorMessage()).toContain('Serviço Gemini temporariamente indisponível');

    analyzeExperiment.mockReturnValueOnce(new Observable((subscriber) => subscriber.error({ status: 503 })));
    component.analyze();
    expect(component.errorMessage()).toContain('Limite temporário do Gemini');
  });

  it('does not call the API when the local input is invalid', () => {
    const fixture = TestBed.createComponent(ExperimentAnalysisComponent);
    const component = fixture.componentInstance as unknown as {
      predictedNumbersInput: string;
      actualContestNumber: number;
      analyze: () => void;
      errorMessage: () => string;
    };
    component.predictedNumbersInput = '1, 2, 3';
    component.actualContestNumber = 3802;

    component.analyze();

    expect(analyzeExperiment).not.toHaveBeenCalled();
    expect(component.errorMessage()).toContain('exatamente 15');
  });

  it('saves the same parsed values as a prediction attempt and reports success', () => {
    const fixture = TestBed.createComponent(ExperimentAnalysisComponent);
    const component = fixture.componentInstance as unknown as {
      predictedNumbersInput: string;
      actualContestNumber: number;
      savePrediction: () => void;
      predictionSaveMessage: () => string;
    };
    component.predictedNumbersInput = [...validRequest.predictedNumbers].reverse().join(' ');
    component.actualContestNumber = 3802;
    const response: PredictionAttemptResponse = {
      id: 41,
      targetContestNumber: 3802,
      createdAt: '2026-10-31T18:00:00',
      predictedNumbers: validRequest.predictedNumbers,
    };
    savePredictionAttempt.mockReturnValueOnce(new Observable<PredictionAttemptResponse>((subscriber) => {
      subscriber.next(response);
      subscriber.complete();
    }));

    component.savePrediction();

    expect(savePredictionAttempt).toHaveBeenCalledWith({
      targetContestNumber: validRequest.actualContestNumber,
      predictedNumbers: validRequest.predictedNumbers,
    });
    expect(component.predictionSaveMessage()).toBe('Previsão salva com sucesso!');
  });

  it('does not save when the prediction is invalid and shows API failure', () => {
    const fixture = TestBed.createComponent(ExperimentAnalysisComponent);
    const component = fixture.componentInstance as unknown as {
      predictedNumbersInput: string;
      actualContestNumber: number;
      savePrediction: () => void;
      predictionSaveError: () => string;
    };
    component.predictedNumbersInput = '1 2 3';
    component.actualContestNumber = 3802;
    component.savePrediction();
    expect(savePredictionAttempt).not.toHaveBeenCalled();
    expect(component.predictionSaveError()).toContain('exatamente 15');

    component.predictedNumbersInput = validRequest.predictedNumbers.join(' ');
    savePredictionAttempt.mockReturnValueOnce(new Observable((subscriber) => subscriber.error({ status: 0 })));
    component.savePrediction();
    expect(component.predictionSaveError()).toContain('Não foi possível conectar');
  });
});
