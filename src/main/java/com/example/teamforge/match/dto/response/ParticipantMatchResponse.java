package com.example.teamforge.match.dto.response;

import com.example.teamforge.match.entity.Match;
import com.example.teamforge.match.entity.MatchParticipant.Team;
import com.example.teamforge.match.entity.MatchResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record ParticipantMatchResponse(UUID matchId, Instant createdAt, Team team,
                                       @Schema(nullable = true) Integer scoreA,
                                       @Schema(nullable = true) Integer scoreB,
                                       @Schema(description = "참가자 소속 팀 기준 결과. 미입력은 null", nullable = true)
                                       MatchResult.Outcome outcome) {
    public ParticipantMatchResponse(Match match, Team team) {
        this(match.getId(), match.getCreatedAt(), team,
                match.getResult() == null ? null : match.getResult().getScoreA(),
                match.getResult() == null ? null : match.getResult().getScoreB(),
                match.getResult() == null ? null : match.getResult().outcomeFor(team));
    }
}
