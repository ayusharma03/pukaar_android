// Placeholder until D1 Login is built (pukaar_web/docs/build-prompts.md, step 2).
import { usingEmulators } from './lib/firebase';

export function App() {
  return (
    <main className="flex h-full items-center justify-center bg-surface-container-lowest">
      <div className="flex flex-col items-center gap-2 rounded-section bg-surface-container p-8">
        <img src="/pukaar-mark.svg" alt="" width={48} height={48} />
        <h1 className="text-screen-title font-semibold">Pukaar</h1>
        <p className="text-on-surface-variant">Rescuer dashboard: set-up only, screens come next.</p>
        {usingEmulators && <p className="text-caption text-on-surface-variant">Using the local Firebase emulators</p>}
      </div>
    </main>
  );
}
