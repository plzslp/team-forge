package com.example.teamforge.match.dto;

import com.example.teamforge.gameprofile.entity.Game;
import com.example.teamforge.gameprofile.entity.Position;
import com.example.teamforge.match.entity.MatchParticipant;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 미리보기 전용 응답. 저장 엔티티와 엔티티 ID는 공개하지 않는다. */
public record TeamPreviewResponse(Game game, List<TeamResponse> teams, long scoreDifference,
                                  int nonPreferredAssignmentCount, List<String> warnings,
                                  Map<UUID, ParticipantVersions> participantVersions) {
    public static TeamPreviewResponse from(TeamPreview preview, List<PreviewCandidate> candidates) {
        List<MemberResponse> members = preview.participants().stream().map(participant -> {
            PreviewCandidate candidate = candidates.stream()
                    .filter(value -> value.participantId().equals(participant.getParticipantId()))
                    .findFirst().orElseThrow();
            return new MemberResponse(participant.getParticipantId(), participant.getName(), participant.getScore(),
                    participant.getAssignedPosition(), candidate.preferredPositions().contains(participant.getAssignedPosition()));
        }).toList();
        List<TeamResponse> teams = List.of(team(preview, members, MatchParticipant.Team.A),
                team(preview, members, MatchParticipant.Team.B));
        int violations = (int) members.stream().filter(member -> !member.preferredPosition()).count();
        List<String> warnings = violations == 0 ? List.of()
                : List.of("선호 포지션이 아닌 배정이 " + violations + "명 포함되어 있습니다.");
        return new TeamPreviewResponse(preview.game(), teams, Math.abs(teams.get(0).totalScore() - teams.get(1).totalScore()),
                violations, warnings, preview.participantVersions());
    }

    private static TeamResponse team(TeamPreview preview, List<MemberResponse> members, MatchParticipant.Team team) {
        List<UUID> ids = preview.participants().stream().filter(participant -> participant.getTeam() == team)
                .map(MatchParticipant::getParticipantId).toList();
        List<MemberResponse> teamMembers = members.stream().filter(member -> ids.contains(member.participantId())).toList();
        return new TeamResponse(team, teamMembers, teamMembers.stream().mapToLong(MemberResponse::score).sum());
    }

    public record TeamResponse(MatchParticipant.Team team, List<MemberResponse> participants, long totalScore) {
    }

    public record MemberResponse(UUID participantId, String name, int score, Position assignedPosition,
                                 boolean preferredPosition) {
    }
}
