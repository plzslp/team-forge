package com.example.teamforge.participant.dto;

import com.example.teamforge.participant.entity.Game;
import com.example.teamforge.participant.entity.GameProfile;
import com.example.teamforge.participant.entity.Position;

import java.util.Set;
import java.util.UUID;

public record GameProfileResponse(UUID id, UUID participantId, Game game, String accountId,
                                  int baseScore, Integer manualScore, int effectiveScore,
                                  Set<Position> preferredPositions) {
    public static GameProfileResponse from(GameProfile profile) {
        return new GameProfileResponse(profile.getId(), profile.getParticipantId(), profile.getGame(),
                profile.getAccountId(), profile.getBaseScore(), profile.getManualScore(),
                profile.effectiveScore(), profile.getPreferredPositions());
    }
}
