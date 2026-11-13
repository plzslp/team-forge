package com.example.teamforge.gameprofile.entity;

public enum RankTier {
    IRON(0, null),
    BRONZE(400, 0),
    SILVER(800, 500),
    GOLD(1200, 1000),
    PLATINUM(1600, 1500),
    EMERALD(2000, 2000),
    DIAMOND(2400, 2500),
    MASTER(2800, 3000),
    GRANDMASTER(3000, 3500),
    CHALLENGER(3200, null),
    CHAMPION(null, 4000);

    private final Integer lolBase;
    private final Integer overwatchBase;

    RankTier(Integer lolBase, Integer overwatchBase) {
        this.lolBase = lolBase;
        this.overwatchBase = overwatchBase;
    }

    /** 게임과 디비전이 맞지 않으면 null을 반환한다. */
    public Integer score(Game game, Integer division) {
        Integer base = game == Game.LOL ? lolBase : overwatchBase;
        if (base == null) return null;
        if (game == Game.LOL && base >= 2800) return division == null ? base : null;
        int divisions = game == Game.LOL ? 4 : 5;
        if (division == null || division < 1 || division > divisions) return null;
        return base + (divisions - division) * 100;
    }
}
