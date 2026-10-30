package com.example.teamforge.match.dto;

import com.example.teamforge.gameprofile.entity.Position;
import com.example.teamforge.match.entity.Match;
import com.example.teamforge.match.entity.MatchParticipant;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** 생성과 기록 조회가 공유하는 확정 당시 팀 스냅샷. 현재 참가자·프로필을 조회하지 않는다. */
public record MatchTeamResponse(MatchParticipant.Team team, List<MemberResponse> participants, long totalScore) {
    public static List<MatchTeamResponse> from(Match match) {
        return List.of(team(match, MatchParticipant.Team.A), team(match, MatchParticipant.Team.B));
    }

    private static MatchTeamResponse team(Match match, MatchParticipant.Team team) {
        List<MemberResponse> members = match.getParticipants().stream()
                .filter(participant -> participant.getTeam() == team)
                .sorted(Comparator.comparing(MatchParticipant::getAssignedPosition).thenComparing(MatchParticipant::getParticipantId))
                .map(participant -> new MemberResponse(participant.getParticipantId(), participant.getName(),
                        participant.getScore(), participant.getAssignedPosition())).toList();
        return new MatchTeamResponse(team, members, members.stream().mapToLong(MemberResponse::score).sum());
    }

    public record MemberResponse(UUID participantId, String name, int score, Position assignedPosition) {
    }
}
