package com.example.teamforge.participant.service;

import com.example.teamforge.common.exception.BusinessException;
import com.example.teamforge.common.exception.ErrorCode;
import com.example.teamforge.participant.dto.ParticipantResponse;
import com.example.teamforge.participant.entity.Participant;
import com.example.teamforge.participant.repository.ParticipantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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

    public List<ParticipantResponse> findAll() {
        return participantRepository.findAllByDeletedAtIsNullOrderByNameAscIdAsc().stream()
                .map(ParticipantResponse::from)
                .toList();
    }

    public ParticipantResponse findById(UUID id) {
        Participant participant = participantRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return ParticipantResponse.from(participant);
    }
}
