import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { MlLabComponent } from './ml-lab.component';

describe('MlLabComponent', () => {
  let http: HttpTestingController;
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [MlLabComponent], providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('loads persisted model summaries and reports an empty model list', () => {
    const fixture = TestBed.createComponent(MlLabComponent);
    fixture.detectChanges();
    http.expectOne(request => request.url.endsWith('/ml/modelos')).flush([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Nenhum modelo treinado está persistido');
  });

  it('requires explicit acknowledgment before enqueueing training', () => {
    const fixture = TestBed.createComponent(MlLabComponent);
    fixture.detectChanges();
    http.expectOne(request => request.url.endsWith('/ml/modelos')).flush([]);
    const button = fixture.nativeElement.querySelector('section button') as HTMLButtonElement;
    expect(button.disabled).toBe(true);
    http.expectNone(request => request.url.endsWith('/ml/treinamentos'));
  });
});
