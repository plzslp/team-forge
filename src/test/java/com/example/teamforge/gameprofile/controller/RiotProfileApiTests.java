package com.example.teamforge.gameprofile.controller;

import com.example.teamforge.common.exception.BusinessException;
import com.example.teamforge.common.exception.ErrorCode;
import com.example.teamforge.external.riot.RiotClient;
import com.example.teamforge.external.riot.RiotRank;
import com.example.teamforge.gameprofile.entity.Game;
import com.example.teamforge.gameprofile.entity.GameProfile;
import com.example.teamforge.gameprofile.entity.Position;
import com.example.teamforge.participant.entity.Participant;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.nullValue;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:riot-profile-api-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class RiotProfileApiTests {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean RiotClient riot;
    private UUID participantId;
    private UUID profileId;

    @BeforeEach
    void prepareCommittedProfile() {
        transaction().executeWithoutResult(status -> {
            Participant participant = Participant.register("참가자");
            em.persist(participant);
            participantId = participant.getId();
            GameProfile profile = new GameProfile(participantId, Game.LOL, "player#KR1", 1445, 0, Set.of(Position.LOL_MID));
            profile.refreshRiotRank("old-puuid", "GOLD", "II", 45, 1445, Instant.EPOCH);
            em.persist(profile);
            profileId = profile.getId();
        });
    }

    @AfterEach
    void cleanDedicatedDatabase() {
        transaction().executeWithoutResult(status -> {
            em.createNativeQuery("delete from game_profile_positions").executeUpdate();
            em.createQuery("delete from GameProfile").executeUpdate();
            em.createQuery("delete from Participant").executeUpdate();
        });
    }

    @Test
    void refreshesAndPersistsRankWhileKeepingManualZeroAndPositions() throws Exception {
        // given: HTTP 처리 중 외부 조회는 DB 트랜잭션 밖에서 수행한다.
        when(riot.findSoloRank("player#KR1")).thenAnswer(invocation -> {
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            return new RiotRank("new-puuid", "EMERALD", "I", 80);
        });

        // when / then
        mvc.perform(post(url())).andExpect(status().isOk())
                .andExpect(jsonPath("$.baseScore").value(2380))
                .andExpect(jsonPath("$.manualScore").value(0))
                .andExpect(jsonPath("$.effectiveScore").value(0))
                .andExpect(jsonPath("$.riotRank.tier").value("EMERALD"))
                .andExpect(jsonPath("$.riotRank.division").value("I"))
                .andExpect(jsonPath("$.riotRank.leaguePoints").value(80));
        transaction().executeWithoutResult(status -> {
            GameProfile saved = em.find(GameProfile.class, profileId);
            assertEquals("new-puuid", saved.getRiotPuuid());
            assertEquals(2380, saved.getBaseScore());
            assertEquals(Set.of(Position.LOL_MID), saved.getPreferredPositions());
            assertTrue(saved.getRiotUpdatedAt().isAfter(Instant.EPOCH));
        });
        mvc.perform(get("/api/participants/" + participantId + "/profiles/LOL"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.riotRank.tier").value("EMERALD"));
    }

    @Test
    void successfulUnrankedResponseClearsRankButKeepsManualScore() throws Exception {
        // given
        when(riot.findSoloRank("player#KR1")).thenReturn(new RiotRank("puuid", null, null, null));
        // when / then
        mvc.perform(post(url())).andExpect(status().isOk())
                .andExpect(jsonPath("$.baseScore").value(0))
                .andExpect(jsonPath("$.manualScore").value(0))
                .andExpect(jsonPath("$.riotRank.tier").value(nullValue()))
                .andExpect(jsonPath("$.riotRank.updatedAt").exists());
    }

    @ParameterizedTest
    @EnumSource(value = ErrorCode.class, names = {"RIOT_ACCOUNT_NOT_FOUND", "RIOT_AUTHENTICATION_FAILED",
            "RIOT_RATE_LIMITED", "RIOT_UNAVAILABLE", "RIOT_NOT_CONFIGURED", "INVALID_RIOT_ID"})
    void preservesPreviousScoreAndMetadataOnFailure(ErrorCode error) throws Exception {
        // given
        when(riot.findSoloRank("player#KR1")).thenThrow(new BusinessException(error));
        // when / then
        mvc.perform(post(url())).andExpect(status().is(error.getStatus().value()))
                .andExpect(jsonPath("$.code").value(error.name()));
        transaction().executeWithoutResult(status -> {
            GameProfile saved = em.find(GameProfile.class, profileId);
            assertEquals(1445, saved.getBaseScore());
            assertEquals("old-puuid", saved.getRiotPuuid());
            assertEquals(Instant.EPOCH, saved.getRiotUpdatedAt());
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"account", "manualScore", "delete"})
    void rejectsStaleExternalResponseWhenProfileOrParticipantChanges(String change) throws Exception {
        // given: 별도 스레드·영속성 컨텍스트에서 변경을 커밋한 후 Riot 응답을 반환한다.
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            when(riot.findSoloRank("player#KR1")).thenAnswer(invocation -> {
                worker.submit(() -> transaction().executeWithoutResult(status -> {
                    GameProfile profile = em.find(GameProfile.class, profileId);
                    switch (change) {
                        case "account" -> profile.update("other#KR1", 0, Set.of(Position.LOL_MID));
                        case "manualScore" -> profile.overrideScore(3000);
                        case "delete" -> em.find(Participant.class, participantId).delete(Instant.now());
                        default -> throw new AssertionError(change);
                    }
                })).get(5, TimeUnit.SECONDS);
                return new RiotRank("new-puuid", "EMERALD", "I", 80);
            });
            // when / then: 새 계정·수동 점수·삭제 이후 오래된 응답을 저장하지 않는다.
            mvc.perform(post(url())).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(change.equals("delete")
                            ? "PARTICIPANT_DELETED" : "CONCURRENT_MODIFICATION"));
        } finally {
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        }
        transaction().executeWithoutResult(status ->
                assertEquals(1445, em.find(GameProfile.class, profileId).getBaseScore()));
    }

    @Test
    void validatesParticipantAndDocumentsEndpoint() throws Exception {
        // given: 존재하지 않는 참가자와 잘못된 UUID다.
        // when / then
        mvc.perform(post("/api/participants/" + UUID.randomUUID() + "/profiles/LOL/refresh"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/participants/invalid/profiles/LOL/refresh"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(riot);
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/participants/{participantId}/profiles/LOL/refresh'].post").exists());
    }

    private String url() {
        return "/api/participants/" + participantId + "/profiles/LOL/refresh";
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }
}
