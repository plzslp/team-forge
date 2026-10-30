package com.example.teamforge.match.dto;

import com.example.teamforge.gameprofile.entity.Game;
import com.example.teamforge.gameprofile.entity.Position;
import com.example.teamforge.match.entity.Match;
import com.example.teamforge.match.entity.MatchParticipant;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** 확정 당시 스냅샷. JPA 엔티티나 최신 프로필을 응답에 직접 노출하지 않는다. */
public record MatchCreateResponse(UUID id, Game game, Instant createdAt, List<TeamResponse> teams, long scoreDifference) {
    public static MatchCreateResponse from(Match match) {
        List<TeamResponse> teams = List.of(team(match, MatchParticipant.Team.A), team(match, MatchParticipant.Team.B));
        return new MatchCreateResponse(match.getId(), match.getGame(), match.getCreatedAt(), teams,
                Math.abs(teams.get(0).totalScore() - teams.get(1).totalScore()));
    }

    private static TeamResponse team(Match match, MatchParticipant.Team team) {
        List<MemberResponse> members = match.getParticipants().stream()
                .filter(participant -> participant.getTeam() == team)
                .sorted(Comparator.comparing(MatchParticipant::getAssignedPosition).thenComparing(MatchParticipant::getParticipantId))
                .map(participant -> new MemberResponse(participant.getParticipantId(), participant.getName(),
                        participant.getScore(), participant.getAssignedPosition())).toList();
        return new TeamResponse(team, members, members.stream().mapToLong(MemberResponse::score).sum());
    }

    public record TeamResponse(MatchParticipant.Team team, List<MemberResponse> participants, long totalScore) {
    }

    public record MemberResponse(UUID participantId, String name, int score, Position assignedPosition) {
    }
}
