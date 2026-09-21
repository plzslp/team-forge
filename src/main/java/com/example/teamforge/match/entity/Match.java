package com.example.teamforge.match.entity;

import com.example.teamforge.participant.entity.Game;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.ArrayList;
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

    /** 포지션 정책과 최신 정보 검증은 후속 확정 서비스에서 수행한다. */
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
            throw new IllegalArgumentException("중복 없는 참가자 10명을 팀당 5명으로 구성해야 합니다.");
        }
        if (participants.stream().anyMatch(p -> !p.getAssignedPosition().supports(game))) {
            throw new IllegalArgumentException("배정 포지션은 내전 게임에 속해야 합니다.");
        }
    }
}
