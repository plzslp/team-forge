import { useEffect, useState } from 'react';
import { Check, Pencil, Plus, Search } from 'lucide-react';
import { errorMessage } from './api';
import { positionLabels, positions, type Game, type Page, type Participant, type Position, type ProfileSummary } from './domain';
import PageControls from './PageControls';
import { useQuery } from './useQuery';

type Props = {
  game: Game;
  managing: boolean;
  selected: ProfileSummary[];
  revision: number;
  busy: boolean;
  onToggle: (profile: ProfileSummary) => void;
  onEdit: (participant?: Participant) => void;
};

export default function ParticipantRoster({ game, managing, selected, revision, busy, onToggle, onEdit }: Props) {
  const [directory, setDirectory] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [position, setPosition] = useState<Position | ''>('');
  const [page, setPage] = useState(0);
  const [retry, setRetry] = useState(0);
  const allParticipants = managing && directory;
  const query = new URLSearchParams({ page: String(page), size: '10' });
  if (!allParticipants) {
    query.set('game', game);
    if (keyword.trim()) query.set('keyword', keyword.trim());
    if (position) query.set('position', position);
  }
  const path = `/api/${allParticipants ? 'participants' : 'game-profiles'}?${query}`;
  const { data, error, loading } = useQuery<Page<Participant | ProfileSummary>>(path, revision + retry);

  useEffect(() => {
    if (data && page >= data.totalPages && page > 0) setPage(Math.max(0, data.totalPages - 1));
  }, [data, page]);

  return (
    <section className="roster" aria-label={managing ? '참가자 목록' : '참가자 선택'}>
      <div className="section-caption roster-caption">
        <strong>{managing ? '참가자 목록' : '참가자 선택'}</strong>
        {data && <small>{data.totalElements}명</small>}
        <button type="button" className="text-button" disabled={busy} onClick={() => onEdit()}>
          <Plus size={16} />참가자 등록
        </button>
      </div>
      {managing && (
        <div className="filters roster-views" aria-label="참가자 보기">
          <button className={directory ? 'filter' : 'filter on'} aria-pressed={!directory}
            onClick={() => { setDirectory(false); setPage(0); }}>게임 프로필</button>
          <button className={directory ? 'filter on' : 'filter'} aria-pressed={directory}
            onClick={() => { setDirectory(true); setPage(0); }}>전체 참가자</button>
        </div>
      )}
      {!allParticipants && <>
        <div className="searchbox">
          <Search size={18} />
          <input aria-label="참가자 검색" placeholder="닉네임 검색" maxLength={20} value={keyword}
            onChange={event => { setKeyword(event.target.value); setPage(0); }} />
        </div>
        <div className="filters" aria-label="포지션 필터">
          {(['', ...positions[game]] as const).map(item => (
            <button key={item} className={position === item ? 'filter on' : 'filter'}
              aria-pressed={position === item} onClick={() => { setPosition(item); setPage(0); }}>
              {item ? positionLabels[item] : '전체'}
            </button>
          ))}
        </div>
      </>}
      <div className="list-head" aria-hidden="true">
        <span>참가자</span><span>선호 포지션</span><span>적용 점수</span><span>{managing ? '수정' : '선택'}</span>
      </div>
      <div className="players" aria-busy={loading}>
        {loading && <p className="empty" role="status">참가자를 불러오는 중…</p>}
        {error != null && <div className="empty" role="alert">{errorMessage(error)}
          <button className="secondary retry" onClick={() => setRetry(value => value + 1)}>다시 불러오기</button>
        </div>}
        {data?.content.length === 0 && <p className="empty">
          {keyword || position ? '조건에 맞는 참가자가 없습니다.' : allParticipants
            ? '등록한 참가자가 없습니다.' : '이 게임에 등록된 프로필이 없습니다.'}
        </p>}
        {data?.content.map(row => {
          const profile = 'participantId' in row ? row : null;
          const participant = { id: 'id' in row ? row.id : row.participantId, name: row.name };
          const picked = selected.some(item => item.participantId === participant.id);
          return (
            <button key={participant.id} className={`player${!managing && picked ? ' picked' : ''}`}
              disabled={busy} aria-pressed={managing ? undefined : picked}
              aria-label={`${row.name} ${managing ? '수정' : picked ? '선택 해제' : '선택'}`}
              onClick={() => { if (managing) onEdit(participant); else if (profile) onToggle(profile); }}>
              <span className="player-info"><strong>{row.name}</strong>
                {profile?.accountId && <small>{profile.accountId}</small>}
              </span>
              <span className="player-role">{profile?.preferredPositions.map(item => positionLabels[item]).join(' · ') ?? '—'}</span>
              <span className="tier">{profile ? `${profile.effectiveScore.toLocaleString()} PT` : '—'}</span>
              {managing ? <span className="edit-action"><Pencil size={14} /><span>수정</span></span>
                : <span className="checkbox">{picked && <Check size={13} />}</span>}
            </button>
          );
        })}
      </div>
      {data && <PageControls page={page} totalPages={data.totalPages} onPage={setPage} />}
    </section>
  );
}
