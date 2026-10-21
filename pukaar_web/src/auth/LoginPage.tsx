// D1 Login: email and password, then the role decides what happens. Signed in without a role
// gets a clear "no access yet" with Check again and Sign out. Errors use `error`, never SOS red.
import { sendPasswordResetEmail, signInWithEmailAndPassword } from 'firebase/auth';
import { FirebaseError } from 'firebase/app';
import { useState, type FormEvent, type InputHTMLAttributes } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { auth } from '../lib/firebase';
import { DISTRICT } from '../lib/format';
import { Button, Icon, PukaarMark, Spinner, cx } from '../ui/atoms';
import { useAuth } from './auth';

function messageFor(e: unknown) {
  const code = e instanceof FirebaseError ? e.code : '';
  if (['auth/invalid-credential', 'auth/wrong-password', 'auth/user-not-found', 'auth/invalid-email'].includes(code))
    return "That email and password don't match. Check them and try again.";
  if (code === 'auth/too-many-requests') return 'Too many tries. Wait a few minutes, or reset your password.';
  if (code === 'auth/network-request-failed') return "Couldn't reach the sign-in service. Check the internet connection and try again.";
  if (code === 'auth/user-disabled') return 'This account has been switched off. Ask your district admin.';
  return 'Sign-in failed. Try again in a moment.';
}

export function LoginPage() {
  const { user, role } = useAuth();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from || '/';

  if (user === undefined) return <Spinner label="Checking your sign-in…" />;
  if (user && role) return <Navigate to={from} replace />;

  return (
    <main className="relative flex min-h-full flex-col items-center justify-center overflow-hidden bg-surface-container-lowest p-8">
      <Ripples />
      <div className="relative w-[400px] rounded-section bg-surface-container p-8">
        <header className="mb-6 flex items-center gap-3">
          <span className="flex size-12 items-center justify-center rounded-card bg-primary-container text-on-primary-container">
            <PukaarMark size={30} />
          </span>
          <div>
            <h1 className="text-dialog-title font-semibold">Pukaar</h1>
            <p className="text-on-surface-variant">Rescuer dashboard</p>
          </div>
        </header>
        {user ? <NoRole email={user.email ?? ''} /> : <SignInForm />}
      </div>
      <p className="relative mt-6 text-caption text-on-surface-variant">{DISTRICT} district · Bihar State Disaster Management Authority</p>
    </main>
  );
}

function SignInForm() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [info, setInfo] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError('');
    setInfo('');
    setBusy(true);
    try {
      await signInWithEmailAndPassword(auth, email.trim(), password);
    } catch (err) {
      setError(messageFor(err));
    } finally {
      setBusy(false);
    }
  }

  async function forgot() {
    setError('');
    if (!email.trim()) {
      setError('Type your email first, then choose Forgot password.');
      return;
    }
    try {
      await sendPasswordResetEmail(auth, email.trim());
      setInfo(`If ${email.trim()} has an account, a reset link is on its way.`);
    } catch (err) {
      setError(messageFor(err));
    }
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
      <Field label="Email" type="email" value={email} onChange={setEmail} autoComplete="username" invalid={!!error} autoFocus />
      <Field label="Password" type="password" value={password} onChange={setPassword} autoComplete="current-password" invalid={!!error} />
      {error && (
        <p role="alert" className="flex items-start gap-2 text-list text-error">
          <Icon name="error" size={18} fill />
          {error}
        </p>
      )}
      {info && <p className="text-list text-on-surface-variant">{info}</p>}
      <Button type="submit" className="h-12" disabled={busy || !email || !password}>
        {busy ? 'Signing in…' : 'Sign in'}
      </Button>
      <div className="flex items-center justify-between text-list">
        <button type="button" onClick={forgot} className="rounded-chip px-1 font-semibold text-primary hover:underline">
          Forgot password?
        </button>
        <span className="text-on-surface-variant">Access is given by your district admin</span>
      </div>
    </form>
  );
}

function NoRole({ email }: { email: string }) {
  const { refreshRole, signOut } = useAuth();
  const [checking, setChecking] = useState(false);
  const [stillNone, setStillNone] = useState(false);

  async function check() {
    setChecking(true);
    const role = await refreshRole().catch(() => null);
    setChecking(false);
    setStillNone(!role);
  }

  return (
    <div className="flex flex-col gap-4">
      <span className="flex size-10 items-center justify-center rounded-full bg-warning-container text-on-warning-container">
        <Icon name="lock_person" size={22} />
      </span>
      <h2 className="text-panel-title font-semibold">You're signed in, but don't have access yet</h2>
      <p className="text-on-surface-variant">
        {email} has no role on this dashboard. Ask your district admin to add you as a viewer or responder, then check again.
      </p>
      {stillNone && <p className="text-list text-on-surface-variant">Still no role. It can take a minute after the admin adds you.</p>}
      <div className="flex gap-2">
        <Button icon="refresh" onClick={check} disabled={checking}>
          {checking ? 'Checking…' : 'Check again'}
        </Button>
        <Button variant="text" onClick={() => signOut()}>
          Sign out
        </Button>
      </div>
    </div>
  );
}

function Field({
  label,
  value,
  onChange,
  invalid,
  ...rest
}: { label: string; value: string; onChange: (v: string) => void; invalid?: boolean } & Omit<InputHTMLAttributes<HTMLInputElement>, 'onChange' | 'value'>) {
  return (
    <label className="flex flex-col gap-1">
      <span className="text-list font-semibold text-on-surface-variant">{label}</span>
      <input
        {...rest}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        aria-invalid={invalid || undefined}
        className={cx(
          'h-12 rounded-chip border bg-surface-container-lowest px-3 text-body text-on-surface outline-none',
          'focus:border-primary focus:ring-1 focus:ring-primary',
          invalid ? 'border-error' : 'border-outline',
        )}
      />
    </label>
  );
}

/** Faint ripple rings behind the card: the call spreading outward. */
function Ripples() {
  return (
    <div aria-hidden className="pointer-events-none absolute inset-0 flex items-center justify-center">
      {[360, 560, 760, 960, 1160].map((d) => (
        <span key={d} className="absolute rounded-full border border-outline-variant opacity-40" style={{ width: d, height: d }} />
      ))}
    </div>
  );
}
