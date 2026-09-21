package com.example.teamforge.participant.entity;

import com.example.teamforge.common.exception.BusinessException;
import com.example.teamforge.common.exception.ErrorCode;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "game_profiles", uniqueConstraints =
        @UniqueConstraint(columnNames = {"participant_id", "game"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameProfile {
    @Id
    private UUID id;

    @Column(name = "participant_id", nullable = false)
    private UUID participantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Game game;

    private String accountId;

    @Column(nullable = false)
    private int baseScore;

    private Integer manualScore;

    @ElementCollection
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "game_profile_positions", joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "position", nullable = false)
    @Getter(AccessLevel.NONE)
    private Set<Position> preferredPositions = new HashSet<>();

    @Version
    private Long version;

    public GameProfile(UUID participantId, Game game, String accountId, int baseScore, Integer manualScore, Set<Position> preferredPositions) {
        this.id = UUID.randomUUID();
        this.participantId = Objects.requireNonNull(participantId);
        this.game = Objects.requireNonNull(game);
        this.accountId = accountId;
        refreshScore(baseScore);
        overrideScore(manualScore);
        changePreferredPositions(preferredPositions);
    }

    public Set<Position> getPreferredPositions() {
        return Set.copyOf(preferredPositions);
    }

    public void changePreferredPositions(Set<Position> positions) {
        Set<Position> copy = Set.copyOf(positions);
        if (copy.isEmpty() || copy.stream().anyMatch(position -> !position.supports(game))) {
            throw new BusinessException(ErrorCode.INVALID_PREFERRED_POSITION);
        }
        preferredPositions.clear();
        preferredPositions.addAll(copy);
    }

    public int effectiveScore() {
        return manualScore != null ? manualScore : baseScore;
    }

    public GameProfile overrideScore(Integer score) {
        if (score != null && score < 0) throw new BusinessException(ErrorCode.INVALID_SCORE);
        this.manualScore = score;
        return this;
    }

    /** 조회 성공 시에만 호출하며 기존 수동 보정은 유지한다. */
    public GameProfile refreshScore(int score) {
        if (score < 0) throw new BusinessException(ErrorCode.INVALID_SCORE);
        this.baseScore = score;
        return this;
    }
}
