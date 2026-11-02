package com.example.teamforge.match.service;

import com.example.teamforge.common.exception.BusinessException;
import com.example.teamforge.common.exception.ErrorCode;
import com.example.teamforge.gameprofile.entity.GameProfile;
import com.example.teamforge.gameprofile.repository.GameProfileRepository;
import com.example.teamforge.match.dto.ParticipantVersions;
import com.example.teamforge.match.dto.PreviewCandidate;
import com.example.teamforge.match.dto.TeamPreview;
import com.example.teamforge.match.dto.request.TeamPreviewRequest;
import com.example.teamforge.match.dto.response.TeamPreviewResponse;
import com.example.teamforge.participant.entity.Participant;
import com.example.teamforge.participant.repository.ParticipantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamPreviewService {
    private final ParticipantRepository participantRepository;
    private final GameProfileRepository gameProfileRepository;
    private final TeamBalancer teamBalancer;

    public TeamPreviewResponse create(TeamPreviewRequest request) {
        List<UUID> ids = request.participantIds();
        if (request.game() == null || ids == null || ids.size() != 10 || ids.stream().anyMatch(Objects::isNull)
                || ids.stream().distinct().count() != 10) {
            throw new BusinessException(ErrorCode.INVALID_TEAM_COMPOSITION);
        }
        List<Participant> participants = participantRepository.findAllByIdInAndDeletedAtIsNull(ids);
        if (participants.size() != 10) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        Map<UUID, GameProfile> profiles = gameProfileRepository
                .findAllWithPositionsByParticipantIdsAndGame(ids, request.game()).stream()
                .collect(Collectors.toMap(GameProfile::getParticipantId, Function.identity()));
        if (profiles.size() != 10) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        List<PreviewCandidate> candidates = new ArrayList<>();
        Map<UUID, ParticipantVersions> versions = new HashMap<>();
        for (Participant participant : participants) {
            GameProfile profile = profiles.get(participant.getId());
            candidates.add(new PreviewCandidate(participant.getId(), participant.getName(), profile.effectiveScore(),
                    profile.getPreferredPositions()));
            versions.put(participant.getId(), new ParticipantVersions(participant.getVersion(), profile.getVersion()));
        }
        TeamPreview preview = new TeamPreview(request.game(),
                teamBalancer.balance(request.game(), candidates, request.allowNonPreferredPositions()), versions);
        return TeamPreviewResponse.from(preview, candidates);
    }
}
