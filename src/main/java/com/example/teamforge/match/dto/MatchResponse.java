package com.example.teamforge.match.dto;

import com.example.teamforge.gameprofile.entity.Game;
import com.example.teamforge.match.entity.Match;
import com.example.teamforge.match.entity.MatchParticipant;
import com.example.teamforge.match.entity.MatchResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 경기 기록의 목록·상세 조회. 결과 미입력(null)과 0:0 무승부를 구분한다. */
public record MatchResponse(UUID id, Game game, Instant createdAt, List<MatchTeamResponse> teams, long scoreDifference,
                            @Schema(description = "미입력은 null, 입력된 0:0은 무승부", nullable = true) ResultResponse result) {
    public static MatchResponse from(Match match) {
        List<MatchTeamResponse> teams = MatchTeamResponse.from(match);
        return new MatchResponse(match.getId(), match.getGame(), match.getCreatedAt(), teams,
                Math.abs(teams.get(0).totalScore() - teams.get(1).totalScore()), ResultResponse.from(match.getResult()));
    }

    public record ResultResponse(int scoreA, int scoreB, String memo, MatchResult.Outcome outcomeA, MatchResult.Outcome outcomeB) {
        private static ResultResponse from(MatchResult result) {
            return result == null ? null : new ResultResponse(result.getScoreA(), result.getScoreB(), result.getMemo(),
                    result.outcomeFor(MatchParticipant.Team.A), result.outcomeFor(MatchParticipant.Team.B));
        }
    }
}
