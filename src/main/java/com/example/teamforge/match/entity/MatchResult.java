package com.example.teamforge.match.entity;

import com.example.teamforge.common.exception.BusinessException;
import com.example.teamforge.common.exception.ErrorCode;

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
        if (scoreA < 0 || scoreB < 0) throw new BusinessException(ErrorCode.INVALID_MATCH_SCORE);
        if (memo != null && memo.length() > 2000) throw new BusinessException(ErrorCode.INVALID_MATCH_MEMO);
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
