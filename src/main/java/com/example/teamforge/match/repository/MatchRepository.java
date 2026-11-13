package com.example.teamforge.match.repository;

import com.example.teamforge.match.dto.response.ParticipantMatchResponse;
import com.example.teamforge.match.entity.Match;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.teamforge.gameprofile.entity.Game;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchRepository extends JpaRepository<Match, UUID> {
    @Query(value = """
            select new com.example.teamforge.match.dto.response.ParticipantMatchResponse(m, p.team)
            from Match m join m.participants p
            where p.participantId = :participantId and m.game = :game
            order by m.createdAt desc, m.id desc
            """,
            countQuery = """
            select count(m) from Match m join m.participants p
            where p.participantId = :participantId and m.game = :game
            """)
    Page<ParticipantMatchResponse> findParticipantMatches(@Param("participantId") UUID participantId,
                                                          @Param("game") Game game, Pageable pageable);

    /** 컬렉션 fetch join 전에 페이지 대상만 결정해 DB에서 정확히 페이징한다. */
    @Query(value = "select m.id from Match m where m.game = :game order by m.createdAt desc, m.id desc",
            countQuery = "select count(m) from Match m where m.game = :game")
    Page<UUID> findPageIdsByGame(@Param("game") Game game, Pageable pageable);

    @Query("select distinct m from Match m left join fetch m.participants where m.id in :ids")
    List<Match> findAllWithParticipantsByIdIn(@Param("ids") List<UUID> ids);

    @Query("select distinct m from Match m left join fetch m.participants where m.id = :id")
    Optional<Match> findWithParticipantsById(@Param("id") UUID id);

    @Query("select distinct m from Match m left join fetch m.participants where m.confirmationKey = :key")
    Optional<Match> findByConfirmationKey(@Param("key") UUID key);
}
