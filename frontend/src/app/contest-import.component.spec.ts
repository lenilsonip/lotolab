import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { ContestImportComponent } from './contest-import.component';
import { LotolabApiService } from './lotolab-api.service';

describe('ContestImportComponent', () => {
  let previewContestImport: ReturnType<typeof vi.fn>;
  let importContests: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    previewContestImport = vi.fn();
    importContests = vi.fn();
    await TestBed.configureTestingModule({
      imports: [ContestImportComponent],
      providers: [provideRouter([]), { provide: LotolabApiService, useValue: { previewContestImport, importContests } }],
    }).compileComponents();
  });

  function setup() {
    const fixture = TestBed.createComponent(ContestImportComponent);
    const component = fixture.componentInstance as unknown as {
      token: { set(value: string): void };
      selectedFile: () => File | null;
      preview: () => { wouldInsert: number; fileStructureRejected: boolean } | null;
      report: () => { newContests: number } | null;
      confirming: () => boolean;
      errorMessage: () => string;
      onFileSelected: (event: Event) => void;
      requestPreview: () => void;
      beginConfirmation: () => void;
      confirmImport: () => void;
    };
    return { fixture, component };
  }

  it('previews the CSV, requires a separate confirmation action, and reports the import', () => {
    const { component } = setup();
    const file = new File(['valid csv'], 'history.csv', { type: 'text/csv' });
    component.token.set('local-token');
    component.onFileSelected({ target: { files: [file], value: 'history.csv' } } as unknown as Event);
    previewContestImport.mockReturnValueOnce(of({ linesRead: 1, validLines: 1, wouldInsert: 1,
      alreadyExisting: 0, conflictLines: 0, rejectedLines: 0, fileStructureRejected: false,
      rejections: [], conflicts: [], warnings: [] }));
    component.requestPreview();
    expect(previewContestImport).toHaveBeenCalledWith(file, 'local-token');
    expect(component.preview()?.wouldInsert).toBe(1);
    expect(component.confirming()).toBe(false);
    component.beginConfirmation();
    expect(component.confirming()).toBe(true);

    importContests.mockReturnValueOnce(of({ linesRead: 1, newContests: 1, alreadyExisting: 0,
      rejectedLines: 0, conflictLines: 0, notProcessedLines: 0, fileStructureRejected: false,
      persistenceAborted: false, rejections: [], conflicts: [], warnings: [] }));
    component.confirmImport();
    expect(importContests).toHaveBeenCalledWith(file, 'local-token');
    expect(component.report()?.newContests).toBe(1);
    expect(component.preview()).toBeNull();
  });

  it('rejects non-CSV and oversized files in the browser', () => {
    const { component } = setup();
    component.onFileSelected({ target: { files: [new File(['x'], 'history.xlsx')], value: 'history.xlsx' } } as unknown as Event);
    expect(component.selectedFile()).toBeNull();
    expect(component.errorMessage()).toContain('.csv');

    const large = new File([new Uint8Array(1024 * 1024 + 1)], 'large.csv');
    component.onFileSelected({ target: { files: [large], value: 'large.csv' } } as unknown as Event);
    expect(component.selectedFile()).toBeNull();
    expect(component.errorMessage()).toContain('1 MB');
  });

  it('does not request a preview without a selected file and admin token', () => {
    const { component } = setup();
    component.requestPreview();
    expect(previewContestImport).not.toHaveBeenCalled();
    expect(component.errorMessage()).toContain('token administrativo');
  });

  it('shows authorization failures without attempting to import', () => {
    const { component } = setup();
    const file = new File(['valid csv'], 'history.csv');
    component.token.set('wrong');
    component.onFileSelected({ target: { files: [file], value: 'history.csv' } } as unknown as Event);
    previewContestImport.mockReturnValueOnce(throwError(() => ({ status: 403 })));
    component.requestPreview();
    expect(component.errorMessage()).toContain('Token administrativo inválido');
    expect(importContests).not.toHaveBeenCalled();
  });
});
