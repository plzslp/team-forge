import { useEffect, useRef, useState } from 'react';
import { ArrowLeftRight, Check, LogOut, Moon, Shuffle, Sun, Swords, X } from 'lucide-react';
import { api, ApiError, errorMessage } from './api';
import { games, positionLabels, type DraftMember, type Game, type Participant, type Preview, type Profile, type ProfileSummary } from './domain';
import MatchHistory from './MatchRecord';
import ParticipantRoster from './ParticipantRoster';
import ProfileEditor from './ProfileEditor';
import { useTheme } from './useTheme';

const sections = ['팀 편성', '참가자 관리', '경기 기록'] as const;
type Section = (typeof sections)[number];

function createConfirmationKey() {
  // 로컬 네트워크의 HTTP에서도 사용할 수 있는 난수 기반 UUID v4.
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('');
  return [hex.slice(0, 8), hex.slice(8, 12), hex.slice(12, 16), hex.slice(16, 20), hex.slice(20)].join('-');
}

export default function Workspace({ username, onLogout }: { username: string; onLogout: () => Promise<void> }) {
  const { theme, toggleTheme } = useTheme();
  const [section, setSection] = useState<Section>('팀 편성');
  const [game, setGame] = useState<Game>('LOL');
  const [selected, setSelected] = useState<ProfileSummary[]>([]);
  const [draft, setDraft] = useState<Preview | null>(null);
  const [swapId, setSwapId] = useState<string | null>(null);
  const [offerNonPreferred, setOfferNonPreferred] = useState(false);
  const [needsConsent, setNeedsConsent] = useState(false);
  const [editor, setEditor] = useState<{ participant?: Participant } | null>(null);
  const [revision, setRevision] = useState(0);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState('');
  const [error, setError] = useState('');
  const [scrollVersion, setScrollVersion] = useState(0);
  const results = useRef<HTMLElement>(null);
  const confirmation = useRef<{ draft: Preview; allowNonPreferredPositions: boolean; key: string } | null>(null);
  const managing = section === '참가자 관리';
  const showingHistory = section === '경기 기록';

  useEffect(() => {
    if (!scrollVersion) return;
    results.current?.scrollIntoView({
      behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth',
      block: 'start',
    });
    results.current?.focus({ preventScroll: true });
  }, [scrollVersion]);

  function resetDraft() {
    confirmation.current = null;
    setDraft(null);
    setSwapId(null);
    setOfferNonPreferred(false);
    setNeedsConsent(false);
  }

  function clearMessages() {
    setNotice('');
    setError('');
  }

  function toggle(profile: ProfileSummary) {
    clearMessages();
    const alreadySelected = selected.some(item => item.participantId === profile.participantId);
    if (!alreadySelected && selected.length === 10) {
      setError('참가자는 최대 10명까지 선택할 수 있습니다.');
      return;
    }
    setSelected(alreadySelected ? selected.filter(item => item.participantId !== profile.participantId) : [...selected, profile]);
    resetDraft();
  }

  async function generate(allowNonPreferredPositions: boolean) {
    if (busy || selected.length !== 10) return;
    clearMessages();
    resetDraft();
    setBusy(true);
    try {
      setDraft(await api.preview(game, selected.map(item => item.participantId), allowNonPreferredPositions));
      setScrollVersion(value => value + 1);
    } catch (cause) {
      setOfferNonPreferred(cause instanceof ApiError && cause.code === 'PREFERRED_POSITION_UNAVAILABLE');
      setError(errorMessage(cause));
    } finally {
      setBusy(false);
    }
  }

  function swap(member: DraftMember) {
    if (!draft || busy) return;
    const from = draft.teams.find(team => team.participants.some(item => item.participantId === swapId));
    const to = draft.teams.find(team => team.participants.some(item => item.participantId === member.participantId));
    if (!from || from === to) {
      setSwapId(swapId === member.participantId ? null : member.participantId);
      return;
    }
    const other = from.participants.find(item => item.participantId === swapId);
    if (!other) return;

    // 상대 팀의 자리에 배정해 두 팀의 포지션 구성을 유지한다.
    function move(player: DraftMember, slot: DraftMember): DraftMember {
      const assignedPosition = slot.assignedPosition;
      const profile = selected.find(item => item.participantId === player.participantId);
      return { ...player, assignedPosition, preferredPosition: assignedPosition === player.assignedPosition
        ? player.preferredPosition : profile?.preferredPositions.includes(assignedPosition) ?? false };
    }
    const teams = draft.teams.map(team => {
      const participants = team.participants.map(item => item.participantId === other.participantId
        ? move(member, item) : item.participantId === member.participantId ? move(other, item) : item);
      return { ...team, participants, totalScore: participants.reduce((sum, item) => sum + item.score, 0) };
    });
    setDraft({ ...draft, teams, warnings: [] });
    setSwapId(null);
    setNeedsConsent(false);
    clearMessages();
  }

  async function confirm(allowNonPreferredPositions: boolean) {
    if (!draft || busy) return;
    clearMessages();
    setBusy(true);
    try {
      if (confirmation.current?.draft !== draft || confirmation.current.allowNonPreferredPositions !== allowNonPreferredPositions) {
        confirmation.current = { draft, allowNonPreferredPositions, key: createConfirmationKey() };
      }
      await api.confirm(game, draft, allowNonPreferredPositions, confirmation.current.key);
      resetDraft();
      setSelected([]);
      setSection('경기 기록');
      setNotice('경기를 확정했습니다.');
    } catch (cause) {
      if (cause instanceof ApiError && cause.code === 'PREVIEW_OUTDATED') {
        resetDraft();
        setRevision(value => value + 1);
      } else if (cause instanceof ApiError && cause.code === 'NON_PREFERRED_POSITION_NOT_ALLOWED') {
        setNeedsConsent(true);
      }
      setError(errorMessage(cause));
    } finally {
      setBusy(false);
    }
  }

  function updateParticipant(participant: Participant, profile?: Profile) {
    setSelected(items => items.map(item => item.participantId === participant.id
      ? { ...item, ...profile, name: participant.name } : item));
    if (selected.some(item => item.participantId === participant.id)) resetDraft();
    setRevision(value => value + 1);
  }

  function deleteParticipant(id: string) {
    setSelected(items => items.filter(item => item.participantId !== id));
    resetDraft();
    setEditor(null);
    setRevision(value => value + 1);
    setNotice('참가자를 삭제했습니다.');
  }

  async function logout() {
    if (busy) return;
    clearMessages();
    setBusy(true);
    try { await onLogout(); }
    catch (cause) { setError(errorMessage(cause)); }
    finally { setBusy(false); }
  }

  const nonPreferred = draft?.teams.flatMap(team => team.participants).filter(member => !member.preferredPosition).length ?? 0;
  const selectedForSwap = draft?.teams.flatMap(team => team.participants).find(member => member.participantId === swapId);

  return <div className="shell">
    <header className="site-header">
      <div className="header-inner">
        <span className="brand">TEAMFORGE</span>
        <span className="header-user" title={username}>{username}</span>
        <button className="theme-toggle" onClick={toggleTheme}
          aria-label={theme === 'dark' ? '라이트 모드로 전환' : '다크 모드로 전환'}>
          {theme === 'dark' ? <Sun size={17} /> : <Moon size={17} />}
        </button>
        <button className="theme-toggle" disabled={busy} onClick={logout} aria-label="로그아웃">
          <LogOut size={16} /><span>로그아웃</span>
        </button>
      </div>
    </header>
    <nav className="navigation" aria-label="주 메뉴">
      <div className="nav-inner">{sections.map(item => <button key={item} disabled={busy}
        className={section === item ? 'nav active' : 'nav'} aria-current={section === item ? 'page' : undefined}
        onClick={() => { setSection(item); clearMessages(); }}>{item}</button>)}</div>
    </nav>
    <main>
      <h1 className="page-heading">{section}</h1>
      <div className="game-options" aria-label="게임 선택">
        {(Object.keys(games) as Game[]).map(item => <button key={item}
          className={`game-tab${item === game ? ' selected' : ''}`} aria-pressed={item === game} disabled={busy}
          onClick={() => {
            if (item === game) return;
            setGame(item); setSelected([]); resetDraft(); clearMessages();
          }}>{games[item]}</button>)}
      </div>
      {notice && <p className="notice" role="status">{notice}</p>}
      {error && <div className="form-error workspace-error" role="alert">{error}
        {offerNonPreferred && <button className="secondary" disabled={busy} onClick={() => generate(true)}>
          비선호 배정을 허용하고 다시 계산
        </button>}
      </div>}
      {!showingHistory && <>
        {!managing && draft && <section className="results" ref={results} tabIndex={-1} aria-label="편성 결과">
          <div className="result-heading">
            <div>
              <h2>편성 결과</h2>
              <p>총점 차이 <strong>{Math.abs(draft.teams[0].totalScore - draft.teams[1].totalScore).toLocaleString()} PT</strong></p>
            </div>
            <button className="secondary" disabled={busy} onClick={() => generate(false)}><Shuffle size={16} />다시 계산</button>
          </div>
          {selectedForSwap ? <div className="swap-notice" role="status">
            <strong>{selectedForSwap.name}</strong> 선택됨. 반대 팀 선수를 선택하세요.
            <button className="secondary" onClick={() => setSwapId(null)}>교체 취소</button>
          </div> : <p className="position-hint">양쪽 팀에서 선수 한 명씩 선택하면 자리를 교체합니다.</p>}
          {nonPreferred > 0 && <p className="position-warning">
            {draft.warnings[0] ?? `선호하지 않는 포지션에 ${nonPreferred}명이 배정됐습니다.`}
          </p>}
          <div className="teams">{draft.teams.map((team, index) => <div key={team.team} className={`team team-${index}`}>
            <h3>{team.team}팀 <span>{team.totalScore.toLocaleString()} PT</span></h3>
            {team.participants.map(member => <button key={member.participantId} disabled={busy}
              className={swapId === member.participantId ? 'swapping' : ''}
              aria-pressed={swapId === member.participantId} onClick={() => swap(member)}>
              <span>{positionLabels[member.assignedPosition]}{!member.preferredPosition && <small className="non-preferred">비선호</small>}</span>
              <strong>{member.name}</strong>
              <small>{member.score.toLocaleString()}</small>
              <ArrowLeftRight size={15} />
            </button>)}
          </div>)}</div>
          <button className="primary save" disabled={busy} onClick={() => confirm(nonPreferred > 0 || needsConsent)}>
            <Check size={17} />{nonPreferred > 0 || needsConsent ? '비선호 배정을 포함해 확정' : '이 팀으로 확정하기'}
          </button>
        </section>}
        <div className={managing ? 'content-grid management' : 'content-grid'}>
          <ParticipantRoster key={`${game}-${section}`} game={game} managing={managing} selected={selected}
            revision={revision} busy={busy} onToggle={toggle} onEdit={participant => setEditor({ participant })} />
          {!managing && <aside className="selection" aria-label="선택한 참가자">
            <div className="selection-title"><h2>선택한 참가자</h2>
              <button className="reset" disabled={busy || !selected.length}
                onClick={() => { setSelected([]); resetDraft(); clearMessages(); }}>초기화</button>
            </div>
            <div className="count"><strong>{selected.length}<span> / 10</span></strong>
              <span>{selected.length === 10 ? '편성 가능' : `${10 - selected.length}명을 더 선택하세요`}</span></div>
            <div className="progress" aria-hidden="true"><div style={{ width: `${selected.length * 10}%` }} /></div>
            <div className="slots">{Array.from({ length: 10 }, (_, index) => {
              const profile = selected[index];
              return <div className={`slot${profile ? ' filled' : ''}`} key={index}>
                <span className="slot-number">{String(index + 1).padStart(2, '0')}</span>
                {profile ? <>
                  <strong>{profile.name}</strong>
                  <small>{profile.preferredPositions.map(position => positionLabels[position]).join(' · ')}</small>
                  <button aria-label={`${profile.name} 제외`} disabled={busy} onClick={() => toggle(profile)}><X size={15} /></button>
                </> : <span>미선택</span>}
              </div>;
            })}</div>
            <button className="primary generate" disabled={busy || selected.length !== 10} onClick={() => generate(false)}>
              <Swords size={18} />{busy && !draft ? '처리 중…' : '팀 생성하기'}
            </button>
          </aside>}
        </div>
      </>}
      {showingHistory && <MatchHistory key={game} game={game} onCreate={() => setSection('팀 편성')} />}
    </main>
    {editor && <ProfileEditor game={game} participant={editor.participant} onChange={updateParticipant}
      onClose={() => setEditor(null)} onDelete={deleteParticipant} />}
  </div>;
}
