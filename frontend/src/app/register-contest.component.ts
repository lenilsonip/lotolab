import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { parseLotofacilNumbers } from './experiment-analysis.component';
import { ContestRegistrationRequest, LotolabApiService } from './lotolab-api.service';

@Component({
  selector: 'app-register-contest',
  imports: [FormsModule, RouterLink],
  templateUrl: './register-contest.component.html',
  styleUrl: './register-contest.component.css',
})
export class RegisterContestComponent {
  protected contestNumber: number | null = null;
  protected drawDate = '';
  protected drawnNumbersInput = '';
  protected readonly loading = signal(false);
  protected readonly errorMessage = signal('');
  protected readonly successMessage = signal('');

  constructor(private readonly api: LotolabApiService) {}

  protected submit(): void {
    this.errorMessage.set('');
    this.successMessage.set('');
    if (this.contestNumber === null || !Number.isInteger(this.contestNumber) || this.contestNumber <= 0) {
      this.errorMessage.set('Informe um número de concurso positivo.');
      return;
    }
    if (!this.drawDate || Number.isNaN(Date.parse(`${this.drawDate}T00:00:00`))) {
      this.errorMessage.set('Informe uma data de sorteio válida.');
      return;
    }

    let drawnNumbers: number[];
    try {
      drawnNumbers = parseLotofacilNumbers(this.drawnNumbersInput, 'sorteadas');
    } catch (error: unknown) {
      this.errorMessage.set(error instanceof Error ? error.message : 'Revise as dezenas informadas.');
      return;
    }

    const request: ContestRegistrationRequest = {
      number: this.contestNumber,
      drawDate: this.drawDate,
      drawnNumbers,
    };
    this.loading.set(true);
    this.api.registerContest(request).subscribe({
      next: () => {
        this.successMessage.set('Concurso cadastrado com sucesso!');
        this.contestNumber = null;
        this.drawDate = '';
        this.drawnNumbersInput = '';
      },
      error: (error: unknown) => {
        this.errorMessage.set(this.apiErrorMessage(error));
        this.loading.set(false);
      },
      complete: () => this.loading.set(false),
    });
  }

  private apiErrorMessage(error: unknown): string {
    const status = typeof error === 'object' && error !== null && 'status' in error
      ? Number((error as { status: unknown }).status) : -1;
    const body = typeof error === 'object' && error !== null && 'error' in error
      ? (error as { error: unknown }).error : null;
    const serverMessage = typeof body === 'object' && body !== null && 'message' in body
      ? String((body as { message: unknown }).message) : '';
    if (status === 0) return 'Não foi possível conectar à API. Confirme se o backend está executando.';
    if (status === 409) return serverMessage || 'Já existe um concurso com esse número e conteúdo diferente.';
    if (status === 400) return serverMessage || 'A API rejeitou os dados. Confira o número, a data e as dezenas.';
    if (status >= 500) return 'A API encontrou um erro ao cadastrar o concurso. Tente novamente.';
    return serverMessage || 'Ocorreu um erro inesperado ao cadastrar o concurso.';
  }
}
