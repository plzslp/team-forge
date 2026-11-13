import { rankTiers, type Game, type RankTier } from './domain';
import { rankEmblems } from './rankEmblems';

export default function RankBadge({ game, tier, division }: {
  game: Game;
  tier: string;
  division?: number | string | null;
}) {
  if (!Object.hasOwn(rankTiers, tier)) return <span>{tier}{division != null && ` ${division}`}</span>;
  const rank = tier as RankTier;
  const emblem = rankEmblems[game][rank];
  return <span className="rank-badge">
    {emblem && (
      <svg className="rank-emblem" width="24" height="24" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
        <path d={emblem.silhouette} fill={emblem.colors[0]} />
        <path d={emblem.face} fill={emblem.colors[1]} />
        <path d={emblem.highlight} fill={emblem.colors[2]} />
      </svg>
    )}
    <span>{rankTiers[rank]}{division != null && ` ${division}`}</span>
  </span>;
}
