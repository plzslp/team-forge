package com.example.teamforge.gameprofile.dto;

import com.example.teamforge.gameprofile.entity.Game;
import com.example.teamforge.gameprofile.entity.GameProfile;
import com.example.teamforge.gameprofile.entity.Position;

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
