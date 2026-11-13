package com.example.teamforge.gameprofile.dto.response;

import com.example.teamforge.gameprofile.entity.Game;
import com.example.teamforge.gameprofile.entity.GameProfile;
import com.example.teamforge.gameprofile.entity.Position;
import com.example.teamforge.gameprofile.entity.ManualRank;
import com.example.teamforge.gameprofile.dto.response.GameProfileResponse.RiotRankResponse;

import java.util.Set;
import java.util.UUID;

public record GameProfileListResponse(UUID participantId, UUID profileId, String name,
                                      Game game, String accountId, int effectiveScore,
                                      Set<Position> preferredPositions, Integer manualScore, ManualRank manualRank,
                                      RiotRankResponse riotRank) {
    public static GameProfileListResponse from(GameProfile profile, String name) {
        return new GameProfileListResponse(profile.getParticipantId(), profile.getId(), name,
                profile.getGame(), profile.getAccountId(), profile.effectiveScore(), profile.getPreferredPositions(),
                profile.getManualScore(), profile.getManualRank(), RiotRankResponse.from(profile));
    }
}
