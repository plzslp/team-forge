package com.example.teamforge.match.controller;

import com.example.teamforge.gameprofile.entity.Game;
import com.example.teamforge.gameprofile.entity.GameProfile;
import com.example.teamforge.gameprofile.entity.Position;
import com.example.teamforge.match.dto.request.MatchCreateRequest;
import com.example.teamforge.match.dto.response.MatchCreateResponse;
import com.example.teamforge.match.dto.request.MatchParticipantRequest;
import com.example.teamforge.match.dto.ParticipantVersions;
import com.example.teamforge.match.dto.request.TeamPreviewRequest;
import com.example.teamforge.match.dto.response.TeamPreviewResponse;
import com.example.teamforge.match.entity.Match;
import com.example.teamforge.match.entity.MatchParticipant;
import com.example.teamforge.match.service.TeamPreviewService;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:match-create-api-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class MatchCreateApiTests {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired JsonMapper json;
    @Autowired TeamPreviewService previewService;

    @ParameterizedTest
    @EnumSource(Game.class)
    void savesPreviewAsMatchWithIndependentSnapshots(Game game) throws Exception {
        // given: 실제 미리보기 API와 동일한 Service에서 팀과 버전을 받는다.
        MatchCreateRequest request = preview(game, false);
        Instant before = Instant.now();

        // when: 미리보기의 최종 편성을 확정한다.
        String body = perform(request).andExpect(status().isCreated())
                .andExpect(jsonPath("$.teams", hasSize(2)))
                .andExpect(jsonPath("$.teams[0].participants", hasSize(5)))
                .andExpect(jsonPath("$.teams[1].participants", hasSize(5)))
                .andExpect(jsonPath("$.participantVersions").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        MatchCreateResponse response = json.readValue(body, MatchCreateResponse.class);
        em.clear();

        // then: 경기와 참가자 10명의 스냅샷이 함께 저장되고 결과는 아직 없다.
        Match match = em.find(Match.class, response.id());
        assertNotNull(match);
        assertEquals(game, match.getGame());
        assertFalse(match.getCreatedAt().isBefore(before));
        assertFalse(match.getCreatedAt().isAfter(Instant.now()));
        assertEquals(10, match.getParticipants().size());
        assertNull(match.getResult());
        Match.validateRoleComposition(game, match.getParticipants());
        assertEquals(1L, countMatches());
        assertEquals(10L, countSnapshots());
        for (MatchParticipant snapshot : match.getParticipants()) {
            Participant participant = em.find(Participant.class, snapshot.getParticipantId());
            GameProfile profile = profile(snapshot.getParticipantId(), game);
            assertEquals(participant.getName(), snapshot.getName());
            assertEquals(profile.effectiveScore(), snapshot.getScore());
        }
        long scoreA = match.getParticipants().stream().filter(p -> p.getTeam() == MatchParticipant.Team.A)
                .mapToLong(MatchParticipant::getScore).sum();
        long scoreB = match.getParticipants().stream().filter(p -> p.getTeam() == MatchParticipant.Team.B)
                .mapToLong(MatchParticipant::getScore).sum();
        assertEquals(scoreA, response.teams().get(0).totalScore());
        assertEquals(scoreB, response.teams().get(1).totalScore());
        assertEquals(Math.abs(scoreA - scoreB), response.scoreDifference());
    }

    @Test
    void laterChangesDoNotRewriteHistoricalNameScoreOrPosition() throws Exception {
        // given: 미리보기의 스냅샷을 확정한다.
        MatchCreateRequest request = preview(Game.LOL, false);
        MatchCreateResponse response = json.readValue(perform(request).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), MatchCreateResponse.class);
        em.clear();
        UUID id = request.participants().get(0).participantId();
        MatchParticipant original = em.find(Match.class, response.id()).getParticipants().stream()
                .filter(p -> p.getParticipantId().equals(id)).findFirst().orElseThrow();
        String originalName = original.getName();
        int originalScore = original.getScore();
        Position originalPosition = original.getAssignedPosition();

        // when: 참가자의 이름·점수·선호 포지션을 변경하고 논리 삭제한다.
        Participant participant = em.find(Participant.class, id);
        participant.rename("변경된 이름");
        GameProfile profile = profile(id, Game.LOL);
        profile.overrideScore(9999);
        profile.changePreferredPositions(Set.of(Position.LOL_TOP));
        participant.delete(Instant.now());
        em.flush();
        em.clear();

        // then: 확정 기록은 원래 값을 보존한다.
        MatchParticipant saved = em.find(Match.class, response.id()).getParticipants().stream()
                .filter(p -> p.getParticipantId().equals(id)).findFirst().orElseThrow();
        assertEquals(originalName, saved.getName());
        assertEquals(originalScore, saved.getScore());
        assertEquals(originalPosition, saved.getAssignedPosition());
    }

    @Test
    void acceptsManualRoleSwapWithoutRebalancingTeams() throws Exception {
        // given: 같은 팀 두 명의 역할을 서로 바꾼다. 모두 해당 역할을 선호한다.
        MatchCreateRequest request = preview(Game.LOL, false);
        List<MatchParticipantRequest> members = new ArrayList<>(request.participants());
        MatchParticipantRequest first = members.get(0);
        MatchParticipantRequest second = members.get(1);
        members.set(0, new MatchParticipantRequest(first.participantId(), first.team(), second.assignedPosition()));
        members.set(1, new MatchParticipantRequest(second.participantId(), second.team(), first.assignedPosition()));

        // when: 수정한 편성을 확정한다.
        MatchCreateRequest swapped = copy(request, members, request.participantVersions(), false);
        MatchCreateResponse response = json.readValue(perform(swapped).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), MatchCreateResponse.class);
        em.clear();

        // then: 계산을 다시 수행하지 않고 요청한 최종 역할을 저장한다.
        MatchParticipant saved = em.find(Match.class, response.id()).getParticipants().stream()
                .filter(p -> p.getParticipantId().equals(first.participantId())).findFirst().orElseThrow();
        assertEquals(second.assignedPosition(), saved.getAssignedPosition());
        assertEquals(first.team(), saved.getTeam());
    }

    @ParameterizedTest
    @EnumSource(Game.class)
    void acceptsManualPlayerSwapBetweenTeams(Game game) throws Exception {
        // given: 두 팀에서 같은 포지션을 맡은 참가자끼리 팀을 바꾼다.
        MatchCreateRequest request = preview(game, false);
        List<MatchParticipantRequest> members = new ArrayList<>(request.participants());
        MatchParticipantRequest firstA = members.get(0);
        MatchParticipantRequest firstB = members.get(5);
        members.set(0, new MatchParticipantRequest(firstA.participantId(), MatchParticipant.Team.B, firstA.assignedPosition()));
        members.set(5, new MatchParticipantRequest(firstB.participantId(), MatchParticipant.Team.A, firstB.assignedPosition()));

        // when: 자동 편성의 점수 최적 결과와 달라도 유효한 수동 교체를 확정한다.
        MatchCreateResponse response = json.readValue(perform(copy(request, members, request.participantVersions(), false))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), MatchCreateResponse.class);

        // then: 다시 밸런싱하지 않고 지정한 팀을 유지한다.
        assertTrue(response.teams().get(1).participants().stream().anyMatch(member -> member.participantId().equals(firstA.participantId())));
        assertTrue(response.teams().get(0).participants().stream().anyMatch(member -> member.participantId().equals(firstB.participantId())));
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "manual-score", "base-score", "positions", "account", "deleted", "missing", "missing-profile"})
    void rejectsChangedOrUnavailablePreviewWithoutSaving(String change) throws Exception {
        // given: 미리보기 이후 참가자 또는 프로필을 변경한다.
        MatchCreateRequest request = preview(Game.LOL, false);
        UUID id = request.participants().get(0).participantId();
        Participant participant = em.find(Participant.class, id);
        GameProfile profile = profile(id, Game.LOL);
        switch (change) {
            case "name" -> participant.rename("새 이름");
            case "manual-score" -> profile.overrideScore(50);
            case "base-score" -> profile.refreshScore(5000);
            case "positions" -> profile.changePreferredPositions(Set.of(Position.LOL_SUPPORT));
            case "account" -> profile.update("new-account", profile.getManualScore(), profile.getPreferredPositions());
            case "deleted" -> participant.delete(Instant.now());
            case "missing-profile" -> em.remove(profile);
            case "missing" -> {
                em.remove(profile);
                em.flush();
                em.remove(participant);
            }
            default -> throw new IllegalArgumentException(change);
        }
        em.flush();
        em.clear();

        // when / then: 오래된 편성은 재생성을 안내하고 일부 기록도 남기지 않는다.
        perform(request).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PREVIEW_OUTDATED"));
        assertEquals(0L, countMatches());
        assertEquals(0L, countSnapshots());
    }

    @ParameterizedTest
    @EnumSource(Game.class)
    void enforcesNonPreferredConsentAgainAtConfirmation(Game game) throws Exception {
        // given: 미리보기에서는 비선호를 허용했지만 확정 요청에서는 허용하지 않는다.
        MatchCreateRequest request = preview(game, true);
        String omitted = json.writeValueAsString(copy(request, request.participants(), request.participantVersions(), false))
                .replace(",\"allowNonPreferredPositions\":false", "");

        // when / then: 확정 단계에서 허용 여부를 새로 확인한다.
        mvc.perform(post("/api/matches").contentType(MediaType.APPLICATION_JSON).content(omitted))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NON_PREFERRED_POSITION_NOT_ALLOWED"));
        assertEquals(0L, countMatches());

        // when: 비선호 배정을 명시적으로 허용한다.
        ResultActions result = perform(request);

        // then: 같은 팀·역할 구성을 저장한다.
        result.andExpect(status().isCreated());
        assertEquals(1L, countMatches());
    }

    @ParameterizedTest
    @EnumSource(Game.class)
    void rejectsWrongTeamRoleCountsEvenWhenNonPreferredAllowed(Game game) throws Exception {
        // given: 역할 하나를 중복 배정해 롤의 각 1명 또는 오버워치의 1·2·2 구성을 깨뜨린다.
        MatchCreateRequest request = preview(game, false);
        List<MatchParticipantRequest> members = new ArrayList<>(request.participants());
        MatchParticipantRequest first = members.get(0);
        members.set(0, new MatchParticipantRequest(first.participantId(), first.team(), members.get(1).assignedPosition()));

        // when / then: 비선호 허용은 역할 인원 제한을 해제하지 않는다.
        perform(copy(request, members, request.participantVersions(), true))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_TEAM_POSITIONS"));
        assertEquals(0L, countMatches());
    }

    @ParameterizedTest
    @ValueSource(strings = {"duplicate", "six-on-a", "wrong-game", "wrong-version-keys", "participant-version", "profile-version"})
    void rejectsInvalidFinalCompositionOrVersions(String reason) throws Exception {
        // given: 인원 수는 10명인 채로 한 가지 계약을 위반한다.
        MatchCreateRequest request = preview(Game.LOL, false);
        List<MatchParticipantRequest> members = new ArrayList<>(request.participants());
        Map<UUID, ParticipantVersions> versions = new HashMap<>(request.participantVersions());
        MatchParticipantRequest first = members.get(0);
        String code;
        int status;
        switch (reason) {
            case "duplicate" -> {
                members.set(9, first);
                code = "INVALID_TEAM_COMPOSITION";
                status = 400;
            }
            case "six-on-a" -> {
                MatchParticipantRequest last = members.get(9);
                members.set(9, new MatchParticipantRequest(last.participantId(), MatchParticipant.Team.A, last.assignedPosition()));
                code = "INVALID_TEAM_COMPOSITION";
                status = 400;
            }
            case "wrong-game" -> {
                members.set(0, new MatchParticipantRequest(first.participantId(), first.team(), Position.OVERWATCH_TANK));
                code = "POSITION_GAME_MISMATCH";
                status = 400;
            }
            case "wrong-version-keys" -> {
                versions.remove(first.participantId());
                versions.put(UUID.randomUUID(), new ParticipantVersions(0, 0));
                code = "PREVIEW_PARTICIPANTS_MISMATCH";
                status = 400;
            }
            case "participant-version", "profile-version" -> {
                versions.put(first.participantId(), new ParticipantVersions(reason.equals("participant-version") ? 99 : 0,
                        reason.equals("profile-version") ? 99 : 0));
                code = "PREVIEW_OUTDATED";
                status = 409;
            }
            default -> throw new IllegalArgumentException(reason);
        }

        // when / then: 잘못된 편성은 저장하지 않는다.
        perform(copy(request, members, versions, false)).andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code));
        assertEquals(0L, countMatches());
        assertEquals(0L, countSnapshots());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{", "{\"game\":\"LOL\",\"participants\":[],\"participantVersions\":{}}"})
    void rejectsMalformedBody(String body) throws Exception {
        // given: JSON 또는 필수 입력이 올바르지 않은 요청이다.
        // when / then
        mvc.perform(post("/api/matches").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null-member", "null-id", "null-team", "null-position", "nine", "eleven", "null-versions",
            "null-version", "nine-versions", "negative-version", "missing-version-field", "invalid-game", "invalid-team", "invalid-uuid"})
    void validatesNestedRequestFields(String reason) throws Exception {
        // given: 정상 요청 JSON의 한 필드만 잘못된 값으로 바꾼다.
        MatchCreateRequest request = preview(Game.LOL, false);
        List<MatchParticipantRequest> members = new ArrayList<>(request.participants());
        Map<UUID, ParticipantVersions> versions = new HashMap<>(request.participantVersions());
        MatchParticipantRequest first = members.get(0);
        switch (reason) {
            case "null-member" -> members.set(0, null);
            case "null-id" -> members.set(0, new MatchParticipantRequest(null, first.team(), first.assignedPosition()));
            case "null-team" -> members.set(0, new MatchParticipantRequest(first.participantId(), null, first.assignedPosition()));
            case "null-position" -> members.set(0, new MatchParticipantRequest(first.participantId(), first.team(), null));
            case "nine" -> members.remove(9);
            case "eleven" -> members.add(first);
            case "null-version" -> versions.put(first.participantId(), null);
            case "nine-versions" -> versions.remove(first.participantId());
            default -> { }
        }
        String body = json.writeValueAsString(copy(request, members, reason.equals("null-versions") ? null : versions, false));
        body = switch (reason) {
            case "negative-version" -> body.replace("\"profileVersion\":0", "\"profileVersion\":-1");
            case "missing-version-field" -> body.replace("\"participantVersion\":0,", "");
            case "invalid-game" -> body.replace("\"game\":\"LOL\"", "\"game\":\"UNKNOWN\"");
            case "invalid-team" -> body.replace("\"team\":\"A\"", "\"team\":\"UNKNOWN\"");
            case "invalid-uuid" -> body.replace(first.participantId().toString(), "not-a-uuid");
            default -> body;
        };

        // when / then: 중첩 DTO와 버전 필드 오류를 400으로 거절한다.
        mvc.perform(post("/api/matches").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        assertEquals(0L, countMatches());
    }

    @Test
    void ignoresClientNameScoreAndTimestampAndSupportsLargeTotals() throws Exception {
        // given: 모든 프로필 점수는 최댓값이며 첫 참가자의 수동 점수는 0이다.
        MatchCreateRequest initial = preview(Game.LOL, false);
        List<UUID> ids = initial.participants().stream().map(MatchParticipantRequest::participantId).toList();
        for (UUID id : ids) profile(id, Game.LOL).refreshScore(Integer.MAX_VALUE).overrideScore(null);
        profile(ids.get(0), Game.LOL).overrideScore(0);
        em.flush();
        em.clear();
        MatchCreateRequest request = fromPreview(previewService.create(new TeamPreviewRequest(Game.LOL, ids, false)), false);
        String body = json.writeValueAsString(request).replace("\"assignedPosition\":", "\"name\":\"위조 이름\",\"score\":1,\"assignedPosition\":")
                .replace("\"game\":", "\"createdAt\":\"2000-01-01T00:00:00Z\",\"game\":");

        // when: 서버가 받지 않는 이름·점수·시각을 추가해 보낸다.
        String responseBody = mvc.perform(post("/api/matches").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        // then: 저장 값은 현재 DB와 서버 시각이며 합계는 int 범위를 넘겨도 정확하다.
        MatchCreateResponse response = json.readValue(responseBody, MatchCreateResponse.class);
        assertTrue(response.createdAt().isAfter(Instant.parse("2000-01-01T00:00:00Z")));
        assertEquals((long) Integer.MAX_VALUE, response.scoreDifference());
        assertTrue(response.teams().stream().allMatch(team -> team.totalScore() > Integer.MAX_VALUE));
        assertTrue(response.teams().stream().flatMap(team -> team.participants().stream())
                .noneMatch(member -> member.name().equals("위조 이름") || member.score() == 1));
        assertEquals(1, response.teams().stream().flatMap(team -> team.participants().stream()).filter(member -> member.score() == 0).count());
    }

    @Test
    void documentsContractOnApiClass() throws Exception {
        // given: Api 클래스의 Swagger 계약이 등록된 OpenAPI 문서이다.
        // when
        ResultActions result = mvc.perform(get("/v3/api-docs"));
        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/matches'].post.summary").value("팀 편성 확정·저장"))
                .andExpect(jsonPath("$.paths['/api/matches'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/matches'].post.responses['409']").exists())
                .andExpect(jsonPath("$.components.schemas.MatchCreateRequest.required", containsInAnyOrder("game", "participants", "participantVersions")))
                .andExpect(jsonPath("$.components.schemas.MatchParticipantRequest.properties.score").doesNotExist());
    }

    private MatchCreateRequest preview(Game game, boolean singleRole) {
        List<UUID> ids = new ArrayList<>();
        Set<Position> positions = singleRole ? Set.of(game.teamPositions().get(0)) : Set.copyOf(game.teamPositions());
        for (int index = 0; index < 10; index++) {
            Participant participant = Participant.register("참가자" + index);
            em.persist(participant);
            em.persist(new GameProfile(participant.getId(), game, null, 1200 + index, null, positions));
            ids.add(participant.getId());
        }
        em.flush();
        em.clear();
        return fromPreview(previewService.create(new TeamPreviewRequest(game, ids, singleRole)), singleRole);
    }

    private MatchCreateRequest fromPreview(TeamPreviewResponse preview, boolean allow) {
        List<MatchParticipantRequest> members = preview.teams().stream().flatMap(team -> team.participants().stream()
                .map(member -> new MatchParticipantRequest(member.participantId(), team.team(), member.assignedPosition()))).toList();
        return new MatchCreateRequest(preview.game(), members, preview.participantVersions(), allow);
    }

    private MatchCreateRequest copy(MatchCreateRequest request, List<MatchParticipantRequest> members,
                                    Map<UUID, ParticipantVersions> versions, boolean allow) {
        return new MatchCreateRequest(request.game(), members, versions, allow);
    }

    private ResultActions perform(MatchCreateRequest request) throws Exception {
        return mvc.perform(post("/api/matches").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)));
    }

    private GameProfile profile(UUID id, Game game) {
        return em.createQuery("select g from GameProfile g where g.participantId = :id and g.game = :game", GameProfile.class)
                .setParameter("id", id).setParameter("game", game).getSingleResult();
    }

    private long countMatches() {
        return em.createQuery("select count(m) from Match m", Long.class).getSingleResult();
    }

    private long countSnapshots() {
        return em.createQuery("select count(p) from MatchParticipant p", Long.class).getSingleResult();
    }
}
