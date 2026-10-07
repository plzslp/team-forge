package com.example.teamforge.participant.repository;

import com.example.teamforge.participant.entity.Game;
import com.example.teamforge.participant.entity.GameProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GameProfileRepository extends JpaRepository<GameProfile, UUID> {
    Optional<GameProfile> findByParticipantIdAndGame(UUID participantId, Game game);
}
