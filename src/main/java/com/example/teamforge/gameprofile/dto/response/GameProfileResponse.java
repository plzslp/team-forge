package com.example.teamforge.gameprofile.dto.response;

import com.example.teamforge.gameprofile.entity.Game;
import com.example.teamforge.gameprofile.entity.GameProfile;
import com.example.teamforge.gameprofile.entity.Position;
import com.example.teamforge.gameprofile.entity.ManualRank;

import java.util.Set;
import java.util.UUID;
import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;

public record GameProfileResponse(UUID id, UUID participantId, Game game, String accountId,
                                  int baseScore, Integer manualScore, ManualRank manualRank, int effectiveScore,
                                  Set<Position> preferredPositions,
                                  @Schema(description = "Riot 조회 전은 null. 조회된 언랭크는 tier가 null", nullable = true)
                                  RiotRankResponse riotRank) {
    public static GameProfileResponse from(GameProfile profile) {
        return new GameProfileResponse(profile.getId(), profile.getParticipantId(), profile.getGame(),
                profile.getAccountId(), profile.getBaseScore(), profile.getManualScore(), profile.getManualRank(),
                profile.effectiveScore(), profile.getPreferredPositions(), RiotRankResponse.from(profile));
    }

    public record RiotRankResponse(String tier, String division, Integer leaguePoints, Instant updatedAt) {
        static RiotRankResponse from(GameProfile profile) {
            return profile.getRiotUpdatedAt() == null ? null : new RiotRankResponse(profile.getRiotTier(),
                    profile.getRiotDivision(), profile.getRiotLeaguePoints(), profile.getRiotUpdatedAt());
        }
    }
}
