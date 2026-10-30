package com.example.teamforge.match.dto;

import com.example.teamforge.gameprofile.entity.Game;
import com.example.teamforge.match.entity.Match;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 확정 당시 스냅샷. JPA 엔티티나 최신 프로필을 응답에 직접 노출하지 않는다. */
public record MatchCreateResponse(UUID id, Game game, Instant createdAt, List<MatchTeamResponse> teams, long scoreDifference) {
    public static MatchCreateResponse from(Match match) {
        List<MatchTeamResponse> teams = MatchTeamResponse.from(match);
        return new MatchCreateResponse(match.getId(), match.getGame(), match.getCreatedAt(), teams,
                Math.abs(teams.get(0).totalScore() - teams.get(1).totalScore()));
    }
}
