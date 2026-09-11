import RiotLookup from "./RiotLookup";
import MatchRecord from "./MatchRecord";
import { useState, useRef, useEffect } from "react";
import {
  Swords,
  Pencil,
  History,
  ArrowUpRight,
  Search,
  Plus,
  Check,
  X,
  Shuffle,
  ArrowLeftRight,
  CheckCircle2,
} from "lucide-react";
import {
  balance,
  positionWarning,
  seed,
  roles,
  sum,
  type Player,
  type Game,
  type Match,
} from "./domain";

export default function App() {
  const [page, setPage] = useState("편성");
  const [game, setGame] = useState<Game>("LOL");
  const [players, setPlayers] = useState<Player[]>(seed);
  const [selected, setSelected] = useState<string[]>([]);
  const [query, setQuery] = useState("");
  const [role, setRole] = useState("전체");
  const [teams, setTeams] = useState<[Player[], Player[]] | null>(null);
  const [swap, setSwap] = useState<string | null>(null);
  const [history, setHistory] = useState<Match[]>([]);
  const [modal, setModal] = useState(false);
  const [notice, setNotice] = useState("");
  const [name, setName] = useState("");
  const [rating, setRating] = useState("1600");
  const [preferredRoles, setPreferredRoles] = useState<string[]>(["탑"]);
  const [scoreSource, setScoreSource] = useState<Player['source']>('manual');
  const [riotId, setRiotId] = useState('');
  const [checkedAt, setCheckedAt] = useState<string | undefined>();
  const [draftTier, setDraftTier] = useState('수동 점수');
  const [editingId, setEditingId] = useState<string | null>(null);
  const [scrollVersion, setScrollVersion] = useState(0);
  const resultsRef = useRef<HTMLElement>(null);
  useEffect(() => {
    if (scrollVersion > 0) {
      resultsRef.current?.scrollIntoView({ behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth", block: "start" });
      resultsRef.current?.focus({ preventScroll: true });
    }
  }, [scrollVersion]);
  function openEditor(player?: Player) {
    setEditingId(player?.id ?? null);
    setScoreSource(player?.source ?? (player ? 'sample' : 'manual'));
    setRiotId(player?.riotId ?? '');
    setCheckedAt(player?.checkedAt);
    setDraftTier(player?.tier ?? '수동 점수');
    setName(player?.name ?? "");
    setRating(String(player?.score ?? 1600));
    setPreferredRoles(player ? [...player.preferredRoles] : [roles[game][0]]);
    setModal(true);
  }
  const pool = players.filter((p) => p.game === game);
  const visible = pool.filter(
    (p) =>
      (role === "전체" || p.preferredRoles.includes(role)) &&
      p.name.toLowerCase().includes(query.toLowerCase()),
  );
  const chosen = pool.filter((p) => selected.includes(p.id));
  function toggle(id: string) {
    setNotice("");
    if (selected.includes(id)) {
      setSelected(selected.filter((x) => x !== id));
    } else if (selected.length < 10) {
      setSelected([...selected, id]);
    } else {
      setNotice("참가자는 최대 10명까지 선택할 수 있어요.");
      return;
    }
    setTeams(null);
    setSwap(null);
  }
  function changeGame(g: Game) {
    setGame(g);
    setSelected([]);
    setTeams(null);
    setRole("전체");
    setQuery("");
    setSwap(null);
    setNotice("");
  }
  function generate() {
    if (chosen.length !== 10) return;
    setTeams(balance(chosen));
    setScrollVersion(v => v + 1);
    setSwap(null);
    setNotice(
      "데모 팀 편성이 완료되었습니다. 총점 차이를 기준으로 계산했어요.",
    );
  }
  function switchPlayer(p: Player, side: number) {
    if (!teams) return;
    if (swap === p.id) { setSwap(null); return; }
    if (!swap) {
      setSwap(p.id);
      return;
    }
    const other = teams[1 - side].find((x) => x.id === swap);
    if (!other) {
      setSwap(p.id);
      return;
    }
    setTeams(
      teams.map((team, i) =>
        team.map((x) =>
          x.id === (i === side ? p.id : other.id)
            ? i === side
              ? other
              : p
            : x,
        ),
      ) as [Player[], Player[]],
    );
    setSwap(null);
    setNotice("선수 두 명을 교체했습니다.");
  }
  function save() {
    if (!teams) return;
    setHistory([
      {
        id: crypto.randomUUID(),
        date: new Date().toLocaleString("ko-KR"),
        game,
        a: [...teams[0]],
        b: [...teams[1]],
      },
      ...history,
    ]);
    setTeams(null);
    setNotice("이 브라우저 세션에 편성 기록을 저장했습니다.");
    setPage("기록");
  }
  function add(e: React.FormEvent) {
    e.preventDefault();
    const score = Number(rating);
    if (!name.trim() || !rating.trim() || !Number.isInteger(score) || score < 0 || score > 10000 || !preferredRoles.length) return;
    const original = players.find(p => p.id === editingId);
    const updated: Player = {
      id: editingId ?? crypto.randomUUID(),
      name: name.trim(),
      tag: riotId || original?.tag || "수동 등록",
      tier: scoreSource === "manual" ? "수동 점수" : draftTier,
      source: scoreSource,
      riotId: riotId || undefined,
      checkedAt,
      score,
      preferredRoles: [...preferredRoles],
      game,
    };
    setPlayers(current => editingId ? current.map(p => p.id === editingId ? updated : p) : [...current, updated]);
    if (editingId) { setTeams(null); setSwap(null); }
    setModal(false);
    setNotice(editingId ? "참여자 정보를 수정했습니다. 미확정 팀은 다시 생성해주세요." : "참여자를 등록했습니다.");
  }
  return (
    <div className="shell">
      <header className="site-header">
        <div className="header-inner">
          <a className="brand" href="#" onClick={() => setPage("편성")}>
            TEAMFORGE
          </a>
          <span className="header-divider" />
          <span className="header-description">내전 팀 편성</span>
          <span className="header-user">개인용</span>
        </div>
      </header>
      <nav className="navigation" aria-label="주 메뉴">
        <div className="nav-inner">
          {[
            { key: "편성", label: "팀 편성" },
            { key: "참여자", label: "참여자 관리" },
            { key: "기록", label: "편성 기록" },
          ].map(({ key, label }) => (
            <button
              key={key}
              className={page === key ? "nav active" : "nav"}
              aria-current={page === key ? "page" : undefined}
              onClick={() => {
                setPage(key);
                setNotice("");
              }}
            >
              {label}
            </button>
          ))}
        </div>
      </nav>
      <div className="main">
        <main>
          <div className="page-heading">
            <h1>
              {page === "편성"
                ? "팀 편성"
                : page === "참여자"
                  ? "참여자 관리"
                  : "편성 기록"}
            </h1>
            <p>
              {page === "편성"
                ? "참가자 10명을 선택해 두 팀으로 나눕니다."
                : page === "참여자"
                  ? "게임별 참여자와 수동 점수를 관리합니다."
                  : "확정한 팀 구성과 점수를 확인합니다."}
            </p>
          </div>
          <div className="demo-note">
            <strong>데모</strong> 샘플 데이터 사용 중 · 새로고침하면 등록 및
            편성 기록이 초기화됩니다.
          </div>
          {notice && (
            <div className="notice" role="status">
              <CheckCircle2 size={17} />
              {notice}
              <button aria-label="알림 닫기" onClick={() => setNotice("")}>
                <X size={16} />
              </button>
            </div>
          )}
          {page !== "기록" && (
            <>
              <div className="game-options" aria-label="게임 선택">
                {(
                  [
                    { key: "LOL", label: "리그 오브 레전드" },
                    { key: "OW", label: "오버워치" },
                  ] as const
                ).map((g) => (
                  <button
                    key={g.key}
                    className={"game-tab " + (game === g.key ? "selected" : "")}
                    aria-pressed={game === g.key}
                    onClick={() => changeGame(g.key)}
                  >
                    <span className={"game-icon game-icon-" + g.key.toLowerCase()} aria-hidden="true">{g.key === "LOL" ? "L" : <svg viewBox="0 0 24 24" fill="none"><path d="M5 6a9 9 0 0 1 14 0" stroke="#ed9a25" strokeWidth="3"/><path d="M3.5 9a9 9 0 1 0 17 0" stroke="currentColor" strokeWidth="3"/><path d="m5 18 7-7 7 7M12 11V7" stroke="currentColor" strokeWidth="2.5"/></svg>}</span>{g.label}
                  </button>
                ))}
                <span className="game-mode">
                  {game === "LOL" ? "소환사의 협곡" : "사용자 지정 게임"} · 5 vs
                  5
                </span>
              </div>
          {page === "편성" && teams && (
            <section className="results" ref={resultsRef} tabIndex={-1} aria-label="편성 결과">
              <div className="result-heading">
                <div>
                  <h2>편성 결과</h2>
                  <p>
                    총점 차이{" "}
                    <strong>
                      {Math.abs(sum(teams[0]) - sum(teams[1]))} PT
                    </strong>{" "}
                    ·{" "}
                    {swap
                      ? "반대 팀 선수를 눌러 교체하세요."
                      : "선수를 누른 뒤 반대 팀 선수를 선택하면 교체됩니다."}
                  </p>
                </div>
                <button className="secondary" onClick={generate}>
                  <Shuffle size={16} />
                  다시 계산
                </button>
              </div>
              {swap && <div className="swap-notice" role="status"><strong>{teams.flat().find(p=>p.id===swap)?.name}</strong> 선택됨 · 반대 팀 선수를 선택하세요.<button className="secondary" onClick={()=>setSwap(null)}>교체 취소</button></div>}
              <p className="position-hint">표시된 포지션은 선호 포지션이며 실제 배정 결과가 아닙니다.</p><div className="teams">
                {teams.map((team, side) => (
                  <div key={side} className={"team team-" + side}>
                    <h3>
                      TEAM {side === 0 ? "A" : "B"}{" "}
                      <span>{sum(team).toLocaleString()} PT</span>
                    </h3>
                    {positionWarning(team, game) && <p className="position-warning">{positionWarning(team, game)} {game === "LOL" ? "(탑·정글·미드·원딜·서포터 각 1명)" : "(역할 고정 기준: 탱커 1·딜러 2·서포터 2)"}</p>}
                    {team.map((p) => (
                      <button
                        key={p.id}
                        className={swap === p.id ? "swapping" : ""}
                        onClick={() => switchPlayer(p, side)}
                      >
                        <span title="선호 포지션">{p.preferredRoles.join(" · ")}</span>
                        <strong>{p.name}</strong>
                        <small>{p.score}</small>
                        <ArrowLeftRight size={15} />
                      </button>
                    ))}
                  </div>
                ))}
              </div>
              <button className="primary save" onClick={save}>
                <Check size={18} />이 팀으로 확정하기
              </button>
            </section>
          )}
              <div className={page === "참여자" ? "content-grid management" : "content-grid"}>
                <section className="roster">
                  <div className="section-caption roster-caption">
                    <strong>
                      {page === "편성" ? "참가자 선택" : "참여자 목록"}
                    </strong>
                    <small>{pool.length}명</small>
                    <button
                      className="text-button"
                      onClick={() => {
                        openEditor();
                      }}
                    >
                      <Plus size={16} />
                      참여자 등록
                    </button>
                  </div>
                  <div className="roster-body">
                    <div className="searchbox">
                      <Search size={18} />
                      <input
                        aria-label="참여자 검색"
                        placeholder="닉네임 검색"
                        value={query}
                        onChange={(e) => setQuery(e.target.value)}
                      />
                    </div>
                    <div className="filters">
                      {["전체", ...roles[game]].map((r) => (
                        <button
                          key={r}
                          className={role === r ? "filter on" : "filter"}
                          onClick={() => setRole(r)}
                        >
                          {r}
                        </button>
                      ))}
                      <span className="list-label">{visible.length}명</span>
                    </div>
                    <div className="list-head" aria-hidden="true">
                      <span>참여자</span>
                      <span>선호 포지션</span>
                      <span>티어 / 점수</span>
                      <span>{page === "참여자" ? "수정" : "선택"}</span>
                    </div>
                    <div className="players">
                      {visible.length === 0 ? (
                        <div className="empty">
                          검색 결과가 없어요. 다른 닉네임을 입력해보세요.
                        </div>
                      ) : (
                        visible.map((p) => (
                          <button
                            key={p.id}
                            className={
                              "player " +
                              (page === "편성" && selected.includes(p.id) ? "picked" : "")
                            }
                            onClick={() => page === "참여자" ? openEditor(p) : toggle(p.id)}
                            aria-pressed={page === "편성" ? selected.includes(p.id) : undefined}
                            aria-label={`${p.name} ${page === "참여자" ? "수정" : selected.includes(p.id) ? "선택 해제" : "선택"}`}
                          >
                            <span className="player-info">
                              <strong>{p.name}</strong>
                              <small>{p.tag}</small>
                              {page === "참여자" && <small className="source-detail">{p.source === "demo-api" ? "데모 조회" : p.source === "manual" ? "수동 입력/보정" : "샘플 점수"}{p.checkedAt ? " · 최근 데모 조회 " + new Date(p.checkedAt).toLocaleString('ko-KR') : " · 조회 이력 없음"}</small>}
                            </span>
                            <span className="player-role">{p.preferredRoles.join(" · ")}</span>
                            <span
                              className={
                                "tier " +
                                (p.tier.startsWith("Emerald") ? "emerald" : "")
                              }
                            >
                              <span>
                                {game === "OW" ? "수동 점수" : p.tier}
                              </span>
                              <small>{p.score.toLocaleString()} PT</small>
                            </span>
                            {page === "참여자" ? <span className="edit-action"><Pencil size={13}/><span>수정</span></span> : <span className="checkbox">{selected.includes(p.id) && <Check size={13}/>}</span>}
                          </button>
                        ))
                      )}
                    </div>
                    <div className="roster-footer">
                      {game === "LOL"
                        ? "개인 랭크(솔로/듀오) 기준 샘플 점수"
                        : "오버워치 점수는 운영자가 수동으로 등록합니다."}
                    </div>
                  </div>
                </section>
                {page === "편성" && <aside className="selection">
                  <div className="selection-title">
                    <h2>선택한 참가자</h2>
                    <button
                      className="reset"
                      onClick={() => {
                        setSelected([]);
                        setTeams(null);
                        setSwap(null);
                      }}
                    >
                      초기화
                    </button>
                  </div>
                  <div className="count">
                    <strong>
                      {selected.length}
                      <span> / 10</span>
                    </strong>
                    <span>
                      {selected.length === 10
                        ? "편성 가능"
                        : `${10 - selected.length}명을 더 선택하세요`}
                    </span>
                  </div>
                  <div className="progress">
                    <div style={{ width: `${selected.length * 10}%` }} />
                  </div>
                  <div className="slots">
                    {Array.from({ length: 10 }, (_, i) => {
                      const p = chosen[i];
                      return (
                        <div key={i} className={"slot " + (p ? "filled" : "")}>
                          <span className="slot-number">
                            {String(i + 1).padStart(2, "0")}
                          </span>
                          {p ? (
                            <>
                              <strong>{p.name}</strong>
                              <small>{p.preferredRoles.join(" · ")}</small>
                              <button
                                aria-label={`${p.name} 제외`}
                                onClick={() => toggle(p.id)}
                              >
                                <X size={14} />
                              </button>
                            </>
                          ) : (
                            <span>미선택</span>
                          )}
                        </div>
                      );
                    })}
                  </div>
                  <div className="balance-note">
                    <div>
                      <strong>편성 기준</strong>
                      <p>
                        데모에서는 팀 총점 차이를 최소화합니다.
                        <br />
                        포지션·고정 조건은 후속 구현 예정입니다.
                      </p>
                    </div>
                  </div>
                  <button
                    className="primary generate"
                    disabled={selected.length !== 10}
                    onClick={() => {
                      setPage("편성");
                      generate();
                    }}
                  >
                    <Swords size={18} />팀 생성하기
                    <ArrowUpRight size={18} />
                  </button>
                </aside>}
              </div>
            </>
          )}
          {page === "기록" && (
            <section className="history">
              {history.length === 0 ? (
                <div className="empty history-empty">
                  <History size={35} />
                  <h2>편성 기록이 없습니다.</h2>
                  <p>팀을 생성하고 확정하면 이곳에 표시됩니다.</p>
                  <button className="primary" onClick={() => setPage("편성")}>
                    첫 내전 만들기 <ArrowUpRight size={17} />
                  </button>
                </div>
              ) : (
                history.map(m => <MatchRecord key={m.id} match={m} onSave={result => setHistory(current => current.map(item => item.id === m.id ? {...item, result} : item))} />)
              )}
            </section>
          )}
          <footer>
            <strong>TEAMFORGE</strong>
            <span>내전 팀 편성 도구</span>
            <small>외부 게임 서비스와 무관한 개인 프로젝트입니다.</small>
          </footer>
        </main>
      </div>
      {modal && (
        <div className="modal-backdrop">
          <section
            className="modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="modal-title"
          >
            <button
              className="modal-close"
              aria-label="등록 닫기"
              onClick={() => setModal(false)}
            >
              <X />
            </button>
            <h2 id="modal-title">{editingId ? "참여자 수정" : "참여자 등록"}</h2>
            <p>
              {game === "LOL" ? "리그 오브 레전드" : "오버워치"} · 데모 수동
              등록
            </p>
            {game === "LOL" && <RiotLookup initialId={riotId} onApply={(account,id)=>{setRiotId(id);setRating(String(account.score));setDraftTier(account.tier);setScoreSource('demo-api');setCheckedAt(new Date().toISOString());}}/>}
            <form onSubmit={add}>
              <label>
                닉네임
                <input
                  autoFocus
                  required
                  maxLength={20}
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="닉네임"
                />
              </label>
              <label>
                실력 점수
                <input
                  required
                  type="number"
                  min="0"
                  max="10000"
                  step="1"
                  value={rating}
                  onChange={(e) => {setRating(e.target.value);setScoreSource("manual");}}
                />
              </label>
              <p className="score-source">점수 출처: {scoreSource === 'demo-api' ? '데모 조회' : scoreSource === 'sample' ? '샘플 점수' : '수동 입력/보정'} · 수동 변경 시 입력한 점수가 우선합니다.</p>
              <fieldset className="role-options">
                <legend>선호 포지션 <small>복수 선택 · 우선순위 없음</small></legend>
                {roles[game].map(r => <label key={r}><input type="checkbox" checked={preferredRoles.includes(r)} onChange={() => setPreferredRoles(current => current.includes(r) ? current.filter(x => x !== r) : [...current, r])}/>{r}</label>)}
                {preferredRoles.length === 0 && <p role="alert">포지션을 하나 이상 선택해주세요.</p>}
              </fieldset>
              <button className="primary" type="submit" disabled={preferredRoles.length === 0}>
                <Check size={18} />
                {editingId ? "수정 저장" : "참여자 등록"}
              </button>
            </form>
          </section>
        </div>
      )}
    </div>
  );
}
