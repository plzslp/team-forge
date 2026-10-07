package com.example.teamforge.gameprofile.controller;

import com.example.teamforge.gameprofile.entity.Game;
import com.example.teamforge.gameprofile.entity.GameProfile;
import com.example.teamforge.participant.entity.Participant;
import com.example.teamforge.gameprofile.entity.Position;
import com.example.teamforge.gameprofile.repository.GameProfileRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:game-profile-api-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class GameProfileApiTests {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired GameProfileRepository repository;

    @ParameterizedTest
    @ValueSource(strings = {"LOL", "OVERWATCH"})
    void createsProfileAndReturnsRetrievableLocation(String game) throws Exception {
        // given: 활성 참여자와 게임별 선호 포지션을 준비한다.
        Participant participant = saveParticipant();
        String position = game.equals("LOL") ? "LOL_TOP" : "OVERWATCH_TANK";
        String path = path(participant.getId(), game);

        // when: 수동 점수 0으로 신규 등록한다.
        MockHttpServletResponse response = mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"  account#1234  \",\"manualScore\":0,\"preferredPositions\":[\"" + position + "\"]}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", path))
                .andExpect(jsonPath("$.game").value(game))
                .andExpect(jsonPath("$.accountId").value("account#1234"))
                .andExpect(jsonPath("$.manualScore").value(0))
                .andExpect(jsonPath("$.effectiveScore").value(0))
                .andReturn().getResponse();
        em.flush();
        em.clear();

        // then: 실제 저장된 프로필을 Location으로 재조회할 수 있다.
        assertEquals(1, repository.count());
        mvc.perform(get(response.getHeader("Location")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantId").value(participant.getId().toString()))
                .andExpect(jsonPath("$.baseScore").value(0))
                .andExpect(jsonPath("$.preferredPositions", containsInAnyOrder(position)))
                .andExpect(jsonPath("$.version").doesNotExist());
    }

    @Test
    void replacementPreservesBaseScoreAndSupportsOverrideReset() throws Exception {
        // given: 환산 점수와 수동 점수를 가진 기존 프로필을 준비한다.
        Participant participant = saveParticipant();
        GameProfile profile = new GameProfile(participant.getId(), Game.LOL, "old", 1200, 1600,
                Set.of(Position.LOL_TOP));
        em.persist(profile);
        em.flush();
        long version = profile.getVersion();
        em.clear();
        String path = path(participant.getId(), "LOL");

        // when: 수동 점수와 계정을 생략하고 선호 포지션을 교체한다.
        mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferredPositions\":[\"LOL_MID\",\"LOL_SUPPORT\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(profile.getId().toString()))
                .andExpect(jsonPath("$.baseScore").value(1200))
                .andExpect(jsonPath("$.effectiveScore").value(1200));
        em.flush();
        em.clear();

        // then: 보정과 계정이 해제되고 환산 점수·프로필 식별자는 유지된다.
        GameProfile updated = repository.findById(profile.getId()).orElseThrow();
        assertNull(updated.getAccountId());
        assertNull(updated.getManualScore());
        assertEquals(Set.of(Position.LOL_MID, Position.LOL_SUPPORT), updated.getPreferredPositions());
        assertTrue(updated.getVersion() > version);
        assertEquals(1, repository.count());

        // when / then: 반복 PUT도 새 프로필을 만들지 않으며 0점 보정이 환산 점수보다 우선한다.
        String body = "{\"accountId\":\"   \",\"manualScore\":0,\"preferredPositions\":[\"LOL_MID\"]}";
        for (int i = 0; i < 2; i++) {
            mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.effectiveScore").value(0));
            em.flush();
            em.clear();
        }
        assertEquals(1, repository.count());
        assertNull(repository.findById(profile.getId()).orElseThrow().getAccountId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "{broken",
            "{\"preferredPositions\":null}", "{\"preferredPositions\":[]}",
            "{\"preferredPositions\":[null]}", "{\"preferredPositions\":[\"UNKNOWN\"]}",
            "{\"manualScore\":-1,\"preferredPositions\":[\"LOL_TOP\"]}"})
    void invalidInputReturns400WithoutSaving(String body) throws Exception {
        // given: 활성 참여자와 잘못된 입력을 준비한다.
        Participant participant = saveParticipant();
        // when / then: 입력을 거절하고 프로필을 만들지 않는다.
        mvc.perform(put(path(participant.getId(), "LOL")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertEquals(0, repository.count());
    }

    @Test
    void oversizedAccountReturns400() throws Exception {
        // given: DB 열 크기를 넘는 계정을 준비한다.
        Participant participant = saveParticipant();
        // when / then: DB 저장 전에 검증 오류를 반환한다.
        mvc.perform(put(path(participant.getId(), "LOL")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"" + "a".repeat(256) + "\",\"preferredPositions\":[\"LOL_TOP\"]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertEquals(0, repository.count());
    }

    @Test
    void positionsFromAnotherGameAreRejectedWithoutChangingProfile() throws Exception {
        // given: 기존 LOL 프로필을 준비한다.
        Participant participant = saveParticipant();
        GameProfile profile = new GameProfile(participant.getId(), Game.LOL, "old", 1000, 1500,
                Set.of(Position.LOL_TOP));
        em.persist(profile);
        em.flush();
        em.clear();
        // when: 다른 게임의 포지션을 전달한다.
        mvc.perform(put(path(participant.getId(), "LOL")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"new\",\"manualScore\":0,\"preferredPositions\":[\"OVERWATCH_TANK\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PREFERRED_POSITION"));
        em.clear();
        // then: 기존 데이터가 유지된다.
        GameProfile unchanged = repository.findById(profile.getId()).orElseThrow();
        assertEquals("old", unchanged.getAccountId());
        assertEquals(1500, unchanged.effectiveScore());
        assertEquals(Set.of(Position.LOL_TOP), unchanged.getPreferredPositions());
    }

    @Test
    void missingOrDeletedParticipantAndMissingProfileAreHandled() throws Exception {
        // given: 활성 참여자와 프로필이 있는 삭제된 참여자를 준비한다.
        Participant active = saveParticipant();
        Participant deleted = Participant.register("삭제됨").delete(Instant.EPOCH);
        em.persist(deleted);
        GameProfile retained = new GameProfile(deleted.getId(), Game.LOL, null, 1000, null, Set.of(Position.LOL_TOP));
        em.persist(retained);
        em.flush();
        em.clear();
        String body = "{\"preferredPositions\":[\"LOL_TOP\"]}";
        // when / then: 없는 리소스는 404, 삭제된 참여자의 변경은 409를 반환한다.
        for (UUID id : new UUID[]{UUID.randomUUID(), active.getId(), deleted.getId()}) {
            mvc.perform(get(path(id, "LOL"))).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        }
        mvc.perform(put(path(UUID.randomUUID(), "LOL")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
        mvc.perform(put(path(deleted.getId(), "LOL")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PARTICIPANT_DELETED"));
        assertTrue(repository.findById(retained.getId()).isPresent());
        // then: 다른 게임에 신규 프로필을 만들 수도 없다.
        mvc.perform(put(path(deleted.getId(), "OVERWATCH")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferredPositions\":[\"OVERWATCH_TANK\"]}"))
                .andExpect(status().isConflict());
        assertEquals(1, repository.count());
    }

    @Test
    void malformedPathParametersReturn400() throws Exception {
        // given: 잘못된 UUID와 게임 식별자를 준비한다.
        // when / then: GET과 PUT 모두 공통 입력 오류를 반환한다.
        for (String path : new String[]{path(UUID.randomUUID(), "OW"), "/api/participants/invalid/profiles/LOL"}) {
            mvc.perform(get(path)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
            mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"preferredPositions\":[\"LOL_TOP\"]}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
    }

    @Test
    void openApiIncludesProfileContract() throws Exception {
        // given: 문서용 Api를 상속한 Controller가 등록되어 있다.
        // when / then: 상위 클래스의 문서와 실제 요청·응답 계약을 노출한다.
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/participants/{participantId}/profiles/{game}'].put.summary")
                        .value("게임 프로필 등록·전체 수정"))
                .andExpect(jsonPath("$.paths['/api/participants/{participantId}/profiles/{game}'].get.summary")
                        .value("게임 프로필 조회"))
                .andExpect(jsonPath("$.paths['/api/participants/{participantId}/profiles/{game}'].put.requestBody.required").value(true))
                .andExpect(jsonPath("$.paths['/api/participants/{participantId}/profiles/{game}'].put.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/participants/{participantId}/profiles/{game}'].put.responses['409'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ErrorResponse"));
    }

    private Participant saveParticipant() {
        Participant participant = Participant.register("참여자");
        em.persist(participant);
        em.flush();
        return participant;
    }

    private String path(UUID participantId, String game) {
        return "/api/participants/" + participantId + "/profiles/" + game;
    }
}
