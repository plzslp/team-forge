package com.example.teamforge.match.controller;

import com.example.teamforge.gameprofile.entity.Game;
import com.example.teamforge.match.entity.Match;
import com.example.teamforge.match.entity.MatchParticipant;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:match-result-api-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class MatchResultApiTests {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;

    @ParameterizedTest
    @EnumSource(Game.class)
    void recordsAndReplacesResultWithoutChangingSnapshot(Game game) throws Exception {
        // given: 삭제된 원본 참가자가 있어도 확정 기록의 결과는 입력할 수 있다.
        UUID id = persistMatch(game);
        Match original = em.find(Match.class, id);
        UUID participantId = original.getParticipants().get(0).getParticipantId();
        em.find(Participant.class, participantId).delete(Instant.now());
        em.flush();
        em.clear();

        // when
        ResultActions created = putResult(id, "{\"scoreA\":3,\"scoreB\":1,\"memo\":\"첫 결과\"}");

        // then
        created.andExpect(status().isOk())
                .andExpect(jsonPath("$.result.scoreA").value(3))
                .andExpect(jsonPath("$.result.scoreB").value(1))
                .andExpect(jsonPath("$.result.memo").value("첫 결과"))
                .andExpect(jsonPath("$.result.outcomeA").value("WIN"))
                .andExpect(jsonPath("$.result.outcomeB").value("LOSS"));
        em.clear();
        Long firstVersion = em.find(Match.class, id).getVersion();

        // when: 메모 생략은 기존 메모를 비우며 전체 결과를 교체한다.
        putResult(id, "{\"scoreA\":0,\"scoreB\":0}").andExpect(status().isOk())
                .andExpect(jsonPath("$.result.memo").value(""))
                .andExpect(jsonPath("$.result.outcomeA").value("DRAW"))
                .andExpect(jsonPath("$.result.outcomeB").value("DRAW"));
        em.clear();

        // then: 목록·상세에 저장된 결과가 반영되고 팀 스냅샷은 그대로다.
        Match stored = em.find(Match.class, id);
        assertTrue(stored.getVersion() > firstVersion);
        assertEquals(Instant.EPOCH, stored.getCreatedAt());
        assertEquals("참가자0", stored.getParticipants().stream()
                .filter(member -> member.getParticipantId().equals(participantId)).findFirst().orElseThrow().getName());
        assertEquals(10, stored.getParticipants().size());
        mvc.perform(get("/api/matches/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.scoreA").value(0))
                .andExpect(jsonPath("$.result.scoreB").value(0))
                .andExpect(jsonPath("$.teams[0].totalScore").value(5000))
                .andExpect(jsonPath("$.scoreDifference").value(0));
        mvc.perform(get("/api/matches?game=" + game)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].result.outcomeA").value("DRAW"));

        // when / then: 같은 결과 재전송도 성공한다.
        putResult(id, "{\"scoreA\":0,\"scoreB\":0}").andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"\"", "\"메모\""})
    void acceptsOptionalMemo(String memo) throws Exception {
        // given
        UUID id = persistMatch(Game.LOL);
        // when / then
        putResult(id, "{\"scoreA\":1,\"scoreB\":2,\"memo\":" + memo + "}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.outcomeA").value("LOSS"))
                .andExpect(jsonPath("$.result.outcomeB").value("WIN"));
    }

    @Test
    void acceptsMaximumScoreAndMemoLength() throws Exception {
        // given
        UUID id = persistMatch(Game.LOL);
        String memo = "가".repeat(2000);
        // when / then
        putResult(id, "{\"scoreA\":2147483647,\"scoreB\":0,\"memo\":\"" + memo + "\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.scoreA").value(Integer.MAX_VALUE))
                .andExpect(jsonPath("$.result.memo").value(memo));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "", "{", "{\"scoreA\":0}", "{\"scoreB\":0}",
            "{\"scoreA\":null,\"scoreB\":0}", "{\"scoreA\":0,\"scoreB\":null}",
            "{\"scoreA\":-1,\"scoreB\":0}", "{\"scoreA\":0,\"scoreB\":-1}",
            "{\"scoreA\":2147483648,\"scoreB\":0}", "{\"scoreA\":\"bad\",\"scoreB\":0}"})
    void rejectsInvalidInputWithoutRecordingResult(String body) throws Exception {
        // given
        UUID id = persistMatch(Game.LOL);
        // when / then
        putResult(id, body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        em.clear();
        assertNull(em.find(Match.class, id).getResult());
    }

    @Test
    void rejectsOversizedMemoAndInvalidIds() throws Exception {
        // given
        UUID id = persistMatch(Game.LOL);
        String valid = "{\"scoreA\":1,\"scoreB\":0}";
        // when / then
        putResult(id, "{\"scoreA\":1,\"scoreB\":0,\"memo\":\"" + "가".repeat(2001) + "\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        putResult(UUID.randomUUID(), valid).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mvc.perform(put("/api/matches/invalid/result").contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void documentsResultContract() throws Exception {
        // given: Api 추상 클래스에서 상속한 Swagger 계약이다.
        // when / then
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/matches/{id}/result'].put.summary").value("경기 결과 입력·수정"))
                .andExpect(jsonPath("$.paths['/api/matches/{id}/result'].put.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/matches/{id}/result'].put.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/matches/{id}/result'].put.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/matches/{id}/result'].put.responses['409']").exists())
                .andExpect(jsonPath("$.components.schemas.MatchResultRequest.properties.scoreA.minimum").value(0))
                .andExpect(jsonPath("$.components.schemas.MatchResultRequest.properties.memo.maxLength").value(2000));
    }

    private ResultActions putResult(UUID id, String body) throws Exception {
        return mvc.perform(put("/api/matches/" + id + "/result").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private UUID persistMatch(Game game) {
        List<MatchParticipant> snapshots = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            Participant participant = Participant.register("참가자" + index);
            em.persist(participant);
            snapshots.add(new MatchParticipant(participant.getId(), participant.getName(), 1000,
                    game.teamPositions().get(index % 5), index < 5 ? MatchParticipant.Team.A : MatchParticipant.Team.B));
        }
        UUID id = UUID.randomUUID();
        em.persist(new Match(id, game, Instant.EPOCH, snapshots, null));
        em.flush();
        return id;
    }
}
