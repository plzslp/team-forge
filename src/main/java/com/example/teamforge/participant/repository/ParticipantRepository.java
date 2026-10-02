package com.example.teamforge.participant.repository;

import com.example.teamforge.participant.entity.Participant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface ParticipantRepository extends JpaRepository<Participant, UUID> {

    Page<Participant> findAllByDeletedAtIsNullOrderByNameAscIdAsc(Pageable pageable);

    Optional<Participant> findByIdAndDeletedAtIsNull(UUID id);
}
