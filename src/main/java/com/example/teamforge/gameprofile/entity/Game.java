package com.example.teamforge.gameprofile.entity;

import java.util.List;

public enum Game {
    LOL, OVERWATCH;

    /** 미리보기 계산과 수동 편성 확정에서 공유하는 팀당 역할 자리 5개. */
    public List<Position> teamPositions() {
        return switch (this) {
            case LOL -> List.of(Position.LOL_TOP, Position.LOL_JUNGLE, Position.LOL_MID,
                    Position.LOL_BOTTOM, Position.LOL_SUPPORT);
            case OVERWATCH -> List.of(Position.OVERWATCH_TANK, Position.OVERWATCH_DAMAGE,
                    Position.OVERWATCH_DAMAGE, Position.OVERWATCH_SUPPORT, Position.OVERWATCH_SUPPORT);
        };
    }
}
