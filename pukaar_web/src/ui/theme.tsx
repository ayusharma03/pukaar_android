// Light and dark themes, chosen by the user (top bar). Dark is the default.
import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';

export type Theme = 'dark' | 'light';

const KEY = 'pukaar.theme';
const ThemeContext = createContext<{ theme: Theme; toggle: () => void }>({ theme: 'dark', toggle: () => {} });

function stored(): Theme {
  try {
    return localStorage.getItem(KEY) === 'light' ? 'light' : 'dark';
  } catch {
    return 'dark';
  }
}

function apply(theme: Theme) {
  // Set right away (not in an effect): child effects, like the map's, run first and read the tokens.
  document.documentElement.dataset.theme = theme;
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setTheme] = useState<Theme>(() => {
    const t = stored();
    apply(t);
    return t;
  });
  useEffect(() => {
    try {
      localStorage.setItem(KEY, theme);
    } catch {
      /* private window: theme just isn't remembered */
    }
  }, [theme]);
  const toggle = useCallback(
    () =>
      setTheme((t) => {
        const next = t === 'dark' ? 'light' : 'dark';
        apply(next);
        return next;
      }),
    [],
  );
  return <ThemeContext.Provider value={{ theme, toggle }}>{children}</ThemeContext.Provider>;
}

export const useTheme = () => useContext(ThemeContext);

/** Reads a token's current value, e.g. token('surface-container-low'), for the map style. */
export function token(name: string) {
  return getComputedStyle(document.documentElement).getPropertyValue(`--pk-${name}`).trim();
}
