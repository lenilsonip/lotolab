import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LotolabApiService } from './lotolab-api.service';

export function parsePredictedNumbers(raw: string): number[] {
  return parseLotofacilNumbers(raw, 'previstas');
}

export function parseLotofacilNumbers(raw: string, label = 'informadas'): number[] {
  const trimmed = raw.trim();
  if (!trimmed) {
    throw new Error(`Informe as 15 dezenas ${label}.`);
  }

  const values = trimmed.split(/[\s,;]+/);
  if (values.length !== 15) {
    throw new Error(`Informe exatamente 15 dezenas ${label}, separadas por vírgula ou espaço.`);
  }
  if (values.some((value) => !/^\d+$/.test(value))) {
    throw new Error(`Use somente números inteiros para as dezenas ${label}.`);
  }

  const numbers = values.map(Number);
  if (numbers.some((number) => number < 1 || number > 25)) {
    throw new Error(`As dezenas ${label} precisam estar entre 1 e 25.`);
  }
  if (new Set(numbers).size !== 15) {
    throw new Error(`As 15 dezenas ${label} precisam ser distintas.`);
  }
  return numbers.sort((left, right) => left - right);
}

@Component({
  selector: 'app-experiment-analysis',
  imports: [FormsModule],
  templateUrl: './experiment-analysis.component.html',
  styleUrl: './experiment-analysis.component.css',
})
export class ExperimentAnalysisComponent {
  protected predictedNumbersInput = '';
  protected actualContestNumber: number | null = null;
  protected readonly loading = signal(false);
  protected readonly errorMessage = signal('');
  protected readonly aiFeedback = signal('');
  protected readonly promptText = signal('');
  protected readonly promptCopyMessage = signal('');
  protected readonly savingPrediction = signal(false);
  protected readonly predictionSaveMessage = signal('');
  protected readonly predictionSaveError = signal('');

  constructor(private readonly api: LotolabApiService) {}

  protected analyze(): void {
    this.errorMessage.set('');
    this.aiFeedback.set('');
    this.promptText.set('');
    this.promptCopyMessage.set('');

    let predictedNumbers: number[];
    try {
      predictedNumbers = parsePredictedNumbers(this.predictedNumbersInput);
    } catch (error: unknown) {
      this.errorMessage.set(error instanceof Error ? error.message : 'Revise as dezenas informadas.');
      return;
    }

    const actualContestNumber = this.actualContestNumber;
    if (actualContestNumber === null || !Number.isInteger(actualContestNumber) || actualContestNumber <= 0) {
      this.errorMessage.set('Informe um número de concurso positivo.');
      return;
    }

    this.loading.set(true);
    this.api.analyzeExperiment({
      predictedNumbers,
      actualContestNumber,
    }).subscribe({
      next: (response) => {
        this.promptText.set(response.promptText);
        this.aiFeedback.set(response.aiFeedback);
      },
      error: (error: unknown) => {
        this.errorMessage.set(this.apiErrorMessage(error));
        this.loading.set(false);
      },
      complete: () => this.loading.set(false),
    });
  }

  protected async copyPrompt(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.promptText());
      this.promptCopyMessage.set('Prompt copiado.');
    } catch {
      this.promptCopyMessage.set('Não foi possível copiar. Selecione e copie o prompt manualmente.');
    }
  }

  protected savePrediction(): void {
    this.predictionSaveMessage.set('');
    this.predictionSaveError.set('');

    let predictedNumbers: number[];
    try {
      predictedNumbers = parsePredictedNumbers(this.predictedNumbersInput);
    } catch (error: unknown) {
      this.predictionSaveError.set(error instanceof Error ? error.message : 'Revise as dezenas informadas.');
      return;
    }
    const targetContestNumber = this.actualContestNumber;
    if (targetContestNumber === null || !Number.isInteger(targetContestNumber) || targetContestNumber <= 0) {
      this.predictionSaveError.set('Informe um número de concurso alvo positivo.');
      return;
    }

    this.savingPrediction.set(true);
    this.api.savePredictionAttempt({ targetContestNumber, predictedNumbers }).subscribe({
      next: () => this.predictionSaveMessage.set('Previsão salva com sucesso!'),
      error: (error: unknown) => {
        this.predictionSaveError.set(this.predictionErrorMessage(error));
        this.savingPrediction.set(false);
      },
      complete: () => this.savingPrediction.set(false),
    });
  }

  private predictionErrorMessage(error: unknown): string {
    const status = typeof error === 'object' && error !== null && 'status' in error
      ? Number((error as { status: unknown }).status) : -1;
    const body = typeof error === 'object' && error !== null && 'error' in error
      ? (error as { error: unknown }).error : null;
    const serverMessage = typeof body === 'object' && body !== null && 'message' in body
      ? String((body as { message: unknown }).message) : '';
    if (status === 0) return 'Não foi possível conectar à API para salvar a previsão.';
    if (status === 400) return serverMessage || 'A API rejeitou os dados da previsão. Confira o concurso e as dezenas.';
    if (status >= 500) return 'A API encontrou um erro ao salvar a previsão. Tente novamente.';
    return serverMessage || 'Não foi possível salvar a previsão.';
  }

  private apiErrorMessage(error: unknown): string {
    const status = typeof error === 'object' && error !== null && 'status' in error
      ? Number((error as { status: unknown }).status)
      : -1;
    if (status === 0) return 'Não foi possível conectar à API. Confirme se o backend está executando.';
    if (status === 400) return 'A API rejeitou a solicitação. Confira as 15 dezenas e o número do concurso.';
    if (status === 404) return 'O concurso informado não foi encontrado.';
    if (status === 502) return 'Serviço Gemini temporariamente indisponível. Verifique sua chave de API e tente novamente.';
    if (status === 503) return 'Limite temporário do Gemini atingido. Tente novamente mais tarde.';
    if (status >= 500) return 'A API encontrou um erro. Tente novamente em instantes.';
    return 'Ocorreu um erro inesperado ao solicitar a análise.';
  }
}
