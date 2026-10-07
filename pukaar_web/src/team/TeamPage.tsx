// D5 Team and accounts: admins only. Needs the server-side admin function (list users, invite,
// set role, revoke), which is the next build step (docs/build-prompts.md, step 6).
import { useNavigate } from 'react-router-dom';
import { Button, EmptyState } from '../ui/atoms';

export function TeamPage() {
  const navigate = useNavigate();
  return (
    <main className="flex h-full items-center justify-center bg-surface">
      <EmptyState
        icon="group"
        title="Team and accounts comes next"
        action={
          <Button variant="tonal" icon="arrow_back" onClick={() => navigate('/')}>
            Back to operations
          </Button>
        }
      >
        Inviting people and changing roles needs a small admin function on the server. Until then, roles are set with the Admin SDK (see
        server/README.md).
      </EmptyState>
    </main>
  );
}
