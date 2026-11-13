package com.example.teamforge.external.riot;

import com.example.teamforge.common.exception.BusinessException;
import com.example.teamforge.common.exception.ErrorCode;
import com.example.teamforge.gameprofile.entity.Game;
import com.example.teamforge.gameprofile.entity.RankTier;

import java.util.List;

/** 정상 응답에서 솔로랭크가 없으면 tier·division·leaguePoints가 모두 null이다. */
public record RiotRank(String puuid, String tier, String division, Integer leaguePoints) {
    public int score() {
        if (tier == null) return 0;
        if (leaguePoints == null || leaguePoints < 0) throw new BusinessException(ErrorCode.RIOT_UNAVAILABLE);
        RankTier rankTier;
        try {
            rankTier = RankTier.valueOf(tier);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.RIOT_UNAVAILABLE);
        }
        Integer base = rankTier.score(Game.LOL, null);
        if (base == null) {
            int divisionNumber = List.of("I", "II", "III", "IV").indexOf(division == null ? "" : division) + 1;
            base = rankTier.score(Game.LOL, divisionNumber);
        }
        if (base == null || (long) base + leaguePoints > Integer.MAX_VALUE) {
            throw new BusinessException(ErrorCode.RIOT_UNAVAILABLE);
        }
        return base + leaguePoints;
    }
}
