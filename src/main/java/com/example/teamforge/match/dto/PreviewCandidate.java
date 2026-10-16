package com.example.teamforge.match.dto;

import com.example.teamforge.gameprofile.entity.Position;

import java.util.Set;
import java.util.UUID;

/** DB에서 읽은 편성 입력. 계산 로직은 JPA 엔티티와 분리한다. */
public record PreviewCandidate(UUID participantId, String name, int score, Set<Position> preferredPositions) {
    public PreviewCandidate {
        preferredPositions = Set.copyOf(preferredPositions);
    }
}
