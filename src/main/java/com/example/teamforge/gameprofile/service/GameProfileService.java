package com.example.teamforge.gameprofile.service;

import com.example.teamforge.common.exception.BusinessException;
import com.example.teamforge.common.exception.ErrorCode;
import com.example.teamforge.gameprofile.dto.GameProfileRequest;
import com.example.teamforge.gameprofile.dto.GameProfileResponse;
import com.example.teamforge.gameprofile.entity.Game;
import com.example.teamforge.gameprofile.entity.GameProfile;
import com.example.teamforge.participant.entity.Participant;
import com.example.teamforge.gameprofile.repository.GameProfileRepository;
import com.example.teamforge.participant.repository.ParticipantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameProfileService {
    private final ParticipantRepository participantRepository;
    private final GameProfileRepository gameProfileRepository;

    public GameProfileResponse findByParticipantIdAndGame(UUID participantId, Game game) {
        participantRepository.findByIdAndDeletedAtIsNull(participantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return GameProfileResponse.from(gameProfileRepository.findByParticipantIdAndGame(participantId, game)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND)));
    }

    @Transactional
    public UpsertResult upsert(UUID participantId, Game game, GameProfileRequest request) {
        Participant participant = participantRepository.findById(participantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!participant.active()) throw new BusinessException(ErrorCode.PARTICIPANT_DELETED);

        Optional<GameProfile> existing = gameProfileRepository.findByParticipantIdAndGame(participantId, game);
        GameProfile profile;
        boolean created = existing.isEmpty();
        if (created) {
            profile = gameProfileRepository.save(new GameProfile(participantId, game, request.accountId(),
                    0, request.manualScore(), request.preferredPositions()));
        } else {
            profile = existing.orElseThrow();
            profile.update(request.accountId(), request.manualScore(), request.preferredPositions());
        }
        return new UpsertResult(GameProfileResponse.from(profile), created);
    }

    public record UpsertResult(GameProfileResponse profile, boolean created) {}
}
