import { Crown, Diamond, Gem, Shield, ShieldCheck, Star, Swords, Trophy } from 'lucide-react';
import { rankTiers, type Game, type RankTier } from './domain';

const rankIcons = {
  IRON: Shield, BRONZE: ShieldCheck, SILVER: Star, GOLD: Trophy, PLATINUM: Diamond,
  EMERALD: Gem, DIAMOND: Gem, MASTER: Swords, GRANDMASTER: Crown, CHALLENGER: Trophy, CHAMPION: Crown,
};

export default function RankBadge({ game, tier, division }: {
  game: Game;
  tier: string;
  division?: number | string | null;
}) {
  if (!Object.hasOwn(rankTiers, tier)) return <span>{tier}{division != null && ` ${division}`}</span>;
  const knownTier = tier as RankTier;
  const Icon = rankIcons[knownTier];
  return <span className={`rank-badge rank-${knownTier.toLowerCase()} rank-${game.toLowerCase()}`}>
    <span className="rank-icon" aria-hidden="true"><Icon size={12} strokeWidth={2.4} /></span>
    <span>{rankTiers[knownTier]}{division != null && ` ${division}`}</span>
  </span>;
}
