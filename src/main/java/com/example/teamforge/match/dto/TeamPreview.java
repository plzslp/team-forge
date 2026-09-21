package com.example.teamforge.match.dto;

import com.example.teamforge.common.exception.BusinessException;
import com.example.teamforge.common.exception.ErrorCode;

import com.example.teamforge.participant.entity.Game;
import com.example.teamforge.match.entity.Match;
import com.example.teamforge.match.entity.MatchParticipant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/** 미저장 계산 결과. 최신 정보와의 버전 비교는 확정 서비스에서 구현한다. */
public record TeamPreview(
        Game game,
        List<MatchParticipant> participants,
        Map<UUID, ParticipantVersions> participantVersions
) {
    public TeamPreview {
        Objects.requireNonNull(game);
        participants = List.copyOf(participants);
        participantVersions = Map.copyOf(participantVersions);
        Match.validateTeams(game, participants);
        if (!participantVersions.keySet().equals(participants.stream()
                .map(MatchParticipant::getParticipantId).collect(Collectors.toSet()))) {
            throw new BusinessException(ErrorCode.PREVIEW_PARTICIPANTS_MISMATCH);
        }
    }
}
