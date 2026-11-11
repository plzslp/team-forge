import { useState, type FormEvent } from 'react';
import { History } from 'lucide-react';
import { api, ApiError, errorMessage } from './api';
import { formatDate, games, positionLabels, type Game, type Match, type Page } from './domain';
import PageControls from './PageControls';
import { useQuery } from './useQuery';

export default function MatchHistory({ game, onCreate }: { game: Game; onCreate: () => void }) {
  const [page, setPage] = useState(0);
  const [retry, setRetry] = useState(0);
  const { data, error, loading } = useQuery<Page<Match>>(`/api/matches?game=${game}&page=${page}&size=10`, retry);

  return (
    <section className="history" aria-label="경기 기록" aria-busy={loading}>
      {loading && <p className="empty history-empty" role="status">경기 기록을 불러오는 중…</p>}
      {error != null && <div className="empty history-empty" role="alert">{errorMessage(error)}
        <button className="secondary retry" onClick={() => setRetry(value => value + 1)}>다시 불러오기</button>
      </div>}
      {data?.content.length === 0 && <div className="empty history-empty">
        <History size={30} /><h2>확정된 경기가 없습니다.</h2>
        <button className="primary" onClick={onCreate}>팀 편성하기</button>
      </div>}
      {data?.content.map(match => <MatchRecord key={match.id} match={match} />)}
      {data && <PageControls page={page} totalPages={data.totalPages} onPage={setPage} />}
    </section>
  );
}

function MatchRecord({ match }: { match: Match }) {
  const [expanded, setExpanded] = useState(false);
  const [saved, setSaved] = useState<{ source: Match; match: Match } | null>(null);
  const [retry, setRetry] = useState(0);
  const [conflict, setConflict] = useState(false);
  const { data, error, loading } = useQuery<Match>(expanded ? `/api/matches/${match.id}` : null, retry);
  const current = data && saved?.source === data ? saved.match : data ?? saved?.match ?? match;
  const winner = current.result ? current.result.outcomeA === 'DRAW' ? '무승부'
    : current.result.outcomeA === 'WIN' ? 'A팀 승리' : 'B팀 승리' : '결과 미입력';

  return <article className="history-card">
    <div>
      <span className="eyebrow">{games[current.game]}</span>
      <h2>{winner}</h2>
      <time dateTime={current.createdAt}>{formatDate(current.createdAt)}</time>
      {current.result && <p>{current.result.scoreA} : {current.result.scoreB}</p>}
    </div>
    <div>
      {current.teams.map(team => <div key={team.team}>
        <strong>{team.team}팀 · {team.totalScore.toLocaleString()} PT</strong>
        <p>{team.participants.map(participant => participant.name).join(' · ')}</p>
      </div>)}
      <details onToggle={event => {
        const open = event.currentTarget.open;
        if (!open && data) setSaved({ source: data, match: current });
        setExpanded(open);
        setConflict(false);
      }}>
        <summary>상세 보기 · 경기 결과 입력</summary>
        {loading && <p role="status">상세 기록을 불러오는 중…</p>}
        {error != null && <div className="form-error" role="alert">{errorMessage(error)}
          <button className="secondary" onClick={() => setRetry(value => value + 1)}>다시 불러오기</button>
        </div>}
        {expanded && conflict && <p className="form-error" role="alert">{data
          ? '다른 곳에서 경기 결과가 변경되었습니다. 최신 결과를 확인한 뒤 다시 입력하세요.'
          : '다른 곳에서 경기 결과가 변경되었습니다. 최신 결과를 불러와야 다시 저장할 수 있습니다.'}</p>}
        {expanded && data && <>
          <div className="record-rosters">{current.teams.map(team => <section key={team.team}>
            <h3>{team.team}팀</h3>
            {team.participants.map(member => <p key={member.participantId}>
              {positionLabels[member.assignedPosition]} · {member.name} · {member.score.toLocaleString()} PT
            </p>)}
          </section>)}</div>
          <ResultForm match={current} onSaved={updated => {
            setSaved({ source: data, match: updated });
            setConflict(false);
          }} onConflict={() => {
            setConflict(true);
            setRetry(value => value + 1);
          }} />
        </>}
      </details>
    </div>
  </article>;
}

function ResultForm({ match, onSaved, onConflict }: {
  match: Match; onSaved: (match: Match) => void; onConflict: () => void;
}) {
  const [a, setA] = useState(String(match.result?.scoreA ?? ''));
  const [b, setB] = useState(String(match.result?.scoreB ?? ''));
  const [memo, setMemo] = useState(match.result?.memo ?? '');
  const [busy, setBusy] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState('');

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setError('');
    setSaved(false);
    try {
      const updated = await api.saveResult(match.id, { scoreA: Number(a), scoreB: Number(b), memo }, match.version);
      setA(String(updated.result?.scoreA ?? ''));
      setB(String(updated.result?.scoreB ?? ''));
      setMemo(updated.result?.memo ?? '');
      onSaved(updated);
      setSaved(true);
    } catch (cause) {
      if (cause instanceof ApiError && cause.code === 'CONCURRENT_MODIFICATION') onConflict();
      else setError(errorMessage(cause));
    } finally {
      setBusy(false);
    }
  }

  return <form onSubmit={save} onChange={() => { setSaved(false); setError(''); }} aria-busy={busy}>
    <fieldset className="editor-fields" disabled={busy}>
      <div className="score-inputs">
        <label>A팀 스코어<input required type="number" min="0" max="2147483647" step="1" value={a}
          onChange={event => setA(event.target.value)} /></label>
        <label>B팀 스코어<input required type="number" min="0" max="2147483647" step="1" value={b}
          onChange={event => setB(event.target.value)} /></label>
      </div>
      <label>메모<textarea maxLength={2000} value={memo} onChange={event => setMemo(event.target.value)} /></label>
      <button className="secondary" type="submit">{busy ? '저장 중…' : '경기 결과 저장'}</button>
    </fieldset>
    {error && <p role="alert" className="form-error">{error}</p>}
    {saved && <p role="status">결과를 저장했습니다.</p>}
  </form>;
}
