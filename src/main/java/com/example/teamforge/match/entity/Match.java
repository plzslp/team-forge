package com.example.teamforge.match.entity;

import com.example.teamforge.common.exception.BusinessException;
import com.example.teamforge.common.exception.ErrorCode;

import com.example.teamforge.gameprofile.entity.Game;
import com.example.teamforge.gameprofile.entity.Position;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "matches")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Match {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Game game;

    @Column(nullable = false)
    private Instant createdAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "match_id", nullable = false)
    @Getter(AccessLevel.NONE)
    private List<MatchParticipant> participants = new ArrayList<>();

    @Embedded
    private MatchResult result;

    @Version
    private Long version;

    /** 최신 정보·팀별 역할·비선호 허용은 확정 서비스에서 검증한 뒤 생성한다. */
    public Match(UUID id, Game game, Instant createdAt, List<MatchParticipant> participants, MatchResult result) {
        this.id = Objects.requireNonNull(id);
        this.game = Objects.requireNonNull(game);
        this.createdAt = Objects.requireNonNull(createdAt);
        validateTeams(game, participants);
        participants.forEach(p -> this.participants.add(p.copyForMatch()));
        this.result = result;
    }

    public List<MatchParticipant> getParticipants() {
        return List.copyOf(participants);
    }

    public Match recordResult(MatchResult result) {
        this.result = Objects.requireNonNull(result);
        return this;
    }

    /** 미리보기와 확정 기록에서 공유하는 인원·게임별 포지션 검증. */
    public static void validateTeams(Game game, List<MatchParticipant> participants) {
        Objects.requireNonNull(game);
        if (participants.size() != 10
                || participants.stream().map(MatchParticipant::getParticipantId).distinct().count() != 10
                || participants.stream().filter(p -> p.getTeam() == MatchParticipant.Team.A).count() != 5) {
            throw new BusinessException(ErrorCode.INVALID_TEAM_COMPOSITION);
        }
        if (participants.stream().anyMatch(p -> !p.getAssignedPosition().supports(game))) {
            throw new BusinessException(ErrorCode.POSITION_GAME_MISMATCH);
        }
    }

    /** 수동 교체 후에도 팀별로 게임이 요구하는 역할 인원을 정확히 채웠는지 확인한다. */
    public static void validateRoleComposition(Game game, List<MatchParticipant> participants) {
        validateTeams(game, participants);
        List<Position> positions = game.teamPositions();
        for (MatchParticipant.Team team : MatchParticipant.Team.values()) {
            for (Position position : positions) {
                int required = Collections.frequency(positions, position);
                long assigned = participants.stream()
                        .filter(participant -> participant.getTeam() == team && participant.getAssignedPosition() == position)
                        .count();
                if (assigned != required) throw new BusinessException(ErrorCode.INVALID_TEAM_POSITIONS);
            }
        }
    }
}
