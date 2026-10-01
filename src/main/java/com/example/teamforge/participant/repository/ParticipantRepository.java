package com.example.teamforge.participant.repository;

import com.example.teamforge.participant.entity.Participant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParticipantRepository extends JpaRepository<Participant, UUID> {

    List<Participant> findAllByDeletedAtIsNullOrderByNameAscIdAsc();

    Optional<Participant> findByIdAndDeletedAtIsNull(UUID id);
}
