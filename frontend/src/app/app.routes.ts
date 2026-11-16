import { Routes } from '@angular/router';
import { HomeComponent } from './home.component';
import { RegisterContestComponent } from './register-contest.component';
import { ContestImportComponent } from './contest-import.component';
import { AgentDashboardComponent } from './agent-dashboard.component';
import { MlLabComponent } from './ml-lab.component';

export const routes: Routes = [
  { path: '', component: HomeComponent, title: 'LotoLab — Análise estatística' },
  { path: 'cadastro', component: RegisterContestComponent, title: 'LotoLab — Cadastro de concurso' },
  { path: 'importacao', component: ContestImportComponent, title: 'LotoLab — Importação histórica' },
  { path: 'agentes', component: AgentDashboardComponent, title: 'LotoLab — Agentes experimentais' },
  { path: 'ml', component: MlLabComponent, title: 'LotoLab — Laboratório de Machine Learning' },
  { path: '**', redirectTo: '' },
];
