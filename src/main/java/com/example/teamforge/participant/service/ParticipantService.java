package com.example.teamforge.participant.service;

import com.example.teamforge.common.exception.BusinessException;
import com.example.teamforge.common.exception.ErrorCode;
import com.example.teamforge.participant.dto.ParticipantResponse;
import com.example.teamforge.participant.dto.ParticipantPageResponse;
import com.example.teamforge.participant.entity.Participant;
import com.example.teamforge.participant.repository.ParticipantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParticipantService {

    private final ParticipantRepository participantRepository;

    @Transactional
    public ParticipantResponse register(String name) {
        Participant participant = participantRepository.save(Participant.register(name));
        return ParticipantResponse.from(participant);
    }

    public ParticipantPageResponse findAll(int page, int size) {
        // JPA 조회 offset은 int 범위이므로 넘치는 요청을 DB 호출 전에 거절한다.
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return ParticipantPageResponse.from(participantRepository
                .findAllByDeletedAtIsNullOrderByNameAscIdAsc(PageRequest.of(page, size))
                .map(ParticipantResponse::from));
    }

    public ParticipantResponse findById(UUID id) {
        Participant participant = participantRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return ParticipantResponse.from(participant);
    }

    @Transactional
    public ParticipantResponse update(UUID id, String name) {
        Participant participant = findParticipant(id);
        participant.rename(name);
        return ParticipantResponse.from(participant);
    }

    @Transactional
    public void delete(UUID id) {
        findParticipant(id).delete(Instant.now());
    }

    private Participant findParticipant(UUID id) {
        return participantRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
