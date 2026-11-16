import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { map, Observable, Subscription } from 'rxjs';
import { catchError, switchMap, takeWhile, timer } from 'rxjs';
import { ExperimentAnalysisComponent } from './experiment-analysis.component';
import {
  GameGenerationResponse,
  HistoricalFrequencyResponse,
  LotolabApiService,
  OptimizedGameGenerationResponse,
  RecentFrequencyResponse,
  ContestResponse,
} from './lotolab-api.service';

@Component({
  selector: 'app-home',
  imports: [DatePipe, DecimalPipe, FormsModule, RouterLink, ExperimentAnalysisComponent],
  templateUrl: './home.component.html',
  styleUrl: './home.component.css',
})
export class HomeComponent implements OnInit {
  protected readonly windows = [50, 100, 200, 500];
  protected readonly recentWindow = signal(100);
  protected readonly historical = signal<HistoricalFrequencyResponse | null>(null);
  protected readonly recent = signal<RecentFrequencyResponse | null>(null);
  protected readonly historyLoading = signal(true);
  protected readonly recentLoading = signal(true);
  protected readonly historyError = signal('');
  protected readonly recentError = signal('');
  protected readonly games = signal<GameGenerationResponse | null>(null);
  protected readonly gamesLoading = signal(false);
  protected readonly gamesError = signal('');
  protected readonly copyMessage = signal('');
  protected readonly latestContest = signal<ContestResponse | null>(null);
  protected readonly latestLoading = signal(true);
  protected readonly latestError = signal('');

  protected generationMode: 'random' | 'optimized' | 'policy' = 'random';
  protected quantity = 5;
  protected evenNumbers = 8;
  protected oddNumbers = 7;
  protected seed: number | null = null;
  protected targetContestNumber: number | null = null;

  private recentRequest?: Subscription;

  constructor(private readonly api: LotolabApiService) {}

  ngOnInit(): void {
    this.loadHistorical();
    this.loadRecent();
    this.loadLatestContest();
  }

  protected loadLatestContest(): void {
    this.latestLoading.set(true);
    this.latestError.set('');
    this.api.getLatestContest().subscribe({
      next: (contest) => this.latestContest.set(contest),
      error: (error: unknown) => {
        const status = typeof error === 'object' && error !== null && 'status' in error
          ? Number((error as { status: unknown }).status) : -1;
        this.latestError.set(status === 404
          ? 'Ainda não há concursos cadastrados.'
          : this.errorMessage(error));
        this.latestLoading.set(false);
      },
      complete: () => this.latestLoading.set(false),
    });
  }

  protected loadHistorical(): void {
    this.historyLoading.set(true);
    this.historyError.set('');
    this.api.getHistoricalFrequency().subscribe({
      next: (response) => this.historical.set(response),
      error: (error: unknown) => {
        this.historyError.set(this.errorMessage(error));
        this.historyLoading.set(false);
      },
      complete: () => this.historyLoading.set(false),
    });
  }

  protected loadRecent(): void {
    this.recentRequest?.unsubscribe();
    this.recentLoading.set(true);
    this.recentError.set('');
    this.recentRequest = this.api.getRecentFrequency(this.recentWindow()).subscribe({
      next: (response) => this.recent.set(response),
      error: (error: unknown) => {
        this.recentError.set(this.errorMessage(error));
        this.recentLoading.set(false);
      },
      complete: () => this.recentLoading.set(false),
    });
  }

  protected onWindowChange(value: string): void {
    this.recentWindow.set(Number(value));
    this.loadRecent();
  }

  protected generateGames(): void {
    this.gamesError.set('');
    this.copyMessage.set('');
    if (this.generationMode === 'random'
        && (!Number.isInteger(this.quantity) || this.quantity < 1 || this.quantity > 100)) {
      this.gamesError.set('Escolha entre 1 e 100 jogos.');
      return;
    }
    if (this.evenNumbers < 0 || this.oddNumbers < 0 || this.evenNumbers + this.oddNumbers !== 15
        || this.evenNumbers > 12 || this.oddNumbers > 13) {
      this.gamesError.set('Pares e ímpares precisam somar 15 (máximo de 12 pares e 13 ímpares).');
      return;
    }

    this.gamesLoading.set(true);
    this.games.set(null);
    const seed = this.seed === null ? {} : { seed: this.seed };
    const target = this.targetContestNumber === null ? {} : { targetContestNumber: this.targetContestNumber };
    const request = {
      evenNumbers: this.evenNumbers,
      oddNumbers: this.oddNumbers,
      ...seed,
      ...target,
    };
    const request$: Observable<GameGenerationResponse> = this.generationMode === 'optimized'
      ? this.api.generateOptimizedGame(request).pipe(
        map((result: OptimizedGameGenerationResponse): GameGenerationResponse => ({
            games: [result.game],
            criteria: { quantity: 1, ...result.criteria },
            probabilityNotice: result.statisticalNotice,
            backtesting: {
              fitnessScore: result.fitnessScore,
              contestsEvaluated: result.contestsEvaluated,
              iterations: result.iterations,
              trainingFitnessScore: result.trainingFitnessScore,
              trainingContestsEvaluated: result.trainingContestsEvaluated,
              duplicateMutationsSkipped: result.duplicateMutationsSkipped,
              searchExhausted: result.searchExhausted,
              randomBaselineFitnessScore: result.randomBaselineFitnessScore,
              randomBaselineContestsEvaluated: result.randomBaselineContestsEvaluated,
              candidateBudget: result.candidateBudget,
              evidenceStatus: result.evidenceStatus,
              performanceStatus: result.performanceStatus,
              predictionId: result.predictionId,
              walkForwardStrategyScore: result.walkForwardStrategyScore,
              walkForwardBaselineScore: result.walkForwardBaselineScore,
              aiInterpretationStatus: result.aiInterpretationStatus,
              interpretationJobId: result.interpretationJobId,
              aiSummary: result.aiSummary,
              aiHypotheses: result.aiHypotheses,
              aiExplanation: result.aiExplanation,
            },
          })),
      )
      : this.generationMode === 'policy'
        ? this.api.generateByPolicy(request).pipe(map(result => ({
          games: [result.game], criteria: { quantity: 1, evenNumbers: this.evenNumbers,
            oddNumbers: this.oddNumbers, seed: this.seed }, probabilityNotice: result.statisticalNotice,
          policy: { modelVersion: result.modelVersion, strategyKey: result.strategyKey,
            evaluationStatus: result.evaluationStatus, predictionId: result.predictionId,
            interpretationJobId: result.interpretationJobId, interpretationStatus: result.interpretationStatus },
        })))
        : this.api.generateGames({ quantity: this.quantity, ...request });

    request$.subscribe({
      next: (response) => {
        this.games.set(response);
        const jobId = response.backtesting?.interpretationJobId ?? response.policy?.interpretationJobId ?? undefined;
        if (jobId) {
          timer(0, 2000).pipe(
            switchMap(() => this.api.getAgentJob(jobId)),
            takeWhile(job => job.status === 'PENDING' || job.status === 'RUNNING', true),
            catchError(() => []),
          ).subscribe(job => {
            const current = this.games();
            if (!current?.backtesting) return;
            if (current.backtesting) this.games.set({ ...current, backtesting: { ...current.backtesting,
              aiInterpretationStatus: job.interpretationStatus, aiSummary: job.summary, aiExplanation: job.explanation } });
            else if (current.policy) this.games.set({ ...current, policy: { ...current.policy,
              interpretationStatus: job.interpretationStatus } });
          });
        }
      },
      error: (error: unknown) => {
        this.gamesError.set(this.errorMessage(error));
        this.gamesLoading.set(false);
      },
      complete: () => this.gamesLoading.set(false),
    });
  }

  protected async copyGame(numbers: number[], index: number): Promise<void> {
    try {
      await navigator.clipboard.writeText(numbers.join(' '));
      this.copyMessage.set(`Jogo ${index + 1} copiado.`);
    } catch {
      this.copyMessage.set('Não foi possível copiar. Selecione e copie as dezenas manualmente.');
    }
  }

  protected numberClass(number: number): string {
    return number % 2 === 0 ? 'number-chip even' : 'number-chip odd';
  }

  private errorMessage(error: unknown): string {
    if (typeof error === 'object' && error !== null && 'status' in error) {
      const status = Number((error as { status: unknown }).status);
      if (status === 0) return 'Não foi possível conectar à API. Confirme se o backend está executando.';
      if (status >= 500) return 'A API encontrou um erro. Tente novamente em instantes.';
      if (status === 400) return 'A API rejeitou os parâmetros enviados. Revise os valores e tente novamente.';
    }
    return 'Ocorreu um erro inesperado. Tente novamente.';
  }
}
