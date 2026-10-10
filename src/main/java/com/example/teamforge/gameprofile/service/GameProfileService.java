package com.example.teamforge.gameprofile.service;

import com.example.teamforge.common.exception.BusinessException;
import com.example.teamforge.common.exception.ErrorCode;
import com.example.teamforge.gameprofile.dto.GameProfileListRequest;
import com.example.teamforge.gameprofile.dto.GameProfileListResponse;
import com.example.teamforge.gameprofile.dto.GameProfileListRow;
import com.example.teamforge.gameprofile.dto.GameProfilePageResponse;
import com.example.teamforge.gameprofile.dto.GameProfileRequest;
import com.example.teamforge.gameprofile.dto.GameProfileResponse;
import com.example.teamforge.gameprofile.entity.Game;
import com.example.teamforge.gameprofile.entity.GameProfile;
import com.example.teamforge.participant.entity.Participant;
import com.example.teamforge.gameprofile.repository.GameProfileRepository;
import com.example.teamforge.participant.repository.ParticipantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameProfileService {
    private final ParticipantRepository participantRepository;
    private final GameProfileRepository gameProfileRepository;

    public GameProfilePageResponse findAll(GameProfileListRequest request) {
        int page = request.getPage();
        int size = request.getSize();
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Page<GameProfileListRow> rows = gameProfileRepository.findActiveList(request.getGame(),
                searchPattern(request.getKeyword()), request.getPosition(), PageRequest.of(page, size));
        if (rows.isEmpty()) {
            return new GameProfilePageResponse(List.of(), page, size, rows.getTotalElements(), rows.getTotalPages());
        }
        List<UUID> ids = rows.getContent().stream().map(GameProfileListRow::profileId).toList();
        Map<UUID, GameProfile> profiles = gameProfileRepository.findAllWithPositionsByIdIn(ids).stream()
                .collect(Collectors.toMap(GameProfile::getId, Function.identity()));
        Page<GameProfileListResponse> result = rows.map(row ->
                GameProfileListResponse.from(profiles.get(row.profileId()), row.name()));
        return GameProfilePageResponse.from(result);
    }

    private String searchPattern(String keyword) {
        if (keyword == null) return null;
        String escaped = keyword.toLowerCase(Locale.ROOT).replace("!", "!!")
                .replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }

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
