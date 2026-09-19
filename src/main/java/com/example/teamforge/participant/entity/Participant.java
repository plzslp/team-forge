package com.example.teamforge.participant.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "participants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Participant {
    @Id
    private UUID id;

    @Column(nullable = false, length = 20)
    private String name;

    private Instant deletedAt;

    @Version
    private Long version;

    private Participant(String name) {
        this.id = UUID.randomUUID();
        rename(name);
    }

    public static Participant register(String name) {
        return new Participant(name);
    }

    public Participant rename(String name) {
        if (!active()) throw new IllegalStateException("삭제된 참여자는 수정할 수 없습니다.");
        if (name == null || name.isBlank() || name.strip().length() > 20) {
            throw new IllegalArgumentException("이름은 공백이 아닌 1~20자여야 합니다.");
        }
        this.name = name.strip();
        return this;
    }

    /** 물리 삭제 대신 사용한다. 조회 시 deletedAt 조건은 repository에서 적용해야 한다. */
    public Participant delete(Instant now) {
        Objects.requireNonNull(now);
        if (active()) deletedAt = now;
        return this;
    }

    public boolean active() {
        return deletedAt == null;
    }
}
