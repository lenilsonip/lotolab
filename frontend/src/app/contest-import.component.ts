import { Component, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ContestImportPreview, ContestImportReport, ImportIssue, LotolabApiService } from './lotolab-api.service';

@Component({
  selector: 'app-contest-import',
  imports: [RouterLink],
  templateUrl: './contest-import.component.html',
  styleUrl: './contest-import.component.css',
})
export class ContestImportComponent {
  protected readonly token = signal('');
  protected readonly selectedFile = signal<File | null>(null);
  protected readonly preview = signal<ContestImportPreview | null>(null);
  protected readonly report = signal<ContestImportReport | null>(null);
  protected readonly confirming = signal(false);
  protected readonly loading = signal(false);
  protected readonly importing = signal(false);
  protected readonly errorMessage = signal('');

  constructor(private readonly api: LotolabApiService) {}

  protected onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    this.selectedFile.set(file);
    this.preview.set(null);
    this.report.set(null);
    this.confirming.set(false);
    this.errorMessage.set('');
    if (file && file.size > 1024 * 1024) {
      this.selectedFile.set(null);
      input.value = '';
      this.errorMessage.set('O arquivo deve ter no máximo 1 MB.');
    } else if (file && !file.name.toLowerCase().endsWith('.csv')) {
      this.selectedFile.set(null);
      input.value = '';
      this.errorMessage.set('Selecione um arquivo com extensão .csv.');
    }
  }

  protected requestPreview(): void {
    const file = this.selectedFile();
    if (!file || !this.token().trim()) {
      this.errorMessage.set('Selecione um CSV e informe o token administrativo local.');
      return;
    }
    this.errorMessage.set('');
    this.preview.set(null);
    this.report.set(null);
    this.confirming.set(false);
    this.loading.set(true);
    this.api.previewContestImport(file, this.token().trim()).subscribe({
      next: value => {
        this.preview.set(value);
        if (value.fileStructureRejected) this.errorMessage.set('O arquivo não passou pela validação estrutural. Nenhum dado foi gravado.');
      },
      error: error => { this.loading.set(false); this.errorMessage.set(this.apiErrorMessage(error)); },
      complete: () => this.loading.set(false),
    });
  }

  protected beginConfirmation(): void {
    if (this.preview() && !this.preview()?.fileStructureRejected) this.confirming.set(true);
  }

  protected confirmImport(): void {
    const file = this.selectedFile();
    if (!file || !this.preview() || !this.confirming()) return;
    this.importing.set(true);
    this.errorMessage.set('');
    this.api.importContests(file, this.token().trim()).subscribe({
      next: value => {
        this.report.set(value);
        this.preview.set(null);
        this.confirming.set(false);
      },
      error: error => { this.importing.set(false); this.errorMessage.set(this.apiErrorMessage(error)); },
      complete: () => this.importing.set(false),
    });
  }

  protected cancelConfirmation(): void { this.confirming.set(false); }

  protected issueGroups(data: ContestImportPreview): { title: string; issues: ImportIssue[] }[] {
    return [
      { title: 'Linhas rejeitadas', issues: data.rejections },
      { title: 'Conflitos com dados existentes', issues: data.conflicts },
      { title: 'Avisos para revisão', issues: data.warnings },
    ];
  }

  protected reportGroups(data: ContestImportReport): { title: string; issues: ImportIssue[] }[] {
    return [
      { title: 'Linhas rejeitadas', issues: data.rejections },
      { title: 'Conflitos', issues: data.conflicts },
      { title: 'Avisos', issues: data.warnings },
    ];
  }

  private apiErrorMessage(error: unknown): string {
    const status = typeof error === 'object' && error !== null && 'status' in error
      ? Number((error as { status: unknown }).status) : -1;
    if (status === 0) return 'Não foi possível conectar à API. Confirme se o backend está em execução.';
    if (status === 403) return 'Token administrativo inválido ou acesso fora do computador local.';
    if (status === 503) return 'Importação administrativa desabilitada. Configure LOTOLAB_IMPORT_TOKEN no ambiente do backend.';
    if (status === 413) return 'O arquivo excede o limite de 1 MB.';
    if (status === 400) return 'A solicitação foi rejeitada. Revise o arquivo e a confirmação de importação.';
    return 'A importação falhou. Nenhum resultado foi atualizado; revise o relatório ou tente novamente.';
  }
}
