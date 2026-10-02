package com.example.teamforge.participant.controller;

import com.example.teamforge.participant.entity.Participant;
import com.example.teamforge.participant.entity.Game;
import com.example.teamforge.participant.entity.GameProfile;
import com.example.teamforge.participant.entity.Position;
import com.example.teamforge.match.entity.Match;
import com.example.teamforge.match.entity.MatchParticipant;
import com.example.teamforge.participant.repository.ParticipantRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import java.util.Set;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:participant-api-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class ParticipantApiTests {

    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired ParticipantRepository repository;

    @Test
    void registrationPersistsTrimmedNameAndReturnsRetrievableLocation() throws Exception {
        // given: 앞뒤 공백이 있는 이름을 준비한다.
        String body = "{\"name\":\"  참여자  \"}";

        // when: 등록 API를 호출하고 DB에 반영한 뒤 영속성 컨텍스트를 비운다.
        var response = mvc.perform(post("/api/participants")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("참여자"))
                .andExpect(jsonPath("$.id").isString())
                .andReturn().getResponse();
        em.flush();
        em.clear();

        // then: 실제 저장된 참여자와 Location으로 재조회한 응답이 일치한다.
        String location = response.getHeader("Location");
        assertNotNull(location);
        UUID id = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
        assertEquals("참여자", repository.findById(id).orElseThrow().getName());
        mvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("참여자"))
                .andExpect(jsonPath("$.deletedAt").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist());
    }

    @Test
    void registrationAcceptsTwentyCharactersAfterTrimming() throws Exception {
        // given: 공백을 제거하면 정확히 20자인 이름을 준비한다.
        String name = "가".repeat(20);

        // when / then: 등록에 성공하고 정규화된 이름을 반환한다.
        mvc.perform(post("/api/participants").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  " + name + "  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"name\":null}", "{\"name\":\"\"}", "{\"name\":\"   \"}",
            "{\"name\":\"123456789012345678901\"}", "null", "{broken"})
    void invalidRegistrationReturns400WithoutSaving(String body) throws Exception {
        // given: 잘못된 입력과 등록 전 참여자 수를 준비한다.
        long count = repository.count();

        // when: 등록 API에 잘못된 입력을 전달한다.
        var result = mvc.perform(post("/api/participants")
                .contentType(MediaType.APPLICATION_JSON).content(body));

        // then: 공통 오류를 반환하고 참여자를 저장하지 않는다.
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertEquals(count, repository.count());
    }

    @Test
    void listReturnsOnlyActiveParticipantsInNameOrder() throws Exception {
        // given: 이름 순서와 다른 순서로 활성 참여자와 삭제된 참여자를 저장한다.
        em.persist(Participant.register("Zed"));
        em.persist(Participant.register("Hidden").delete(Instant.EPOCH));
        em.persist(Participant.register("Amy"));
        em.flush();
        em.clear();

        // when / then: 삭제된 참여자를 제외하고 이름순으로 조회한다.
        mvc.perform(get("/api/participants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].name").value("Amy"))
                .andExpect(jsonPath("$.content[1].name").value("Zed"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void emptyListReturnsEmptyContentAndPageMetadata() throws Exception {
        // given: 참여자를 등록하지 않는다.
        // when / then: 빈 배열을 반환한다.
        mvc.perform(get("/api/participants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void paginationReturnsOrderedPagesAndRetainsTotalsBeyondLastPage() throws Exception {
        // given: 순서가 다른 활성 참여자 3명과 삭제된 참여자 1명을 저장한다.
        em.persist(Participant.register("Zed"));
        em.persist(Participant.register("Aaron").delete(Instant.EPOCH));
        em.persist(Participant.register("Amy"));
        em.persist(Participant.register("Ben"));
        em.flush();
        em.clear();

        // when / then: 첫 페이지에 정렬된 2명과 활성 참여자 전체 개수를 반환한다.
        mvc.perform(get("/api/participants").param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].name").value("Amy"))
                .andExpect(jsonPath("$.content[1].name").value("Ben"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
        // when / then: 마지막 페이지에 남은 참여자 1명을 반환한다.
        mvc.perform(get("/api/participants").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("Zed"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
        // when / then: 마지막 페이지 이후에도 전체 개수를 보존한다.
        mvc.perform(get("/api/participants").param("page", "2").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void sameNamesHaveStableIdOrderEvenWithSortParameter() throws Exception {
        // given: 동일한 이름을 가진 참여자를 저장하고 DB의 ID 오름차순을 확인한다.
        em.persist(Participant.register("동명이인"));
        em.persist(Participant.register("동명이인"));
        em.flush();
        em.clear();
        var ids = em.createQuery("select p.id from Participant p order by p.id asc", UUID.class)
                .getResultList();

        // when / then: sort 파라미터는 정렬을 바꾸지 않으며 두 페이지가 ID 순서로 연결된다.
        for (int page = 0; page < 2; page++) {
            mvc.perform(get("/api/participants").param("page", String.valueOf(page))
                            .param("size", "1").param("sort", "id,desc"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(ids.get(page).toString()))
                    .andExpect(jsonPath("$.totalElements").value(2));
        }
    }

    @Test
    void defaultPageLimitsResultsAndMaximumSizeIsAccepted() throws Exception {
        // given: 기본 페이지 크기보다 많은 활성 참여자를 저장한다.
        IntStream.range(0, 21).forEach(i -> em.persist(Participant.register("참여자" + i)));
        em.flush();
        em.clear();

        // when / then: 기본 요청은 20명만 반환하며 다음 페이지가 존재한다.
        mvc.perform(get("/api/participants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(20)))
                .andExpect(jsonPath("$.totalElements").value(21))
                .andExpect(jsonPath("$.totalPages").value(2));
        // when / then: 최대 크기 100은 정상 요청이다.
        mvc.perform(get("/api/participants").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(21)))
                .andExpect(jsonPath("$.size").value(100));
        // when / then: size만 생략해도 기본 크기 20을 유지한다.
        mvc.perform(get("/api/participants").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(20));
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,-1", "0,101", "abc,20", "0,abc",
            "2147483648,20", "0,2147483648", "2147483647,100",
            "'',20", "0,''", "'',''", "' ',20", "0,' '"})
    void invalidPaginationReturns400(String page, String size) throws Exception {
        // given: 범위 또는 형식이 잘못된 페이지 조건을 준비한다.
        // when / then: 공통 입력 오류 응답을 반환한다.
        mvc.perform(get("/api/participants").param("page", page).param("size", size))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void deletedParticipantCannotBeRetrieved() throws Exception {
        // given: 삭제된 참여자를 저장한다.
        Participant participant = Participant.register("삭제됨").delete(Instant.EPOCH);
        em.persist(participant);
        em.flush();
        em.clear();

        // when / then: 상세 조회에서는 공통 404 오류를 반환한다.
        mvc.perform(get("/api/participants/" + participant.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        assertTrue(repository.findById(participant.getId()).isPresent());
    }

    @Test
    void missingParticipantReturns404() throws Exception {
        // given: 저장하지 않은 ID를 준비한다.
        UUID id = UUID.randomUUID();
        // when / then: 상세 조회는 공통 404 오류를 반환한다.
        mvc.perform(get("/api/participants/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void malformedIdReturns400() throws Exception {
        // given: UUID 형식이 아닌 ID를 준비한다.
        // when / then: 상세 조회는 공통 400 오류를 반환한다.
        mvc.perform(get("/api/participants/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void openApiIncludesDocumentationFromAbstractApiClass() throws Exception {
        // given: ParticipantApi를 상속한 실제 컨트롤러가 등록되어 있다.
        // when / then: 상위 클래스의 문서와 요청·오류 응답 스키마를 노출한다.
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/participants'].post.summary").value("참여자 등록"))
                .andExpect(jsonPath("$.paths['/api/participants'].get.summary").value("참여자 목록 조회"))
                .andExpect(jsonPath("$.paths['/api/participants'].get.parameters[?(@.name == 'page')].schema.type")
                        .value(hasItem("integer")))
                .andExpect(jsonPath("$.paths['/api/participants'].get.parameters[?(@.name == 'page')].schema.default")
                        .value(hasItem(0)))
                .andExpect(jsonPath("$.paths['/api/participants'].get.parameters[?(@.name == 'size')].schema.type")
                        .value(hasItem("integer")))
                .andExpect(jsonPath("$.paths['/api/participants'].get.parameters[?(@.name == 'size')].schema.default")
                        .value(hasItem(20)))
                .andExpect(jsonPath("$.paths['/api/participants/{id}'].get.summary").value("참여자 상세 조회"))
                .andExpect(jsonPath("$.paths['/api/participants/{id}'].patch.summary").value("참여자 이름 수정"))
                .andExpect(jsonPath("$.paths['/api/participants/{id}'].delete.summary").value("참여자 삭제"))
                .andExpect(jsonPath("$.paths['/api/participants/{id}'].patch.requestBody.required").value(true))
                .andExpect(jsonPath("$.paths['/api/participants/{id}'].delete.responses['204'].content").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/participants'].post.requestBody.required").value(true))
                .andExpect(jsonPath("$.paths['/api/participants'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/participants/{id}'].get.responses['404'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ErrorResponse"));
    }

    @Test
    void updatePersistsTrimmedNameAndIncrementsVersion() throws Exception {
        // given: 기존 참여자와 버전을 준비한다.
        Participant participant = Participant.register("기존 이름");
        em.persist(participant);
        em.flush();
        UUID id = participant.getId();
        long version = participant.getVersion();
        em.clear();
        String name = "가".repeat(20);

        // when: 공백을 제거하면 20자인 이름으로 수정하고 DB에 반영한다.
        mvc.perform(patch("/api/participants/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  " + name + "  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value(name));
        em.flush();
        em.clear();

        // then: 재조회 시 새 이름과 증가한 버전을 확인한다.
        Participant updated = repository.findById(id).orElseThrow();
        assertEquals(name, updated.getName());
        assertTrue(updated.getVersion() > version);
        mvc.perform(get("/api/participants/" + id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value(name));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"name\":null}", "{\"name\":\"\"}", "{\"name\":\"   \"}",
            "{\"name\":\"123456789012345678901\"}", "null", "{broken"})
    void invalidUpdateReturns400WithoutChangingName(String body) throws Exception {
        // given: 변경 대상 참여자와 잘못된 요청을 준비한다.
        Participant participant = Participant.register("기존 이름");
        em.persist(participant);
        em.flush();
        em.clear();

        // when: 잘못된 이름 수정 요청을 전달한다.
        mvc.perform(patch("/api/participants/" + participant.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        em.flush();
        em.clear();

        // then: 기존 이름이 유지된다.
        assertEquals("기존 이름", repository.findById(participant.getId()).orElseThrow().getName());
    }

    @Test
    void deletedParticipantCannotBeUpdated() throws Exception {
        // given: 삭제된 참여자를 저장한다.
        Participant participant = Participant.register("삭제됨").delete(Instant.EPOCH);
        em.persist(participant);
        em.flush();
        em.clear();

        // when / then: 수정 요청은 삭제된 참여자 오류를 반환한다.
        mvc.perform(patch("/api/participants/" + participant.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"새 이름\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PARTICIPANT_DELETED"));
        em.clear();
        assertEquals("삭제됨", repository.findById(participant.getId()).orElseThrow().getName());
    }

    @Test
    void deleteRetainsRowAndExcludesParticipantFromPublicQueries() throws Exception {
        // given: 활성 참여자를 저장한다.
        Participant participant = Participant.register("참여자");
        em.persist(participant);
        em.flush();
        UUID id = participant.getId();
        long version = participant.getVersion();
        em.clear();

        // when: 삭제 API를 호출하고 DB에 반영한다.
        mvc.perform(delete("/api/participants/" + id))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        em.flush();
        em.clear();

        // then: 행과 이름은 남고 삭제 시각과 버전이 변경되며 공개 조회에서는 제외된다.
        Participant deleted = repository.findById(id).orElseThrow();
        Instant deletedAt = deleted.getDeletedAt();
        long deletedVersion = deleted.getVersion();
        assertNotNull(deletedAt);
        assertEquals("참여자", deleted.getName());
        assertTrue(deletedVersion > version);
        mvc.perform(get("/api/participants/" + id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/participants"))
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));

        // when: 같은 참여자를 다시 삭제한다.
        mvc.perform(delete("/api/participants/" + id)).andExpect(status().isNoContent());
        em.flush();
        em.clear();

        // then: 최초 삭제 시각과 버전이 유지된다.
        deleted = repository.findById(id).orElseThrow();
        assertEquals(deletedAt, deleted.getDeletedAt());
        assertEquals(deletedVersion, deleted.getVersion());
    }

    @Test
    void updateAndDeleteMissingParticipantReturn404() throws Exception {
        // given: 존재하지 않는 ID를 준비한다.
        String path = "/api/participants/" + UUID.randomUUID();
        // when / then: 수정·삭제 모두 공통 404 오류를 반환한다.
        mvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"새 이름\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mvc.perform(delete(path))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void updateAndDeletePreserveProfileAndHistoricalSnapshot() throws Exception {
        // given: 프로필과 확정 당시 이름이 저장된 경기 기록을 준비한다.
        Participant participant = Participant.register("확정 당시 이름");
        em.persist(participant);
        GameProfile profile = new GameProfile(participant.getId(), Game.LOL, null, 1000, 1500,
                Set.of(Position.LOL_TOP));
        em.persist(profile);
        var players = IntStream.range(0, 10).mapToObj(i -> {
            Participant player = i == 0 ? participant : Participant.register("참여자" + i);
            if (i != 0) em.persist(player);
            return new MatchParticipant(player.getId(), player.getName(), 1500, Position.LOL_TOP,
                    i < 5 ? MatchParticipant.Team.A : MatchParticipant.Team.B);
        }).toList();
        Match match = new Match(UUID.randomUUID(), Game.LOL, Instant.EPOCH, players, null);
        em.persist(match);
        em.flush();
        em.clear();

        // when: 이름 수정 후 Soft Delete를 수행한다.
        String path = "/api/participants/" + participant.getId();
        mvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"새 이름\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete(path)).andExpect(status().isNoContent());
        em.flush();
        em.clear();

        // then: 프로필과 경기 스냅샷의 이름·점수·포지션·팀은 보존된다.
        assertEquals(1500, em.find(GameProfile.class, profile.getId()).effectiveScore());
        var snapshots = em.find(Match.class, match.getId()).getParticipants();
        assertEquals(10, snapshots.size());
        var snapshot = snapshots.stream().filter(p -> p.getParticipantId().equals(participant.getId()))
                .findFirst().orElseThrow();
        assertEquals("확정 당시 이름", snapshot.getName());
        assertEquals(1500, snapshot.getScore());
        assertEquals(Position.LOL_TOP, snapshot.getAssignedPosition());
        assertEquals(MatchParticipant.Team.A, snapshot.getTeam());
    }

    @Test
    void updateAndDeleteMalformedIdReturn400() throws Exception {
        // given: UUID 형식이 아닌 ID를 준비한다.
        String path = "/api/participants/not-a-uuid";
        // when / then: 수정·삭제 모두 공통 입력 오류를 반환한다.
        mvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"새 이름\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(delete(path))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
