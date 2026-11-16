import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AgentDashboardComponent } from './agent-dashboard.component';

describe('AgentDashboardComponent', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AgentDashboardComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('shows research evidence and saved prediction history from the API', () => {
    const fixture = TestBed.createComponent(AgentDashboardComponent);
    fixture.detectChanges();
    http.expectOne('/api/agentes/pesquisador/insights').flush([{
      id: 1, hypothesisKey: 'ODD_COUNT:fold-1', algorithmVersion: 'walk-forward-1', patternType: 'ODD_COUNT',
      description: 'Treino e validação separados', confidenceScore: 0, trainingFirstContest: 1,
      trainingLastContest: 100, validationFirstContest: 101, validationLastContest: 110, sampleSize: 10,
      observedMetric: 1.2, baselineMetric: 1.5, evidenceStatus: 'EXPERIMENTAL', evidenceJson: '{}',
    }]);
    http.expectOne('/api/previsoes').flush([{
      id: 15, targetContestNumber: 3803, createdAt: '2026-11-01T12:00:00', predictedNumbers: [1, 2],
      source: 'OPTIMIZED', simulation: false, strategyVersion: 'mutation-1', experimentalScore: 800,
      baselineScore: 700, evidenceStatus: 'EVIDENCE_USED', performanceStatus: 'NO_DEMONSTRATED_ADVANTAGE',
      sourceCutoffContest: 3802, walkForwardStrategyScore: 300, walkForwardBaselineScore: 400,
      actualContestNumber: null, hits: null, aiInterpretationStatus: 'SKIPPED_DISABLED',
      aiSummary: null, aiExplanation: null, aiHypotheses: [], status: 'PENDING',
    }]);
    http.expectOne('/api/agentes/pesquisador/interpretacao').flush({
      id: 1, agentRunId: 1, predictionAttemptId: null, agentKey: 'PATTERN_RESEARCHER',
      provider: 'GOOGLE_GEMINI', model: 'gemini-3.6-flash', promptVersion: 'statistical-interpretation-v1',
      status: 'SUCCEEDED', summary: 'Resumo cauteloso das métricas observadas.',
      hypotheses: ['Testar estabilidade em janelas distintas.'], explanation: 'A evidência não demonstra vantagem futura.',
      statisticalInputJson: '{}', errorCategory: null, createdAt: '2026-11-03T12:00:00Z',
    });
    http.expectOne(request => request.url === '/api/agentes/jobs/latest'
      && request.params.get('agentKey') === 'PATTERN_RESEARCHER').flush({
      id: 8, status: 'SUCCEEDED', attempts: 1, maxAttempts: 4, interpretationId: 1,
      interpretationStatus: 'SUCCEEDED', summary: 'Resumo cauteloso das métricas observadas.',
      explanation: 'Limitação descritiva.', errorCategory: null, createdAt: '2026-11-03T12:00:00Z',
    });
    http.expectOne('/api/agentes/aprendizagem').flush({
      championModelId: 1, version: 1, status: 'CHAMPION', strategyKey: 'UNIFORM', sampleSize: 0,
      meanHits: 9, varianceHits: 1.5, firstContest: null, lastContest: null,
      learningSampleSize: 0, learningMeanHits: 9, learningVarianceHits: 1.5,
      candidateStatus: null, candidateStrategyKey: null,
      scientificNotice: 'Sem vantagem demonstrada.', recentEvents: [],
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('ODD_COUNT');
    expect(fixture.nativeElement.textContent).toContain('3803');
    expect(fixture.nativeElement.textContent).toContain('PENDING');
    expect(fixture.nativeElement.textContent).toContain('Resumo cauteloso');
  });
});
