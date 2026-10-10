package com.example.teamforge.gameprofile.repository;

import com.example.teamforge.gameprofile.dto.GameProfileListRow;
import com.example.teamforge.gameprofile.entity.Game;
import com.example.teamforge.gameprofile.entity.GameProfile;
import com.example.teamforge.gameprofile.entity.Position;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GameProfileRepository extends JpaRepository<GameProfile, UUID> {
    Optional<GameProfile> findByParticipantIdAndGame(UUID participantId, Game game);

    @Query(value = """
            select new com.example.teamforge.gameprofile.dto.GameProfileListRow(g.id, p.name)
            from GameProfile g join Participant p on p.id = g.participantId
            where p.deletedAt is null and g.game = :game
              and (:keyword is null or lower(p.name) like :keyword escape '!')
              and (:position is null or :position member of g.preferredPositions)
            order by p.name asc, p.id asc
            """, countQuery = """
            select count(g) from GameProfile g join Participant p on p.id = g.participantId
            where p.deletedAt is null and g.game = :game
              and (:keyword is null or lower(p.name) like :keyword escape '!')
              and (:position is null or :position member of g.preferredPositions)
            """)
    Page<GameProfileListRow> findActiveList(@Param("game") Game game, @Param("keyword") String keyword,
                                          @Param("position") Position position, Pageable pageable);

    /** 페이지 대상만 조회한 후 포지션 컬렉션을 함께 읽어 개별 추가 조회를 방지한다. */
    @Query("select distinct g from GameProfile g left join fetch g.preferredPositions where g.id in :ids")
    List<GameProfile> findAllWithPositionsByIdIn(@Param("ids") List<UUID> ids);
}
