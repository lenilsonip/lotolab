import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, catchError, of, throwError } from 'rxjs';
import { environment } from '../environments/environment';

export interface HistoricalFrequency {
  number: number;
  occurrences: number;
  percentage: number;
}

export interface HistoricalFrequencyResponse {
  totalContests: number;
  frequencies: HistoricalFrequency[];
}

export interface RecentFrequency {
  number: number;
  recentOccurrences: number;
  recentPercentage: number;
  historicalOccurrences: number;
  historicalPercentage: number;
  differencePercentagePoints: number;
}

export interface RecentFrequencyResponse {
  totalContests: number;
  analyzedRecentContests: number;
  frequencies: RecentFrequency[];
}

export interface GenerateGamesRequest {
  quantity: number;
  evenNumbers?: number;
  oddNumbers?: number;
  seed?: number;
}

export interface GameGenerationResponse {
  games: number[][];
  criteria: {
    quantity: number;
    evenNumbers: number | null;
    oddNumbers: number | null;
    seed: number | null;
  };
  probabilityNotice: string;
  policy?: { modelVersion: number; strategyKey: string; evaluationStatus: string; predictionId: number;
    interpretationJobId: number | null; interpretationStatus: string };
  backtesting?: {
    fitnessScore: number;
    contestsEvaluated: number;
    iterations: number;
    trainingFitnessScore: number;
    trainingContestsEvaluated: number;
    duplicateMutationsSkipped: number;
    searchExhausted: boolean;
    randomBaselineFitnessScore?: number;
    randomBaselineContestsEvaluated?: number;
    candidateBudget?: number;
    evidenceStatus?: string;
    performanceStatus?: string;
    predictionId?: number;
    walkForwardStrategyScore?: number;
    walkForwardBaselineScore?: number;
    aiInterpretationStatus?: string;
    interpretationJobId?: number;
    aiSummary?: string | null;
    aiHypotheses?: string[];
    aiExplanation?: string | null;
  } | null;
}

export interface OptimizedGameGenerationResponse {
  game: number[];
  fitnessScore: number;
  contestsEvaluated: number;
  iterations: number;
  trainingFitnessScore: number;
  trainingContestsEvaluated: number;
  duplicateMutationsSkipped: number;
  searchExhausted: boolean;
  predictionId: number;
  targetContestNumber: number;
  sourceCutoffContest: number;
  targetWasOverridden: boolean;
  strategyVersion: string;
  evidenceStatus: string;
  performanceStatus: string;
  evidenceIds: number[];
  randomBaselineFitnessScore: number;
  randomBaselineContestsEvaluated: number;
  candidateBudget: number;
  randomBaselineSeed: number;
  walkForwardStrategyScore: number;
  walkForwardBaselineScore: number;
  walkForwardFolds: {
    foldNumber: number; trainingFirstContest: number; trainingLastContest: number;
    validationFirstContest: number; validationLastContest: number; trainingSampleSize: number;
    validationSampleSize: number; strategyScore: number; randomBaselineScore: number;
    randomSeed: number; candidateBudget: number;
  }[];
  criteria: {
    evenNumbers: number | null;
    oddNumbers: number | null;
    seed: number | null;
  };
  statisticalNotice: string;
  aiInterpretationStatus: string;
  interpretationJobId: number;
  aiSummary: string | null;
  aiHypotheses: string[];
  aiExplanation: string | null;
}

export interface OptimizedGameRequest {
  evenNumbers?: number;
  oddNumbers?: number;
  seed?: number;
  targetContestNumber?: number;
}

export interface PolicyGameResponse {
  game: number[]; predictionId: number; targetContestNumber: number; modelVersion: number; strategyKey: string;
  evaluationStatus: string; experimentalScore: number | null; interpretationJobId: number | null;
  interpretationStatus: string; statisticalNotice: string;
}

export interface PredictionHistoryItem {
  id: number;
  targetContestNumber: number;
  createdAt: string;
  predictedNumbers: number[];
  source: string;
  simulation: boolean;
  strategyVersion: string | null;
  experimentalScore: number | null;
  baselineScore: number | null;
  evidenceStatus: string;
  performanceStatus: string;
  sourceCutoffContest: number | null;
  walkForwardStrategyScore: number | null;
  walkForwardBaselineScore: number | null;
  actualContestNumber: number | null;
  hits: number | null;
  aiInterpretationStatus: string | null;
  aiSummary: string | null;
  aiExplanation: string | null;
  aiHypotheses: string[];
  status: 'PENDING' | 'EVALUATED' | 'SIMULATED';
}

export interface AgentInterpretation {
  id: number;
  agentRunId: number | null;
  predictionAttemptId: number | null;
  agentKey: string;
  provider: string;
  model: string;
  promptVersion: string;
  status: string;
  summary: string | null;
  hypotheses: string[];
  explanation: string | null;
  statisticalInputJson: string;
  errorCategory: string | null;
  createdAt: string;
}

export interface AgentJob {
  id: number; status: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'SKIPPED' | 'CANCELLED';
  attempts: number; maxAttempts: number; interpretationId: number; interpretationStatus: string;
  summary: string | null; explanation: string | null; errorCategory: string | null; createdAt: string;
}

export interface LearningStatus {
  championModelId: number; version: number; status: string; strategyKey: string; sampleSize: number;
  meanHits: number; varianceHits: number; firstContest: number | null; lastContest: number | null;
  learningSampleSize: number; learningMeanHits: number; learningVarianceHits: number;
  candidateStatus: string | null; candidateStrategyKey: string | null;
  scientificNotice: string;
  recentEvents: { id: number; attemptId: number; contestNumber: number; observedHits: number; sampleSize: number;
    meanBefore: number; meanAfter: number; lower95: number; upper95: number; decision: string; reason: string; createdAt: string }[];
}

export interface PatternInsight {
  id: number;
  hypothesisKey: string;
  algorithmVersion: string;
  patternType: string;
  description: string;
  confidenceScore: number;
  trainingFirstContest: number | null;
  trainingLastContest: number | null;
  validationFirstContest: number | null;
  validationLastContest: number | null;
  sampleSize: number | null;
  observedMetric: number | null;
  baselineMetric: number | null;
  evidenceStatus: string;
  evidenceJson: string;
}

export interface ExperimentAnalysisRequest {
  predictedNumbers: number[];
  actualContestNumber: number;
}

export interface ExperimentAnalysisResponse {
  promptText: string;
  aiFeedback: string;
}

export interface PredictionAttemptRequest {
  targetContestNumber: number;
  predictedNumbers: number[];
}

export interface PredictionAttemptResponse extends PredictionAttemptRequest {
  id: number;
  createdAt: string;
}

export interface ContestResponse {
  number: number;
  drawDate: string;
  drawnNumbers: number[];
}

export type ContestRegistrationRequest = ContestResponse;

export interface ImportIssue {
  lineNumber: number;
  message: string;
}

export interface ContestImportPreview {
  linesRead: number;
  validLines: number;
  wouldInsert: number;
  alreadyExisting: number;
  conflictLines: number;
  rejectedLines: number;
  fileStructureRejected: boolean;
  rejections: ImportIssue[];
  conflicts: ImportIssue[];
  warnings: ImportIssue[];
}

export interface ContestImportReport {
  linesRead: number;
  newContests: number;
  alreadyExisting: number;
  rejectedLines: number;
  conflictLines: number;
  notProcessedLines: number;
  fileStructureRejected: boolean;
  persistenceAborted: boolean;
  rejections: ImportIssue[];
  conflicts: ImportIssue[];
  warnings: ImportIssue[];
}

export interface MlTrainingRequest { trainingCutoffExclusive?: number; seed?: number; }
export interface MlTrainingJob {
  id: number; status: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'; trainingCutoffExclusive: number;
  datasetChecksum: string; seed: number; attempts: number; maxAttempts: number; modelId: number | null;
  errorCategory: string | null; createdAt: string; updatedAt: string;
}
export interface MlModelSummary {
  id: number; version: number; status: string; algorithmVersion: string; featureSchemaVersion: string;
  checksum: string; trainingCutoffExclusive: number; trainingFirstContest: number; trainingLastContest: number;
  trainingContestCount: number; trainingExampleCount: number; createdAt: string;
}
export interface MlEvaluationSlice {
  split: string; trainingFirstContest: number; trainingLastContest: number; targetFirstContest: number;
  targetLastContest: number; sampleCount: number; meanModelHits: number; meanUniformHits: number;
  meanPairedDifference: number; standardError: number; confidence95Lower: number; confidence95Upper: number;
  modelHitDistribution: number[]; uniformHitDistribution: number[]; seed: number;
}
export interface MlModel {
  id: number; version: number; status: string; checksum: string;
  artifact: { featureSchemaVersion: string; trainingCutoffExclusive: number; trainingFirstContest: number;
    trainingLastContest: number; trainingContestCount: number; trainingExampleCount: number;
    seed: number; epochs: number; learningRate: number; l2: number; numberModels: { number: number; intercept: number; coefficients: number[] }[] };
  evaluations: MlEvaluationSlice[]; createdAt: string;
}
export interface MlPredictionRequest { modelId: number; targetContestNumber?: number; evenNumbers?: number; oddNumbers?: number; seed?: number; }
export interface MlPrediction {
  predictionId: number; modelId: number; modelVersion: number; targetContestNumber: number;
  trainingCutoffExclusive: number; numbers: number[]; uniformBaselineNumbers: number[];
  rankingScores: number[]; seed: number; createdAt: string; status: string; notice: string;
}

@Injectable({ providedIn: 'root' })
export class LotolabApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiBaseUrl;

  getHistoricalFrequency(): Observable<HistoricalFrequencyResponse> {
    return this.http.get<HistoricalFrequencyResponse>(`${this.baseUrl}/analises/frequencia-dezenas`);
  }

  getRecentFrequency(contests: number): Observable<RecentFrequencyResponse> {
    return this.http.get<RecentFrequencyResponse>(`${this.baseUrl}/analises/frequencia-recente`, {
      params: { concursos: contests },
    });
  }

  generateGames(request: GenerateGamesRequest): Observable<GameGenerationResponse> {
    return this.http.post<GameGenerationResponse>(`${this.baseUrl}/jogos/geracao`, request);
  }

  generateOptimizedGame(request: OptimizedGameRequest): Observable<OptimizedGameGenerationResponse> {
    return this.http.post<OptimizedGameGenerationResponse>(`${this.baseUrl}/experimentos/gerar-otimizado`, request);
  }

  generateByPolicy(request: OptimizedGameRequest): Observable<PolicyGameResponse> {
    return this.http.post<PolicyGameResponse>(`${this.baseUrl}/experimentos/gerar-politica`, request);
  }

  analyzeExperiment(request: ExperimentAnalysisRequest): Observable<ExperimentAnalysisResponse> {
    return this.http.post<ExperimentAnalysisResponse>(`${this.baseUrl}/experimentos/analise`, request);
  }

  getLatestContest(): Observable<ContestResponse> {
    return this.http.get<ContestResponse>(`${this.baseUrl}/concursos/ultimo`);
  }

  registerContest(payload: ContestRegistrationRequest): Observable<ContestResponse> {
    return this.http.post<ContestResponse>(`${this.baseUrl}/concursos`, payload);
  }

  savePredictionAttempt(request: PredictionAttemptRequest): Observable<PredictionAttemptResponse> {
    return this.http.post<PredictionAttemptResponse>(`${this.baseUrl}/previsoes`, request);
  }

  getPredictionHistory(): Observable<PredictionHistoryItem[]> {
    return this.http.get<PredictionHistoryItem[]>(`${this.baseUrl}/previsoes`);
  }

  minePatterns(): Observable<{ insightsWritten: number; message: string; insights: PatternInsight[] }> {
    return this.http.post<{ insightsWritten: number; message: string; insights: PatternInsight[] }>(
      `${this.baseUrl}/agentes/pesquisador/minerar`, {});
  }

  getLatestPatternInsights(): Observable<PatternInsight[]> {
    return this.http.get<PatternInsight[]>(`${this.baseUrl}/agentes/pesquisador/insights`);
  }

  getLatestResearchInterpretation(): Observable<AgentInterpretation | null> {
    return this.http.get<AgentInterpretation>(`${this.baseUrl}/agentes/pesquisador/interpretacao`);
  }

  getAgentJob(jobId: number): Observable<AgentJob> {
    return this.http.get<AgentJob>(`${this.baseUrl}/agentes/jobs/${jobId}`);
  }

  getLatestAgentJob(agentKey = 'PATTERN_RESEARCHER'): Observable<AgentJob | null> {
    return this.http.get<AgentJob>(`${this.baseUrl}/agentes/jobs/latest`, { params: { agentKey } }).pipe(
      catchError(error => error instanceof HttpErrorResponse && error.status === 404
        ? of(null)
        : throwError(() => error)),
    );
  }

  getLearningStatus(): Observable<LearningStatus> {
    return this.http.get<LearningStatus>(`${this.baseUrl}/agentes/aprendizagem`);
  }

  previewContestImport(file: File, token: string): Observable<ContestImportPreview> {
    const body = new FormData();
    body.append('file', file);
    return this.http.post<ContestImportPreview>(`${this.baseUrl}/admin/importacao-concursos/preview`, body, {
      headers: { 'X-LotoLab-Import-Token': token },
    });
  }

  importContests(file: File, token: string): Observable<ContestImportReport> {
    const body = new FormData();
    body.append('file', file);
    body.append('confirm', 'true');
    return this.http.post<ContestImportReport>(`${this.baseUrl}/admin/importacao-concursos`, body, {
      headers: { 'X-LotoLab-Import-Token': token },
    });
  }

  enqueueMlTraining(request: MlTrainingRequest = {}): Observable<MlTrainingJob> {
    return this.http.post<MlTrainingJob>(`${this.baseUrl}/ml/treinamentos`, request);
  }
  getMlTrainingJob(id: number): Observable<MlTrainingJob> {
    return this.http.get<MlTrainingJob>(`${this.baseUrl}/ml/treinamentos/${id}`);
  }
  getMlModels(): Observable<MlModelSummary[]> {
    return this.http.get<MlModelSummary[]>(`${this.baseUrl}/ml/modelos`);
  }
  getMlModel(id: number): Observable<MlModel> {
    return this.http.get<MlModel>(`${this.baseUrl}/ml/modelos/${id}`);
  }
  generateExperimentalPrediction(request: MlPredictionRequest): Observable<MlPrediction> {
    return this.http.post<MlPrediction>(`${this.baseUrl}/ml/previsoes-experimentais`, request);
  }
}
