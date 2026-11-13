import { useState, type FormEvent } from 'react';
import { ArrowRight } from 'lucide-react';
import { errorMessage, signIn } from './api';
import './login.css';

export default function Login({ onLogin }: { onLogin: (account: { username: string }) => void }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setError('');
    try {
      onLogin(await signIn(username.trim(), password));
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setPassword('');
      setBusy(false);
    }
  }

  return (
    <main className="login-screen">
      <div className="login-frame">
        <section className="login-lineup" aria-labelledby="login-brand">
          <h1 id="login-brand">TeamForge</h1>
          <div className="login-teams" aria-hidden="true">
            {['A', 'B'].map(team => (
              <div className={`login-team login-team-${team}`} key={team}>
                <strong>{team}팀</strong>
                {Array.from({ length: 5 }, (_, index) => <span className="login-slot" key={index} />)}
              </div>
            ))}
          </div>
        </section>
        <section className="login-panel" aria-labelledby="login-title">
          <h2 id="login-title">로그인</h2>
          <form method="post" action="/login" onSubmit={submit} aria-busy={busy}>
            <label htmlFor="login-username">아이디</label>
            <input id="login-username" name="username" autoComplete="username" required value={username}
              disabled={busy} onChange={event => setUsername(event.target.value)} />
            <label htmlFor="login-password">비밀번호</label>
            <div className="login-password">
              <input id="login-password" name="password" type={showPassword ? 'text' : 'password'}
                autoComplete="current-password" required value={password} disabled={busy}
                onChange={event => setPassword(event.target.value)} />
              <button type="button" aria-label={showPassword ? '비밀번호 숨기기' : '비밀번호 표시'}
                onClick={() => setShowPassword(value => !value)}>{showPassword ? '숨기기' : '표시'}</button>
            </div>
            {error && <p className="form-error" role="alert">{error}</p>}
            <button className="primary login-submit" type="submit" disabled={busy || !username.trim() || !password}>
              {busy ? '로그인 중…' : '로그인'} <ArrowRight size={17} />
            </button>
          </form>
        </section>
      </div>
    </main>
  );
}
