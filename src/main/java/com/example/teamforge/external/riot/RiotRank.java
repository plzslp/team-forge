package com.example.teamforge.external.riot;

import com.example.teamforge.common.exception.BusinessException;
import com.example.teamforge.common.exception.ErrorCode;

import java.util.List;

/** 정상 응답에서 솔로랭크가 없으면 tier·division·leaguePoints가 모두 null이다. */
public record RiotRank(String puuid, String tier, String division, Integer leaguePoints) {
    public int score() {
        if (tier == null) return 0;
        if (leaguePoints == null || leaguePoints < 0) throw new BusinessException(ErrorCode.RIOT_UNAVAILABLE);
        int base = switch (tier) {
            case "MASTER" -> 2800;
            case "GRANDMASTER" -> 3000;
            case "CHALLENGER" -> 3200;
            default -> {
                int tierIndex = List.of("IRON", "BRONZE", "SILVER", "GOLD", "PLATINUM", "EMERALD", "DIAMOND").indexOf(tier);
                int divisionIndex = List.of("IV", "III", "II", "I").indexOf(division == null ? "" : division);
                if (tierIndex < 0 || divisionIndex < 0) throw new BusinessException(ErrorCode.RIOT_UNAVAILABLE);
                yield tierIndex * 400 + divisionIndex * 100;
            }
        };
        if ((long) base + leaguePoints > Integer.MAX_VALUE) throw new BusinessException(ErrorCode.RIOT_UNAVAILABLE);
        return base + leaguePoints;
    }
}
