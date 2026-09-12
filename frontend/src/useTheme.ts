import { useEffect, useState } from 'react';

type Theme = 'light' | 'dark';
const key = 'teamforge.theme';
const media = window.matchMedia('(prefers-color-scheme: dark)');
function storedTheme(): Theme | null {
  try {
    const value = localStorage.getItem(key);
    return value === 'light' || value === 'dark' ? value : null;
  } catch { return null; }
}
function apply(theme: Theme) {
  document.documentElement.dataset.theme = theme;
  document.documentElement.style.colorScheme = theme;
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', theme === 'dark' ? '#171d27' : '#263747');
}
const initialTheme = storedTheme() ?? (media.matches ? 'dark' : 'light');
apply(initialTheme);

export function useTheme() {
  const [theme, setTheme] = useState<Theme>(initialTheme);
  const [manual, setManual] = useState(() => storedTheme() !== null);
  useEffect(() => { apply(theme); }, [theme]);
  useEffect(() => {
    const onChange = (event: MediaQueryListEvent) => {
      if (!manual) setTheme(event.matches ? 'dark' : 'light');
    };
    media.addEventListener('change', onChange);
    return () => media.removeEventListener('change', onChange);
  }, [manual]);
  function toggleTheme() {
    const next = theme === 'dark' ? 'light' : 'dark';
    setManual(true);
    setTheme(next);
    try { localStorage.setItem(key, next); } catch { /* 저장 불가 환경에서도 전환은 유지한다. */ }
  }
  return { theme, toggleTheme };
}
