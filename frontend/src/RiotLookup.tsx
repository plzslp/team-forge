import { useState } from 'react';
import { seed, type Player } from './domain';

export default function RiotLookup({ initialId, onApply }: { initialId: string; onApply: (player: Player, riotId: string) => void }) {
  const [query, setQuery] = useState(initialId);
  const [result, setResult] = useState<Player | null>(null);
  const [message, setMessage] = useState('');
  function lookup() {
    setResult(null);
    if (!/^[^#]+#[^#]+$/.test(query.trim())) { setMessage('게임이름#태그 형식으로 입력해주세요.'); return; }
    const found = seed.find(p => p.game === 'LOL' && `${p.name}${p.tag}`.toLowerCase() === query.trim().toLowerCase());
    if (!found) { setMessage('일치하는 데모 계정이 없습니다. 수동 등록을 이용할 수 있습니다.'); return; }
    setResult(found); setMessage('데모 계정 확인 완료 · 실제 Riot 조회 결과가 아닙니다.');
  }
  return <section className="riot-lookup" aria-label="Riot 계정 조회">
    <h3>Riot 계정 <span className="source-label">데모 조회</span></h3>
    <p>API 연결 전입니다. 예시 계정: 오늘도탑차이#KR1</p>
    <label>Riot ID<input value={query} onChange={e => { setQuery(e.target.value); setResult(null); setMessage(''); }} placeholder="게임이름#태그" /></label>
    <button type="button" className="secondary" onClick={lookup}>계정 조회 / 갱신</button>
    {message && <p role="status">{message}</p>}
    {result && <div className="lookup-result"><strong>{result.name}{result.tag}</strong><p>개인 랭크(솔로/듀오) · {result.tier} · {result.score} PT</p><button type="button" className="secondary" onClick={() => { onApply(result, `${result.name}${result.tag}`); setMessage('계정과 점수를 입력창에 반영했습니다. 저장하면 적용됩니다.'); }}>이 계정과 점수 사용</button></div>}
  </section>;
}
