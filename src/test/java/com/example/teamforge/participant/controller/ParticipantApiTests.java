package com.example.teamforge.participant.controller;

import com.example.teamforge.participant.entity.Participant;
import com.example.teamforge.participant.repository.ParticipantRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
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
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Amy"))
                .andExpect(jsonPath("$[1].name").value("Zed"));
    }

    @Test
    void emptyListReturnsEmptyArray() throws Exception {
        // given: 참여자를 등록하지 않는다.
        // when / then: 빈 배열을 반환한다.
        mvc.perform(get("/api/participants"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
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
                .andExpect(jsonPath("$.paths['/api/participants/{id}'].get.summary").value("참여자 상세 조회"))
                .andExpect(jsonPath("$.paths['/api/participants'].post.requestBody.required").value(true))
                .andExpect(jsonPath("$.paths['/api/participants'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/participants/{id}'].get.responses['404'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ErrorResponse"));
    }
}
