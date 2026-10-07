// Firebase Auth state and the user's role (custom claim set by an admin on the server).
import { onAuthStateChanged, signOut as fbSignOut, type User } from 'firebase/auth';
import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { auth } from '../lib/firebase';
import type { Role } from '../lib/types';
import { Spinner } from '../ui/atoms';

interface AuthState {
  /** undefined while Firebase is still restoring the session */
  user: User | null | undefined;
  role: Role | null;
  name: string;
  /** Fetches a fresh token, so a role an admin just gave shows up without signing out. */
  refreshRole: () => Promise<Role | null>;
  signOut: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

const ROLES: Role[] = ['viewer', 'responder', 'admin'];

async function roleOf(user: User, force = false): Promise<Role | null> {
  const token = await user.getIdTokenResult(force);
  const role = token.claims.role;
  return ROLES.includes(role as Role) ? (role as Role) : null;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null | undefined>(undefined);
  const [role, setRole] = useState<Role | null>(null);

  useEffect(
    () =>
      onAuthStateChanged(auth, async (u) => {
        setRole(u ? await roleOf(u).catch(() => null) : null);
        setUser(u);
      }),
    [],
  );

  const refreshRole = useCallback(async () => {
    if (!auth.currentUser) return null;
    const r = await roleOf(auth.currentUser, true);
    setRole(r);
    return r;
  }, []);

  const signOut = useCallback(() => fbSignOut(auth), []);
  const name = user?.displayName || user?.email || '';

  return <AuthContext.Provider value={{ user, role, name, refreshRole, signOut }}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth outside AuthProvider');
  return ctx;
}

/** Protects a route: signed in with a role, or back to the login screen (which explains "no access"). */
export function RequireRole({ children, roles = ROLES }: { children: ReactNode; roles?: Role[] }) {
  const { user, role } = useAuth();
  const location = useLocation();
  if (user === undefined) return <Spinner label="Checking your sign-in…" />;
  if (!user || !role) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  if (!roles.includes(role)) return <Navigate to="/" replace />;
  return <>{children}</>;
}
