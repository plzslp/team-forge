import { useState } from 'react';
import { sum, type Match } from './domain';

export default function MatchRecord({ match, onSave }: { match: Match; onSave: (result: NonNullable<Match['result']>) => void }) {
 const [a, setA] = useState(String(match.result?.a ?? 0));
 const [b, setB] = useState(String(match.result?.b ?? 0));
 const [memo, setMemo] = useState(match.result?.memo ?? '');
 const [saved, setSaved] = useState(false);
 const winner = match.result ? match.result.a === match.result.b ? '무승부' : match.result.a > match.result.b ? 'A팀 승리' : 'B팀 승리' : '결과 미입력';
 return <article className="history-card"><div><span className="eyebrow">{match.game === 'LOL' ? '리그 오브 레전드' : '오버워치'}</span><h2>{winner}</h2><small>{match.date}</small>{match.result && <p>{match.result.a} : {match.result.b}</p>}</div><div><strong>A팀 · {sum(match.a)} PT</strong><p>{match.a.map(p => p.name).join(' · ')}</p><strong>B팀 · {sum(match.b)} PT</strong><p>{match.b.map(p => p.name).join(' · ')}</p>
 <details><summary>상세 보기 · 경기 결과 입력</summary><div className="record-rosters">{[match.a,match.b].map((team,i)=><section key={i}><h3>{i===0?'A팀':'B팀'}</h3>{team.map(p=><p key={p.id}>{p.name} · {p.preferredRoles.join('/')} · {p.score} PT</p>)}</section>)}</div>
 <form onChange={()=>setSaved(false)} onSubmit={e=>{e.preventDefault();const x=Number(a),y=Number(b);if(!a.trim()||!b.trim()||!Number.isInteger(x)||!Number.isInteger(y)||x<0||y<0||x>99||y>99)return;onSave({a:x,b:y,memo:memo.trim()});setSaved(true);}}><div className="score-inputs"><label>A팀 스코어<input required type="number" min="0" max="99" step="1" value={a} onChange={e=>setA(e.target.value)}/></label><label>B팀 스코어<input required type="number" min="0" max="99" step="1" value={b} onChange={e=>setB(e.target.value)}/></label></div><label>메모<textarea maxLength={1000} value={memo} onChange={e=>setMemo(e.target.value)} /></label><p>승리 팀은 스코어로 결정됩니다. 결과는 밸런싱 점수에 반영되지 않습니다.</p><button className="secondary" type="submit">경기 결과 저장</button>{saved&&<p role="status">결과를 저장했습니다.</p>}</form></details></div></article>;
}
