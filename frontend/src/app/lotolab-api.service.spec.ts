import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { LotolabApiService } from './lotolab-api.service';

describe('LotolabApiService', () => {
  let api: LotolabApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(LotolabApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('uses the historical frequency contract', () => {
    api.getHistoricalFrequency().subscribe((response) => {
      expect(response.totalContests).toBe(3802);
      expect(response.frequencies[0]).toEqual({ number: 5, occurrences: 2292, percentage: 60.2841 });
    });

    const request = http.expectOne('/api/analises/frequencia-dezenas');
    expect(request.request.method).toBe('GET');
    request.flush({ totalContests: 3802, frequencies: [{ number: 5, occurrences: 2292, percentage: 60.2841 }] });
  });

  it('sends the recent contest window using the backend query parameter', () => {
    api.getRecentFrequency(200).subscribe((response) => expect(response.analyzedRecentContests).toBe(200));

    const request = http.expectOne('/api/analises/frequencia-recente?concursos=200');
    expect(request.request.method).toBe('GET');
    request.flush({ totalContests: 3802, analyzedRecentContests: 200, frequencies: [] });
  });

  it('sends the game generation request matching the backend DTO', () => {
    const body = { quantity: 2, evenNumbers: 8, oddNumbers: 7, seed: 42 };
    api.generateGames(body).subscribe((response) => expect(response.games).toHaveLength(2));

    const request = http.expectOne('/api/jogos/geracao');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(body);
    request.flush({ games: [[], []], criteria: body, probabilityNotice: 'Equiprovável.' });
  });

  it('sends optimized generation criteria to the backtesting endpoint', () => {
    const body = { evenNumbers: 8, oddNumbers: 7, seed: 42, targetContestNumber: 3803 };
    api.generateOptimizedGame(body).subscribe((response) => {
      expect(response.fitnessScore).toBe(1234);
      expect(response.contestsEvaluated).toBe(3802);
      expect(response.targetContestNumber).toBe(3803);
      expect(response.evidenceStatus).toBe('NO_ELIGIBLE_EVIDENCE');
    });

    const request = http.expectOne('/api/experimentos/gerar-otimizado');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(body);
    request.flush({
      game: Array.from({ length: 15 }, (_, index) => index + 1),
      fitnessScore: 1234,
      contestsEvaluated: 3802,
      iterations: 500,
      predictionId: 59,
      targetContestNumber: 3803,
      sourceCutoffContest: 3802,
      targetWasOverridden: true,
      strategyVersion: 'mutation-1',
      evidenceStatus: 'NO_ELIGIBLE_EVIDENCE',
      performanceStatus: 'NO_DEMONSTRATED_ADVANTAGE',
      evidenceIds: [],
      randomBaselineFitnessScore: 700,
      randomBaselineContestsEvaluated: 760,
      candidateBudget: 501,
      randomBaselineSeed: 123,
      walkForwardStrategyScore: 600,
      walkForwardBaselineScore: 550,
      walkForwardFolds: [],
      criteria: body,
      statisticalNotice: 'Pontuação retrospectiva.',
    });
  });

  it('loads saved prediction history including evaluated hits', () => {
    api.getPredictionHistory().subscribe((items) => expect(items[0].hits).toBe(12));
    const request = http.expectOne('/api/previsoes');
    expect(request.request.method).toBe('GET');
    request.flush([{ id: 59, targetContestNumber: 3803, createdAt: '2026-11-01T12:00:00',
      predictedNumbers: [], source: 'OPTIMIZED', simulation: false, strategyVersion: 'mutation-1',
      experimentalScore: 800, baselineScore: 700, evidenceStatus: 'NO_ELIGIBLE_EVIDENCE',
      performanceStatus: 'NO_DEMONSTRATED_ADVANTAGE',
      sourceCutoffContest: 3802,
      walkForwardStrategyScore: 300,
      walkForwardBaselineScore: 400,
      actualContestNumber: 3803, hits: 12, status: 'EVALUATED' }]);
  });

  it('treats only a missing latest job as an empty state and preserves real errors', () => {
    let absent: unknown = 'not-set';
    api.getLatestAgentJob().subscribe(value => absent = value);
    http.expectOne(request => request.url === '/api/agentes/jobs/latest'
      && request.params.get('agentKey') === 'PATTERN_RESEARCHER').flush(null, { status: 404, statusText: 'Not Found' });
    expect(absent).toBeNull();

    let failureStatus = 0;
    api.getLatestAgentJob().subscribe({ error: error => failureStatus = error.status });
    http.expectOne(request => request.url === '/api/agentes/jobs/latest'
      && request.params.get('agentKey') === 'PATTERN_RESEARCHER').flush({}, { status: 503, statusText: 'Unavailable' });
    expect(failureStatus).toBe(503);
  });

  it('triggers pattern research and loads its versioned evidence', () => {
    api.minePatterns().subscribe((result) => expect(result.insightsWritten).toBe(1));
    const run = http.expectOne('/api/agentes/pesquisador/minerar');
    expect(run.request.method).toBe('POST');
    run.flush({ insightsWritten: 1, message: 'Mineração concluída.', insights: [] });

    api.getLatestPatternInsights().subscribe((insights) => expect(insights[0].evidenceStatus).toBe('EXPERIMENTAL'));
    const list = http.expectOne('/api/agentes/pesquisador/insights');
    expect(list.request.method).toBe('GET');
    list.flush([{ id: 1, hypothesisKey: 'ODD_COUNT:fold-1', algorithmVersion: 'walk-forward-1',
      patternType: 'ODD_COUNT', description: 'Teste', confidenceScore: 0, trainingFirstContest: 1,
      trainingLastContest: 10, validationFirstContest: 11, validationLastContest: 15, sampleSize: 5,
      observedMetric: 1, baselineMetric: 2, evidenceStatus: 'EXPERIMENTAL', evidenceJson: '{}' }]);
  });

  it('loads the latest researcher interpretation', () => {
    api.getLatestResearchInterpretation().subscribe((value) => {
      expect(value?.status).toBe('SUCCEEDED');
      expect(value?.hypotheses).toContain('Testar outra janela.');
    });
    const request = http.expectOne('/api/agentes/pesquisador/interpretacao');
    expect(request.request.method).toBe('GET');
    request.flush({ id: 1, agentRunId: 2, predictionAttemptId: null, agentKey: 'PATTERN_RESEARCHER',
      provider: 'GOOGLE_GEMINI', model: 'gemini-3.6-flash', promptVersion: 'v1', status: 'SUCCEEDED',
      summary: 'Interpretação descritiva.', hypotheses: ['Testar outra janela.'], explanation: 'Sem alegar previsão.',
      statisticalInputJson: '{}', errorCategory: null, createdAt: '2026-11-03T12:00:00Z' });
  });

  it('posts experiment comparison data to the backend contract', () => {
    const body = { predictedNumbers: Array.from({ length: 15 }, (_, index) => index + 1), actualContestNumber: 3802 };
    api.analyzeExperiment(body).subscribe((response) => {
      expect(response.promptText).toBe('Prompt preparado.');
      expect(response.aiFeedback).toBe('Análise da IA preparada.');
    });

    const request = http.expectOne('/api/experimentos/analise');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(body);
    request.flush({ promptText: 'Prompt preparado.', aiFeedback: 'Análise da IA preparada.' });
  });

  it('fetches the latest contest from the backend contract', () => {
    api.getLatestContest().subscribe((response) => {
      expect(response.number).toBe(3802);
      expect(response.drawDate).toBe('2026-10-11');
    });
    const request = http.expectOne('/api/concursos/ultimo');
    expect(request.request.method).toBe('GET');
    request.flush({ number: 3802, drawDate: '2026-10-11', drawnNumbers: Array.from({ length: 15 }, (_, i) => i + 1) });
  });

  it('posts contest registration using the ContestResponse contract', () => {
    const body = { number: 3803, drawDate: '2026-10-31', drawnNumbers: Array.from({ length: 15 }, (_, i) => i + 1) };
    api.registerContest(body).subscribe((response) => expect(response).toEqual(body));
    const request = http.expectOne('/api/concursos');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(body);
    request.flush(body);
  });

  it('saves a prediction attempt using its own endpoint and DTO', () => {
    const body = { targetContestNumber: 3803, predictedNumbers: Array.from({ length: 15 }, (_, i) => i + 1) };
    api.savePredictionAttempt(body).subscribe((response) => {
      expect(response.id).toBe(41);
      expect(response.targetContestNumber).toBe(3803);
    });
    const request = http.expectOne('/api/previsoes');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(body);
    request.flush({ ...body, id: 41, createdAt: '2026-10-31T18:00:00' });
  });

  it('sends an import preview as multipart form data with the local admin token', () => {
    const file = new File(['csv'], 'history.csv', { type: 'text/csv' });
    api.previewContestImport(file, 'local-token').subscribe((response) => expect(response.wouldInsert).toBe(1));
    const request = http.expectOne('/api/admin/importacao-concursos/preview');
    expect(request.request.method).toBe('POST');
    expect(request.request.headers.get('X-LotoLab-Import-Token')).toBe('local-token');
    expect(request.request.body instanceof FormData).toBe(true);
    expect((request.request.body as FormData).get('file')).toBe(file);
    request.flush({ linesRead: 1, validLines: 1, wouldInsert: 1, alreadyExisting: 0, conflictLines: 0,
      rejectedLines: 0, fileStructureRejected: false, rejections: [], conflicts: [], warnings: [] });
  });

  it('posts an explicit confirmation flag for the import', () => {
    const file = new File(['csv'], 'history.csv', { type: 'text/csv' });
    api.importContests(file, 'local-token').subscribe((response) => expect(response.newContests).toBe(1));
    const request = http.expectOne('/api/admin/importacao-concursos');
    expect(request.request.method).toBe('POST');
    expect(request.request.headers.get('X-LotoLab-Import-Token')).toBe('local-token');
    expect((request.request.body as FormData).get('confirm')).toBe('true');
    request.flush({ linesRead: 1, newContests: 1, alreadyExisting: 0, rejectedLines: 0, conflictLines: 0,
      notProcessedLines: 0, fileStructureRejected: false, persistenceAborted: false,
      rejections: [], conflicts: [], warnings: [] });
  });
});
