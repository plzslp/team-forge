package com.example.teamforge.gameprofile.entity;

import com.example.teamforge.common.exception.BusinessException;
import com.example.teamforge.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotNull;

@Embeddable
public record ManualRank(
        @NotNull @Enumerated(EnumType.STRING) @Column(name = "manual_tier") RankTier tier,
        @Column(name = "manual_division") Integer division
) {
    public int score(Game game) {
        Integer score = tier == null ? null : tier.score(game, division);
        if (score == null) throw new BusinessException(ErrorCode.INVALID_MANUAL_RANK);
        return score;
    }
}
