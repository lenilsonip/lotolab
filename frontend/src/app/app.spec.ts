import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { HomeComponent } from './home.component';
import { LotolabApiService } from './lotolab-api.service';

describe('App', () => {
  const apiStub = {
    getHistoricalFrequency: () => of({ totalContests: 1, frequencies: [] }),
    getRecentFrequency: () => of({ totalContests: 1, analyzedRecentContests: 1, frequencies: [] }),
    getLatestContest: () => of({ number: 1, drawDate: '2026-10-30', drawnNumbers: Array.from({ length: 15 }, (_, index) => index + 1) }),
    generateGames: () => of({
      games: [[1, 2]],
      criteria: { quantity: 1, evenNumbers: 1, oddNumbers: 1, seed: null },
      probabilityNotice: 'Sem previsão.',
    }),
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HomeComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: LotolabApiService, useValue: apiStub },
      ],
    }).compileComponents();
  });

  it('renders the analysis and game generator sections', async () => {
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('h1')?.textContent).toContain('Explore os resultados');
    expect(compiled.querySelector('#frequencias h2')?.textContent).toContain('Frequência das dezenas');
    expect(compiled.querySelector('#gerador h2')?.textContent).toContain('Gerador de jogos');
    expect(compiled.querySelector('.latest-contest')?.textContent).toContain('Concurso 1');
  });
});
