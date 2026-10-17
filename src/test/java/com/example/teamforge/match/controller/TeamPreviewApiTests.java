package com.example.teamforge.match.controller;

import com.example.teamforge.gameprofile.entity.Game;
import com.example.teamforge.gameprofile.entity.GameProfile;
import com.example.teamforge.gameprofile.entity.Position;
import com.example.teamforge.participant.entity.Participant;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:team-preview-api-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class TeamPreviewApiTests {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;

    @ParameterizedTest
    @EnumSource(Game.class)
    void returnsTwoTeamsScoresAndVersionsWithoutSavingMatch(Game game) throws Exception {
        // given: 최대 점수로 long 합계와 DB 입력 버전도 검증한다.
        List<UUID> ids = saveCandidates(game, false);
        // when
        mvc.perform(post("/api/team-previews").contentType(MediaType.APPLICATION_JSON).content(request(game, ids, false)))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.game").value(game.name()))
                .andExpect(jsonPath("$.teams", hasSize(2)))
                .andExpect(jsonPath("$.teams[0].team").value("A"))
                .andExpect(jsonPath("$.teams[1].team").value("B"))
                .andExpect(jsonPath("$.teams[0].participants", hasSize(5)))
                .andExpect(jsonPath("$.teams[1].participants", hasSize(5)))
                .andExpect(jsonPath("$.teams[0].totalScore").value(5L * Integer.MAX_VALUE))
                .andExpect(jsonPath("$.scoreDifference").value(0))
                .andExpect(jsonPath("$.nonPreferredAssignmentCount").value(0))
                .andExpect(jsonPath("$.warnings", empty()))
                .andExpect(jsonPath("$.teams[*].participants[*].preferredPosition", everyItem(is(true))))
                .andExpect(jsonPath("$.teams[*].participants[*].id").doesNotExist())
                .andExpect(jsonPath("$.participantVersions['" + ids.get(0) + "'].participantVersion").value(0))
                .andExpect(jsonPath("$.participantVersions['" + ids.get(0) + "'].profileVersion").value(0));
        assertEquals(0L, em.createQuery("select count(m) from Match m", Long.class).getSingleResult());
        assertEquals(0L, em.createQuery("select count(p) from MatchParticipant p", Long.class).getSingleResult());
    }

    @Test
    void usesCurrentManualScoreAndBothEntityVersions() throws Exception {
        // given: 이름 변경과 수동 점수 0 지정으로 양쪽 버전이 올라간다.
        List<UUID> ids = saveCandidates(Game.LOL, false);
        Participant participant = em.find(Participant.class, ids.get(0));
        participant.rename("변경된 이름");
        GameProfile profile = em.createQuery("select g from GameProfile g where g.participantId = :id", GameProfile.class)
                .setParameter("id", ids.get(0)).getSingleResult();
        profile.overrideScore(0);
        em.flush();
        long participantVersion = participant.getVersion();
        long profileVersion = profile.getVersion();
        em.clear();
        // when
        mvc.perform(post("/api/team-previews").contentType(MediaType.APPLICATION_JSON)
                        .content(request(Game.LOL, ids, false)))
                // then: 수동 0점이 환산 점수보다 우선한다.
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teams[*].participants[?(@.name == '변경된 이름')].score", contains(0)))
                .andExpect(jsonPath("$.scoreDifference").value((long) Integer.MAX_VALUE))
                .andExpect(jsonPath("$.participantVersions['" + ids.get(0) + "'].participantVersion").value(participantVersion))
                .andExpect(jsonPath("$.participantVersions['" + ids.get(0) + "'].profileVersion").value(profileVersion));
    }

    @Test
    void rejectsImpossibleRolesByDefaultAndReturnsWarningsWhenAllowed() throws Exception {
        // given: 모두 탑만 선호하여 선호 포지션만으로 편성할 수 없는 참가자를 준비한다.
        List<UUID> ids = saveCandidates(Game.LOL, true);
        // when / then: 비선호 허용 옵션을 생략하면 편성을 거절한다.
        mvc.perform(post("/api/team-previews").contentType(MediaType.APPLICATION_JSON)
                        .content(request(Game.LOL, ids, false).replace(",\"allowNonPreferredPositions\":false", "")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PREFERRED_POSITION_UNAVAILABLE"));
        // when / then: 비선호 허용 옵션이 null이어도 편성을 거절한다.
        mvc.perform(post("/api/team-previews").contentType(MediaType.APPLICATION_JSON)
                        .content(request(Game.LOL, ids, false).replace("\"allowNonPreferredPositions\":false", "\"allowNonPreferredPositions\":null")))
                .andExpect(status().isConflict());
        // when: 비선호 배정을 명시적으로 허용한다.
        mvc.perform(post("/api/team-previews").contentType(MediaType.APPLICATION_JSON)
                        .content(request(Game.LOL, ids, true)))
                // then: 최소 비선호 배정 인원과 경고를 반환한다.
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nonPreferredAssignmentCount").value(8))
                .andExpect(jsonPath("$.warnings", hasSize(1)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "deleted", "no-profile", "other-game"})
    void rejectsUnavailableParticipantsOrProfiles(String reason) throws Exception {
        // given: 조회 불가 사유에 맞춰 참가자 또는 해당 게임 프로필을 변경한다.
        List<UUID> ids = saveCandidates(Game.LOL, false);
        UUID id = ids.get(0);
        switch (reason) {
            case "missing" -> ids.set(0, UUID.randomUUID());
            case "deleted" -> em.find(Participant.class, id).delete(Instant.now());
            case "no-profile", "other-game" -> {
                GameProfile profile = em.createQuery("select g from GameProfile g where g.participantId = :id", GameProfile.class)
                        .setParameter("id", id).getSingleResult();
                em.remove(profile);
                if (reason.equals("other-game")) {
                    em.persist(new GameProfile(id, Game.OVERWATCH, null, 0, null, Set.of(Position.OVERWATCH_TANK)));
                }
            }
            default -> throw new IllegalArgumentException(reason);
        }
        em.flush();
        em.clear();
        // when / then: 존재하지 않거나 삭제된 참가자, 해당 게임 프로필 누락을 거절한다.
        mvc.perform(post("/api/team-previews").contentType(MediaType.APPLICATION_JSON).content(request(Game.LOL, ids, false)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void rejectsDuplicateIds() throws Exception {
        // given: 같은 참가자 ID가 두 번 포함된 요청을 준비한다.
        List<UUID> ids = saveCandidates(Game.LOL, false);
        ids.set(9, ids.get(0));
        // when / then: 중복된 ID로 편성을 요청하면 입력 오류를 반환한다.
        mvc.perform(post("/api/team-previews").contentType(MediaType.APPLICATION_JSON).content(request(Game.LOL, ids, false)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_TEAM_COMPOSITION"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"game\":\"LOL\",\"participantIds\":null}",
            "{\"game\":\"LOL\",\"participantIds\":[]}", "{\"game\":\"UNKNOWN\",\"participantIds\":[]}",
            "{\"game\":\"LOL\",\"participantIds\":[\"invalid-uuid\"]}"})
    void rejectsMalformedRequests(String body) throws Exception {
        // given: 매개변수로 필수값 누락·잘못된 게임·UUID 등의 JSON 요청을 전달받는다.
        // when / then: 잘못된 요청은 공통 입력 오류로 거절한다.
        mvc.perform(post("/api/team-previews").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void rejectsNullIdAndWrongParticipantCounts() throws Exception {
        // given: 참가자 10명의 정상 요청을 기준으로 입력 오류를 만들 준비를 한다.
        List<UUID> ids = saveCandidates(Game.LOL, false);
        String valid = request(Game.LOL, ids, false);
        // when / then: 참가자 ID 중 null이 있으면 거절한다.
        mvc.perform(post("/api/team-previews").contentType(MediaType.APPLICATION_JSON)
                        .content(valid.replace("\"" + ids.get(0) + "\"", "null")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        // when / then: 참가자가 9명이면 거절한다.
        mvc.perform(post("/api/team-previews").contentType(MediaType.APPLICATION_JSON)
                        .content(request(Game.LOL, ids.subList(0, 9), false)))
                .andExpect(status().isBadRequest());
        // given: 참가자 수가 11명인 요청을 준비한다.
        ids.add(UUID.randomUUID());
        // when / then: 참가자가 11명이면 거절한다.
        mvc.perform(post("/api/team-previews").contentType(MediaType.APPLICATION_JSON).content(request(Game.LOL, ids, false)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exposesSwaggerContractFromApiClass() throws Exception {
        // given: TeamPreviewApi에 선언한 계약이 포함되는 OpenAPI 문서를 확인한다.
        // when
        mvc.perform(get("/v3/api-docs"))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/team-previews'].post.summary").value("팀 편성 미리보기"))
                .andExpect(jsonPath("$.paths['/api/team-previews'].post.responses['409']").exists())
                .andExpect(jsonPath("$.components.schemas.TeamPreviewRequest.required", containsInAnyOrder("game", "participantIds")))
                .andExpect(jsonPath("$.components.schemas.TeamPreviewResponse.properties.participantVersions").exists());
    }

    private List<UUID> saveCandidates(Game game, boolean singleRole) {
        Set<Position> positions = singleRole ? Set.of(Position.LOL_TOP) : Arrays.stream(Position.values())
                .filter(position -> position.supports(game)).collect(Collectors.toSet());
        List<UUID> ids = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            Participant participant = Participant.register("참가자" + index);
            em.persist(participant);
            em.persist(new GameProfile(participant.getId(), game, null, Integer.MAX_VALUE, null, positions));
            ids.add(participant.getId());
        }
        em.flush();
        em.clear();
        return ids;
    }

    private String request(Game game, List<UUID> ids, boolean allow) {
        String values = ids.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(","));
        return "{\"game\":\"" + game + "\",\"participantIds\":[" + values + "],\"allowNonPreferredPositions\":" + allow + "}";
    }
}
