package com.example.teamforge.match.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.Objects;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchResult {
    public enum Outcome { WIN, LOSS, DRAW }

    @Column(name = "result_score_a")
    private int scoreA;

    @Column(name = "result_score_b")
    private int scoreB;

    @Column(name = "result_memo", length = 2000)
    private String memo;

    public MatchResult(int scoreA, int scoreB, String memo) {
        if (scoreA < 0 || scoreB < 0) throw new IllegalArgumentException("경기 스코어는 음수일 수 없습니다.");
        if (memo != null && memo.length() > 2000) throw new IllegalArgumentException("메모는 2000자 이하로 입력하세요.");
        this.scoreA = scoreA;
        this.scoreB = scoreB;
        this.memo = memo == null ? "" : memo;
    }

    public Outcome outcomeFor(MatchParticipant.Team team) {
        Objects.requireNonNull(team);
        if (scoreA == scoreB) return Outcome.DRAW;
        return (scoreA > scoreB) == (team == MatchParticipant.Team.A) ? Outcome.WIN : Outcome.LOSS;
    }
}
