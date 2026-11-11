import type { Game, Match, Participant, Preview, Profile, ProfileInput, ResultInput } from './domain';

export class ApiError extends Error {
  constructor(readonly status: number, readonly code?: string, message = '요청을 처리하지 못했습니다.') {
    super(message);
  }
}

export const sessionExpired = 'teamforge:session-expired';

function csrfCookie() {
  const token = document.cookie.split('; ').find(cookie => cookie.startsWith('XSRF-TOKEN='));
  return token && decodeURIComponent(token.slice('XSRF-TOKEN='.length));
}

async function requestError(response: Response) {
  const error = response.headers.get('content-type')?.includes('application/json')
    ? await response.json().catch(() => null) : null;
  return new ApiError(response.status, error?.code, error?.message ??
    (response.status === 403 ? '요청이 거절됐습니다. 페이지를 새로고침한 뒤 다시 시도하세요.'
      : '서버가 요청을 처리할 수 없습니다. 잠시 후 다시 시도하세요.'));
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  headers.set('Accept', 'application/json');
  if (options.body) headers.set('Content-Type', 'application/json');
  if (options.method && options.method !== 'GET') {
    headers.set('X-XSRF-TOKEN', await sessionCsrfToken());
  }
  const response = await fetch(path, { ...options, headers, credentials: 'same-origin' });
  if (response.status === 401 && path !== '/api/auth/me') window.dispatchEvent(new Event(sessionExpired));
  if (!response.ok) throw await requestError(response);
  if (response.status === 204) return undefined as T;
  return response.json().catch(() => { throw new Error('서버 응답을 읽지 못했습니다. 백엔드 연결을 확인하세요.'); });
}

async function sessionCsrfToken() {
  const existing = csrfCookie();
  if (existing) return existing;
  await request<void>('/api/auth/csrf');
  const token = csrfCookie();
  if (!token) throw new Error('변경 요청에 필요한 쿠키를 받지 못했습니다.');
  return token;
}

const json = (method: string, body: unknown): RequestInit => ({ method, body: JSON.stringify(body) });
const participantPath = (id: string) => `/api/participants/${encodeURIComponent(id)}`;
const profilePath = (id: string, game: Game) => `${participantPath(id)}/profiles/${game}`;

export async function currentAccount() {
  try {
    return await request<{ username: string }>('/api/auth/me');
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return null;
    throw error;
  }
}

export async function signIn(username: string, password: string) {
  // Spring Security 기본 폼의 GET 요청에서 로그인에 필요한 CSRF 쿠키를 받는다.
  const form = await fetch('/login', { credentials: 'same-origin', cache: 'no-store' });
  if (!form.ok) throw await requestError(form);
  const token = csrfCookie();
  if (!token) throw new Error('로그인 확인에 필요한 쿠키를 받지 못했습니다.');
  const response = await fetch('/login', {
    method: 'POST',
    body: new URLSearchParams({ username, password }),
    headers: { 'X-XSRF-TOKEN': token },
    credentials: 'same-origin',
    redirect: 'manual',
  });
  if (response.type !== 'opaqueredirect' && !response.ok) throw await requestError(response);
  const account = await currentAccount();
  if (!account) throw new Error('아이디 또는 비밀번호를 확인하세요.');
  return account;
}

export async function signOut() {
  const token = await sessionCsrfToken();
  const response = await fetch('/logout', {
    method: 'POST',
    headers: { 'X-XSRF-TOKEN': token },
    credentials: 'same-origin',
    redirect: 'manual',
  });
  if (response.type !== 'opaqueredirect' && !response.ok) throw await requestError(response);
}

export const api = {
  get: <T>(path: string, signal?: AbortSignal) => request<T>(path, { signal }),
  createParticipant: (name: string) => request<Participant>('/api/participants', json('POST', { name })),
  renameParticipant: (id: string, name: string) => request<Participant>(participantPath(id), json('PATCH', { name })),
  deleteParticipant: (id: string) => request<void>(participantPath(id), { method: 'DELETE' }),
  saveProfile: (id: string, game: Game, input: ProfileInput) => request<Profile>(profilePath(id, game), json('PUT', input)),
  refreshRank: (id: string) => request<Profile>(`${profilePath(id, 'LOL')}/refresh`, { method: 'POST' }),
  preview: (game: Game, participantIds: string[], allowNonPreferredPositions: boolean) =>
    request<Preview>('/api/team-previews', json('POST', { game, participantIds, allowNonPreferredPositions })),
  confirm: (game: Game, preview: Preview, allowNonPreferredPositions: boolean) =>
    request<{ id: string }>('/api/matches', json('POST', {
      game,
      participants: preview.teams.flatMap(({ team, participants }) => participants.map(({ participantId, assignedPosition }) => ({
        participantId, team, assignedPosition,
      }))),
      participantVersions: preview.participantVersions,
      allowNonPreferredPositions,
    })),
  saveResult: (id: string, result: ResultInput) =>
    request<Match>(`/api/matches/${encodeURIComponent(id)}/result`, json('PUT', result)),
};

export function errorMessage(error: unknown) {
  if (error instanceof ApiError || (error instanceof Error && !(error instanceof TypeError))) return error.message;
  return '서버에 연결하지 못했습니다. 연결 상태를 확인하고 다시 시도하세요.';
}
