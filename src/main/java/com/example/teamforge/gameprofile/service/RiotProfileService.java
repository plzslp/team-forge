package com.example.teamforge.gameprofile.service;

import com.example.teamforge.common.exception.BusinessException;
import com.example.teamforge.common.exception.ErrorCode;
import com.example.teamforge.external.riot.RiotClient;
import com.example.teamforge.external.riot.RiotRank;
import com.example.teamforge.gameprofile.dto.response.GameProfileResponse;
import com.example.teamforge.gameprofile.entity.Game;
import com.example.teamforge.gameprofile.entity.GameProfile;
import com.example.teamforge.gameprofile.repository.GameProfileRepository;
import com.example.teamforge.participant.entity.Participant;
import com.example.teamforge.participant.repository.ParticipantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.persistence.EntityManager;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class RiotProfileService {
    private final ParticipantRepository participants;
    private final GameProfileRepository profiles;
    private final RiotClient riotClient;
    private final TransactionTemplate transaction;
    private final EntityManager entityManager;

    public RiotProfileService(ParticipantRepository participants, GameProfileRepository profiles,
                              RiotClient riotClient, PlatformTransactionManager transactionManager,
                              EntityManager entityManager) {
        this.participants = participants;
        this.profiles = profiles;
        this.riotClient = riotClient;
        this.transaction = new TransactionTemplate(transactionManager);
        this.entityManager = entityManager;
    }

    public GameProfileResponse refresh(UUID participantId) {
        Snapshot snapshot = transaction.execute(status -> {
            Participant participant = participants.findByIdAndDeletedAtIsNull(participantId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
            GameProfile profile = profiles.findByParticipantIdAndGame(participantId, Game.LOL)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
            return new Snapshot(participant.getVersion(), profile.getVersion(), profile.getAccountId());
        });

        // HTTP 통신은 트랜잭션 밖에서 수행하고, 저장 직전에 참가자 → 프로필 순서로 잠근다.
        RiotRank rank = riotClient.findSoloRank(snapshot.accountId());
        return transaction.execute(status -> {
            // Open-in-View가 보관한 이전 객체를 비워 잠금 조회에서 최신 활성 상태·버전을 읽는다.
            entityManager.clear();
            Participant participant = participants.findAllForMatchConfirmation(List.of(participantId)).stream()
                    .findFirst().orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
            if (!participant.active()) throw new BusinessException(ErrorCode.PARTICIPANT_DELETED);
            GameProfile profile = profiles.findAllForMatchConfirmation(List.of(participantId), Game.LOL).stream()
                    .findFirst().orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
            if (participant.getVersion() != snapshot.participantVersion()
                    || profile.getVersion() != snapshot.profileVersion()
                    || !Objects.equals(profile.getAccountId(), snapshot.accountId())) {
                throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION);
            }
            profile.refreshRiotRank(rank.puuid(), rank.tier(), rank.division(), rank.leaguePoints(),
                    rank.score(), Instant.now());
            profiles.flush();
            return GameProfileResponse.from(profile);
        });
    }

    private record Snapshot(long participantVersion, long profileVersion, String accountId) {}
}
