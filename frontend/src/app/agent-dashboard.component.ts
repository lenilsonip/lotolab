import { Component, OnDestroy, OnInit, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { catchError, forkJoin, of, Subscription, switchMap, takeWhile, timer } from 'rxjs';
import { AgentInterpretation, AgentJob, LearningStatus, LotolabApiService, PatternInsight, PredictionHistoryItem } from './lotolab-api.service';

@Component({
  selector: 'app-agent-dashboard',
  imports: [DatePipe, DecimalPipe, RouterLink],
  templateUrl: './agent-dashboard.component.html',
  styleUrl: './agent-dashboard.component.css',
})
export class AgentDashboardComponent implements OnInit, OnDestroy {
  protected readonly insights = signal<PatternInsight[]>([]);
  protected readonly predictions = signal<PredictionHistoryItem[]>([]);
  protected readonly interpretation = signal<AgentInterpretation | null>(null);
  protected readonly loading = signal(true);
  protected readonly mining = signal(false);
  protected readonly error = signal('');
  protected readonly message = signal('');
  protected readonly job = signal<AgentJob | null>(null);
  protected readonly learning = signal<LearningStatus | null>(null);
  private polling?: Subscription;

  constructor(private readonly api: LotolabApiService) {}

  ngOnInit(): void { this.refresh(); }
  ngOnDestroy(): void { this.polling?.unsubscribe(); }

  protected refresh(): void {
    this.loading.set(true);
    this.error.set('');
    forkJoin({
      insights: this.api.getLatestPatternInsights(),
      predictions: this.api.getPredictionHistory(),
      interpretation: this.api.getLatestResearchInterpretation().pipe(catchError(() => of(null))),
      job: this.api.getLatestAgentJob(),
      learning: this.api.getLearningStatus(),
    })
      .subscribe({
        next: (result) => {
          this.insights.set(result.insights);
          this.predictions.set(result.predictions);
          this.interpretation.set(result.interpretation);
          this.job.set(result.job);
          this.learning.set(result.learning);
          if (result.job && (result.job.status === 'PENDING' || result.job.status === 'RUNNING')) this.pollJob();
        },
        error: () => this.error.set('Não foi possível carregar as informações dos agentes.'),
        complete: () => this.loading.set(false),
      });
  }

  protected mine(): void {
    this.mining.set(true);
    this.error.set('');
    this.message.set('');
    this.api.minePatterns().subscribe({
      next: (result) => {
        this.message.set(result.message);
        this.insights.set(result.insights);
        this.api.getLatestAgentJob().subscribe({
          next: job => {
            this.job.set(job);
            if (job && (job.status === 'PENDING' || job.status === 'RUNNING')) this.pollJob();
          },
          error: () => this.error.set('Não foi possível consultar o job de interpretação.'),
        });
      },
      error: () => this.error.set('A mineração falhou. Verifique a API e tente novamente.'),
      complete: () => this.mining.set(false),
    });
  }

  private pollJob(): void {
    this.polling?.unsubscribe();
    this.polling = timer(0, 2000).pipe(
      switchMap(() => this.api.getLatestAgentJob()),
      takeWhile(job => job !== null && (job.status === 'PENDING' || job.status === 'RUNNING'), true),
    ).subscribe({
      next: job => this.job.set(job),
      complete: () => this.api.getLatestResearchInterpretation().pipe(catchError(() => of(null)))
        .subscribe(value => this.interpretation.set(value)),
      error: () => this.error.set('Não foi possível consultar o job de interpretação.'),
    });
  }
}
