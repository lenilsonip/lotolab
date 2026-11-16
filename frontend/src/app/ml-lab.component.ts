import { Component, OnDestroy, OnInit, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subscription, timer, switchMap, takeWhile } from 'rxjs';
import { LotolabApiService, MlModel, MlModelSummary, MlPrediction, MlTrainingJob } from './lotolab-api.service';

@Component({
  selector: 'app-ml-lab',
  imports: [FormsModule, RouterLink, DecimalPipe],
  templateUrl: './ml-lab.component.html',
  styleUrl: './ml-lab.component.css',
})
export class MlLabComponent implements OnInit, OnDestroy {
  protected readonly models = signal<MlModelSummary[]>([]);
  protected readonly selectedModel = signal<MlModel | null>(null);
  protected readonly job = signal<MlTrainingJob | null>(null);
  protected readonly prediction = signal<MlPrediction | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal('');
  protected readonly message = signal('');
  protected cutoff = '';
  protected seed = '20261112';
  protected target = '';
  protected evenNumbers = 8;
  protected oddNumbers = 7;
  protected confirmed = false;
  private polling?: Subscription;

  constructor(private readonly api: LotolabApiService) {}

  ngOnInit(): void { this.loadModels(); }
  ngOnDestroy(): void { this.polling?.unsubscribe(); }

  protected loadModels(): void {
    this.loading.set(true); this.error.set('');
    this.api.getMlModels().subscribe({
      next: models => { this.models.set(models); this.loading.set(false); },
      error: () => { this.error.set('Não foi possível carregar os modelos. Verifique se a API está ativa.'); this.loading.set(false); },
    });
  }

  protected train(): void {
    if (!this.confirmed) { this.error.set('Confirme que o treinamento usa dados históricos e é experimental.'); return; }
    this.loading.set(true); this.error.set(''); this.message.set(''); this.prediction.set(null);
    const request = { ...(this.cutoff ? { trainingCutoffExclusive: Number(this.cutoff) } : {}),
      ...(this.seed ? { seed: Number(this.seed) } : {}) };
    this.api.enqueueMlTraining(request).subscribe({
      next: job => { this.job.set(job); this.loading.set(false); this.message.set(`Treinamento enfileirado como job #${job.id}.`); this.pollJob(job.id); },
      error: () => { this.loading.set(false); this.error.set('Não foi possível enfileirar o treinamento. Confira o cutoff e o histórico disponível.'); },
    });
  }

  protected openModel(summary: MlModelSummary): void {
    this.error.set(''); this.selectedModel.set(null);
    this.api.getMlModel(summary.id).subscribe({
      next: model => this.selectedModel.set(model),
      error: () => this.error.set(`Não foi possível recarregar o modelo #${summary.id} e validar seus parâmetros.`),
    });
  }

  protected predict(): void {
    const model = this.selectedModel();
    if (!model) { this.error.set('Selecione e carregue um modelo antes de gerar a previsão experimental.'); return; }
    if (this.evenNumbers + this.oddNumbers !== 15 || this.evenNumbers < 0 || this.oddNumbers < 0) {
      this.error.set('A soma de pares e ímpares deve ser exatamente 15.'); return;
    }
    this.loading.set(true); this.error.set(''); this.prediction.set(null);
    this.api.generateExperimentalPrediction({ modelId: model.id, evenNumbers: this.evenNumbers, oddNumbers: this.oddNumbers,
      ...(this.target ? { targetContestNumber: Number(this.target) } : {}), ...(this.seed ? { seed: Number(this.seed) } : {}) }).subscribe({
      next: value => { this.prediction.set(value); this.loading.set(false); },
      error: () => { this.loading.set(false); this.error.set('A inferência falhou. O alvo deve ser futuro em relação ao histórico e ao cutoff do modelo.'); },
    });
  }

  private pollJob(id: number): void {
    this.polling?.unsubscribe();
    this.polling = timer(0, 2000).pipe(switchMap(() => this.api.getMlTrainingJob(id)),
      takeWhile(value => value.status === 'PENDING' || value.status === 'RUNNING', true)).subscribe({
      next: value => this.job.set(value),
      complete: () => {
        const current = this.job();
        if (current?.status === 'SUCCEEDED') {
          this.message.set(`Job #${id} concluído. Modelo #${current.modelId} persistido.`); this.loadModels();
        } else if (current?.status === 'FAILED') this.error.set(`Job #${id} falhou (${current.errorCategory ?? 'erro sem categoria'}).`);
      },
      error: () => this.error.set('Não foi possível acompanhar o job; atualize a página e consulte o estado pelo ID exibido.'),
    });
  }
}
