package com.example.teamforge.match.entity;

import com.example.teamforge.common.exception.BusinessException;
import com.example.teamforge.common.exception.ErrorCode;

import com.example.teamforge.participant.entity.Position;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.Objects;
import java.util.UUID;

/** 확정 당시 값. participant 모듈의 Entity와 직접 연관관계를 맺지 않는다. */
@Entity
@Table(name = "match_participants", uniqueConstraints =
        @UniqueConstraint(columnNames = {"match_id", "participant_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchParticipant {
    public enum Team { A, B }

    @Id
    private UUID id;

    @Column(name = "participant_id", nullable = false)
    private UUID participantId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Position assignedPosition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Team team;

    public MatchParticipant(UUID participantId, String name, int score, Position assignedPosition, Team team) {
        this.id = UUID.randomUUID();
        this.participantId = Objects.requireNonNull(participantId);
        this.team = Objects.requireNonNull(team);
        if (name == null || name.isBlank() || assignedPosition == null || score < 0) {
            throw new BusinessException(ErrorCode.INVALID_MATCH_PARTICIPANT);
        }
        this.name = name;
        this.score = score;
        this.assignedPosition = assignedPosition;
    }

    MatchParticipant copyForMatch() {
        return new MatchParticipant(participantId, name, score, assignedPosition, team);
    }
}
