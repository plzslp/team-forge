package com.example.teamforge.gameprofile.repository;

import com.example.teamforge.gameprofile.entity.Game;
import com.example.teamforge.gameprofile.entity.GameProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GameProfileRepository extends JpaRepository<GameProfile, UUID> {
    Optional<GameProfile> findByParticipantIdAndGame(UUID participantId, Game game);
}
