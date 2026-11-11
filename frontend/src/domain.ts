export const games = {
  LOL: '리그 오브 레전드',
  OVERWATCH: '오버워치',
} as const;

export type Game = keyof typeof games;

export const positionLabels = {
  LOL_TOP: '탑',
  LOL_JUNGLE: '정글',
  LOL_MID: '미드',
  LOL_BOTTOM: '원딜',
  LOL_SUPPORT: '서포터',
  OVERWATCH_TANK: '탱커',
  OVERWATCH_DAMAGE: '딜러',
  OVERWATCH_SUPPORT: '서포터',
} as const;

export type Position = keyof typeof positionLabels;
export const positions: Record<Game, readonly Position[]> = {
  LOL: ['LOL_TOP', 'LOL_JUNGLE', 'LOL_MID', 'LOL_BOTTOM', 'LOL_SUPPORT'],
  OVERWATCH: ['OVERWATCH_TANK', 'OVERWATCH_DAMAGE', 'OVERWATCH_SUPPORT'],
};

export type Participant = { id: string; name: string };
export type ProfileInput = {
  accountId: string | null;
  manualScore: number | null;
  preferredPositions: Position[];
};
export type Profile = ProfileInput & {
  participantId: string;
  game: Game;
  baseScore: number;
  effectiveScore: number;
  riotRank: { tier: string | null; division: string | null; leaguePoints: number | null; updatedAt: string } | null;
};
export type ProfileSummary = Pick<Profile, 'participantId' | 'game' | 'accountId' | 'effectiveScore' | 'preferredPositions'> & {
  name: string;
};
export type Page<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number };
export type Team = 'A' | 'B';
export type Member = { participantId: string; name: string; score: number; assignedPosition: Position };
export type Roster<T extends Member = Member> = { team: Team; participants: T[]; totalScore: number };
export type DraftMember = Member & { preferredPosition: boolean };
export type Preview = {
  teams: Roster<DraftMember>[];
  warnings: string[];
  participantVersions: Record<string, { participantVersion: number; profileVersion: number }>;
};
export type Outcome = 'WIN' | 'LOSS' | 'DRAW';
export type ResultInput = { scoreA: number; scoreB: number; memo: string };
export type Match = {
  id: string;
  game: Game;
  createdAt: string;
  teams: Roster[];
  scoreDifference: number;
  result: (ResultInput & { outcomeA: Outcome; outcomeB: Outcome }) | null;
};
export type ParticipantMatch = {
  matchId: string;
  createdAt: string;
  team: Team;
  scoreA: number | null;
  scoreB: number | null;
  outcome: Outcome | null;
};

export const formatDate = (date: string) => new Date(date).toLocaleString('ko-KR');
