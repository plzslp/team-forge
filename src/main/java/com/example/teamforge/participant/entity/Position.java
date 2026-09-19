package com.example.teamforge.participant.entity;

/** 게임별 포지션. enum 이름을 DB에 문자열로 저장한다. */
public enum Position {
    LOL_TOP(Game.LOL),
    LOL_JUNGLE(Game.LOL),
    LOL_MID(Game.LOL),
    LOL_BOTTOM(Game.LOL),
    LOL_SUPPORT(Game.LOL),
    OVERWATCH_TANK(Game.OVERWATCH),
    OVERWATCH_DAMAGE(Game.OVERWATCH),
    OVERWATCH_SUPPORT(Game.OVERWATCH);

    private final Game game;

    Position(Game game) {
        this.game = game;
    }

    public boolean supports(Game game) {
        return this.game == game;
    }
}
