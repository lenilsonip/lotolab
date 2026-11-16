import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { HomeComponent } from './home.component';
import { LotolabApiService } from './lotolab-api.service';

describe('HomeComponent optimized game generation', () => {
  it('calls optimized API, shows its one game and backtesting summary', () => {
    const generateOptimizedGame = vi.fn(() => of({
      game: Array.from({ length: 15 }, (_, index) => index + 1),
      fitnessScore: 999,
      contestsEvaluated: 3802,
      iterations: 500,
      criteria: { evenNumbers: 8, oddNumbers: 7, seed: 45 },
      statisticalNotice: 'Ajuste histórico apenas.',
    }));
    const generateGames = vi.fn();
    const api = { generateOptimizedGame, generateGames } as unknown as LotolabApiService;
    const component = new HomeComponent(api);
    const state = component as unknown as {
      generationMode: 'random' | 'optimized';
      seed: number;
      generateGames: () => void;
      games: () => {
        games: number[][];
        backtesting?: { fitnessScore: number; contestsEvaluated: number; iterations: number } | null;
      } | null;
      gamesLoading: () => boolean;
    };
    state.generationMode = 'optimized';
    state.seed = 45;

    state.generateGames();

    expect(generateOptimizedGame).toHaveBeenCalledWith({ evenNumbers: 8, oddNumbers: 7, seed: 45 });
    expect(generateGames).not.toHaveBeenCalled();
    expect(state.games()?.games).toHaveLength(1);
    expect(state.games()?.backtesting).toEqual({ fitnessScore: 999, contestsEvaluated: 3802, iterations: 500 });
    expect(state.gamesLoading()).toBe(false);
  });
});
