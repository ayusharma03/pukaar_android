import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider, RequireRole } from './auth/auth';
import { LoginPage } from './auth/LoginPage';
import { OpsPage } from './ops/OpsPage';
import { TeamPage } from './team/TeamPage';
import { ThemeProvider } from './ui/theme';
import { ToastProvider } from './ui/toasts';

export function App() {
  return (
    <ThemeProvider>
      <AuthProvider>
        <ToastProvider>
          <BrowserRouter>
            <Routes>
              <Route path="/login" element={<LoginPage />} />
              <Route
                path="/"
                element={
                  <RequireRole>
                    <OpsPage />
                  </RequireRole>
                }
              />
              <Route
                path="/team"
                element={
                  <RequireRole roles={['admin']}>
                    <TeamPage />
                  </RequireRole>
                }
              />
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </BrowserRouter>
        </ToastProvider>
      </AuthProvider>
    </ThemeProvider>
  );
}
