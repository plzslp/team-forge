import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Check, RefreshCw, Trash2, X } from 'lucide-react';
import { api, ApiError, errorMessage } from './api';
import {
  formatDate, games, positionLabels, positions, type Game, type Page, type Participant,
  type ParticipantMatch, type Position, type Profile,
} from './domain';
import PageControls from './PageControls';
import { useQuery } from './useQuery';

type Props = {
  game: Game;
  participant?: Participant;
  onClose: () => void;
  onChange: (participant: Participant, profile?: Profile) => void;
  onDelete: (id: string) => void;
};

export default function ProfileEditor(props: Props) {
  const { participant, game, onClose } = props;
  const dialog = useRef<HTMLDialogElement>(null);
  const [busy, setBusy] = useState(false);
  const [retry, setRetry] = useState(0);
  const { data, loading, error } = useQuery<Profile>(
    participant ? `/api/participants/${participant.id}/profiles/${game}` : null, retry,
  );
  const loadError = error != null && !(error instanceof ApiError && error.status === 404);

  useEffect(() => { dialog.current?.showModal(); }, []);

  return (
    <dialog ref={dialog} className="modal editor" aria-labelledby="editor-title"
      onCancel={event => { event.preventDefault(); if (!busy) onClose(); }}>
      <button className="modal-close" aria-label="편집 닫기" disabled={busy} onClick={onClose}><X /></button>
      <h2 id="editor-title">{participant ? '참가자 수정' : '참가자 등록'}</h2>
      <p>{games[game]}</p>
      {loading && <p role="status">프로필을 불러오는 중…</p>}
      {loadError && <div role="alert" className="form-error">
        {errorMessage(error)}
        <button className="secondary" onClick={() => setRetry(value => value + 1)}>다시 불러오기</button>
      </div>}
      {!loading && !loadError && <EditorForm {...props} initialProfile={data} busy={busy} setBusy={setBusy} />}
    </dialog>
  );
}

function EditorForm({ game, participant, initialProfile, onChange, onDelete, busy, setBusy }: Props & {
  initialProfile: Profile | null;
  busy: boolean;
  setBusy: (value: boolean) => void;
}) {
  const [savedParticipant, setSavedParticipant] = useState(participant);
  const [profile, setProfile] = useState(initialProfile);
  const [name, setName] = useState(participant?.name ?? '');
  const [accountId, setAccountId] = useState(initialProfile?.accountId ?? '');
  const [manualScore, setManualScore] = useState(initialProfile?.manualScore?.toString() ?? '');
  const [preferred, setPreferred] = useState<Position[]>(initialProfile?.preferredPositions ?? [positions[game][0]]);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [showHistory, setShowHistory] = useState(false);

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    const displayName = name.trim();
    const score = manualScore === '' ? null : Number(manualScore);
    if (!displayName || !preferred.length ||
        (score !== null && (!Number.isInteger(score) || score < 0 || score > 2147483647))) {
      setError('닉네임, 선호 포지션, 수동 점수를 확인하세요.');
      return;
    }
    setBusy(true);
    setError('');
    setNotice('');
    let participantSaved = false;
    try {
      // 프로필 저장만 실패하면 이미 등록된 ID로 다시 시도한다.
      const record = !savedParticipant ? await api.createParticipant(displayName)
        : savedParticipant.name !== displayName ? await api.renameParticipant(savedParticipant.id, displayName)
          : savedParticipant;
      setSavedParticipant(record);
      if (record !== savedParticipant) {
        participantSaved = true;
        onChange(record);
      }
      const updated = await api.saveProfile(record.id, game, {
        accountId: accountId.trim() || null,
        manualScore: score,
        preferredPositions: preferred,
      });
      setProfile(updated);
      onChange(record, updated);
      setNotice('프로필을 저장했습니다.');
    } catch (cause) {
      setError((participantSaved ? '참가자는 저장됐습니다. 프로필 저장을 다시 시도하세요. ' : '') + errorMessage(cause));
    } finally {
      setBusy(false);
    }
  }

  async function refresh() {
    if (!savedParticipant || !profile?.accountId || accountId.trim() !== profile.accountId || busy) return;
    setBusy(true);
    setError('');
    setNotice('');
    try {
      const updated = await api.refreshRank(savedParticipant.id);
      setProfile(updated);
      onChange(savedParticipant, updated);
      setNotice('솔로랭크를 갱신했습니다.');
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setBusy(false);
    }
  }

  async function remove() {
    if (!savedParticipant || busy ||
        !window.confirm(`${savedParticipant.name} 참가자를 삭제할까요? 확정된 경기 기록은 보존됩니다.`)) return;
    setBusy(true);
    setError('');
    try {
      await api.deleteParticipant(savedParticipant.id);
      onDelete(savedParticipant.id);
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setBusy(false);
    }
  }

  const rank = profile?.riotRank;
  return (
    <>
      <form onSubmit={save} aria-busy={busy}>
        <fieldset className="editor-fields" disabled={busy}>
          <label>닉네임<input required maxLength={20} value={name} onChange={event => setName(event.target.value)} /></label>
          <label>{game === 'LOL' ? 'Riot ID' : '게임 계정'}
            <input maxLength={255} placeholder={game === 'LOL' ? '게임이름#태그' : '닉네임#태그'}
              value={accountId} onChange={event => setAccountId(event.target.value)} />
          </label>
          {game === 'LOL' && (
            <section className="riot-lookup" aria-label="롤 솔로랭크">
              <h3>롤 솔로랭크</h3>
              <p>{rank ? (rank.tier ? `${rank.tier} ${rank.division ?? ''} · ${rank.leaguePoints} LP` : '언랭크')
                + ` · ${formatDate(rank.updatedAt)}` : 'Riot ID를 저장한 뒤 솔로랭크를 갱신할 수 있습니다.'}</p>
              <button type="button" className="secondary" onClick={refresh}
                disabled={!profile?.accountId || accountId.trim() !== profile.accountId}>
                <RefreshCw size={15} />솔로랭크 갱신
              </button>
            </section>
          )}
          <label>수동 점수
            <input type="number" min="0" max="2147483647" step="1" value={manualScore}
              onChange={event => setManualScore(event.target.value)} placeholder="미지정" />
          </label>
          <p className="score-source">
            {profile && `환산 ${profile.baseScore.toLocaleString()} PT · 적용 ${profile.effectiveScore.toLocaleString()} PT. `}
            비워두면 환산 점수를 사용합니다.
          </p>
          <fieldset className="role-options">
            <legend>선호 포지션</legend>
            {positions[game].map(position => (
              <label key={position}>
                <input type="checkbox" checked={preferred.includes(position)}
                  onChange={() => setPreferred(value => value.includes(position)
                    ? value.filter(item => item !== position) : [...value, position])} />
                {positionLabels[position]}
              </label>
            ))}
          </fieldset>
          <button className="primary" type="submit" disabled={!preferred.length || !name.trim()}>
            <Check size={17} />{busy ? '처리 중…' : profile ? '수정 저장' : '프로필 저장'}
          </button>
        </fieldset>
        {error && <p role="alert" className="form-error">{error}</p>}
        {notice && <p role="status" className="form-status">{notice}</p>}
      </form>
      {savedParticipant && (
        <>
          <div className="editor-actions">
            <button className="secondary" disabled={busy} aria-expanded={showHistory}
              onClick={() => setShowHistory(value => !value)}>{showHistory ? '최근 내전 닫기' : '최근 내전 보기'}</button>
            <button className="secondary danger" disabled={busy} onClick={remove}><Trash2 size={15} />참가자 삭제</button>
          </div>
          {showHistory && <ParticipantHistory id={savedParticipant.id} game={game} />}
        </>
      )}
    </>
  );
}

function ParticipantHistory({ id, game }: { id: string; game: Game }) {
  const [page, setPage] = useState(0);
  const [retry, setRetry] = useState(0);
  const { data, error, loading } = useQuery<Page<ParticipantMatch>>(
    `/api/participants/${id}/matches?game=${game}&page=${page}&size=10`, retry,
  );
  const outcomes = { WIN: '승리', LOSS: '패배', DRAW: '무승부' };
  return <section className="participant-history" aria-label="참가자 최근 내전">
    <h3>최근 내전</h3>
    {loading && <p role="status">기록을 불러오는 중…</p>}
    {error != null && <div role="alert" className="form-error">{errorMessage(error)}
      <button className="secondary" onClick={() => setRetry(value => value + 1)}>다시 불러오기</button>
    </div>}
    {data?.content.length === 0 && <p>아직 내전 기록이 없습니다.</p>}
    {data && <ul>{data.content.map(match => <li key={match.matchId}>
      <time dateTime={match.createdAt}>{formatDate(match.createdAt)}</time>
      <span>{match.team}팀</span>
      <strong>{match.outcome ? `${outcomes[match.outcome]} (${match.scoreA} : ${match.scoreB})` : '결과 미입력'}</strong>
    </li>)}</ul>}
    {data && <PageControls page={page} totalPages={data.totalPages} onPage={setPage} />}
  </section>;
}
