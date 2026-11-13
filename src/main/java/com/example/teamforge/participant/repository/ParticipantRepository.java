package com.example.teamforge.participant.repository;

import com.example.teamforge.participant.entity.Participant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface ParticipantRepository extends JpaRepository<Participant, UUID> {

    Page<Participant> findAllByDeletedAtIsNullOrderByNameAscIdAsc(Pageable pageable);

    Optional<Participant> findByIdAndDeletedAtIsNull(UUID id);

    List<Participant> findAllByIdInAndDeletedAtIsNull(List<UUID> ids);

    /** 확정까지 수정·삭제를 막는다. 잠금 획득 순서는 불변인 ID순으로 통일한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Participant p where p.id in :ids order by p.id")
    List<Participant> findAllForMatchConfirmation(@Param("ids") List<UUID> ids);
}
