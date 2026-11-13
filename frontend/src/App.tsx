import { useEffect, useState } from 'react';
import { currentAccount, errorMessage, sessionExpired, signOut } from './api';
import Login from './Login';
import Workspace from './Workspace';

export default function App() {
  const [account, setAccount] = useState<{ username: string } | null | undefined>(undefined);
  const [error, setError] = useState('');
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    const expire = () => setAccount(null);
    window.addEventListener(sessionExpired, expire);
    return () => window.removeEventListener(sessionExpired, expire);
  }, []);

  useEffect(() => {
    let active = true;
    currentAccount().then(
      result => { if (active) setAccount(result); },
      cause => { if (active) setError(errorMessage(cause)); },
    );
    return () => { active = false; };
  }, [retry]);

  if (account === undefined) return (
    <main className="login-screen">
      {error ? <div className="empty" role="alert">{error}
        <button className="secondary retry" onClick={() => { setError(''); setRetry(value => value + 1); }}>
          다시 연결
        </button>
      </div> : <p role="status">로그인 상태를 확인하는 중…</p>}
    </main>
  );
  if (!account) return <Login onLogin={setAccount} />;
  return <Workspace username={account.username} onLogout={async () => {
    await signOut();
    setAccount(null);
  }} />;
}
