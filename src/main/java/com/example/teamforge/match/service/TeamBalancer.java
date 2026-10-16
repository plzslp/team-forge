package com.example.teamforge.match.service;

import com.example.teamforge.common.exception.BusinessException;
import com.example.teamforge.common.exception.ErrorCode;
import com.example.teamforge.gameprofile.entity.Game;
import com.example.teamforge.gameprofile.entity.Position;
import com.example.teamforge.match.dto.PreviewCandidate;
import com.example.teamforge.match.entity.MatchParticipant;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** 모든 5:5 분할에서 비선호 배정 수, 팀 점수 차이 순으로 최적 편성을 선택한다. */
@Component
public class TeamBalancer {
    /**
     * 참가자 10명의 팀 구성과 포지션을 함께 결정한다. DB 조회나 저장은 하지 않는다.
     * 비선호 배정 인원을 먼저 최소화하고, 그 인원이 같은 편성 중 점수 차이를 최소화한다.
     *
     * @param game 역할 구성을 결정할 게임
     * @param candidates 이름·점수·선호 포지션을 담은 중복 없는 참가자 10명
     * @param allowNonPreferredPositions 선호 편성이 불가능할 때 비선호 배정을 허용할지 여부
     * @return A/B팀 각각 5명의 배정 결과
     * @throws BusinessException 입력이 올바르지 않거나, 필요한 비선호 배정을 허용하지 않은 경우
     */
    public List<MatchParticipant> balance(Game game, List<PreviewCandidate> candidates,
                                          boolean allowNonPreferredPositions) {
        validate(game, candidates);
        // 요청의 참가자 순서가 달라도 동일한 순서로 탐색하여 동률 결과를 일정하게 유지한다.
        List<PreviewCandidate> ordered = candidates.stream()
                .sorted(Comparator.comparing(PreviewCandidate::participantId)).toList();
        List<Position> slots = slots(game);
        TeamAssignment bestA = null;
        TeamAssignment bestB = null;
        // 첫 계산 결과가 최적 후보로 선택되도록 비교 기준을 가장 큰 값으로 시작한다.
        int bestViolations = Integer.MAX_VALUE;
        long bestDifference = Long.MAX_VALUE;
        // mask의 각 비트는 참가자 한 명의 A팀 포함 여부다. 1이면 A팀, 0이면 B팀이다.
        // 1 << 10은 1024이며, 10명의 모든 선택 상태는 0~1023으로 표현된다.
        // 홀수만 탐색하면 0번 참가자 비트가 항상 1이다. A/B만 뒤집힌 중복을 제외한다.
        for (int mask = 1; mask < (1 << ordered.size()); mask += 2) {
            // 1인 비트가 정확히 5개인 상태만 사용한다. 이 조건을 통과하는 분할은 126개다.
            if (Integer.bitCount(mask) != 5) continue;
            List<PreviewCandidate> teamA = new ArrayList<>();
            List<PreviewCandidate> teamB = new ArrayList<>();
            for (int index = 0; index < ordered.size(); index++) {
                // 1 << index는 현재 참가자 자리만 1로 만든다. & 결과가 0이 아니면 A팀에 포함된다.
                ((mask & (1 << index)) != 0 ? teamA : teamB).add(ordered.get(index));
            }
            // 이번에 정한 팀원을 유지하면서, 각 팀의 최소 비선호 포지션 배정을 구한다.
            TeamAssignment assignmentA = assign(teamA, slots);
            TeamAssignment assignmentB = assign(teamB, slots);
            int violations = assignmentA.violations() + assignmentB.violations();
            long difference = Math.abs(assignmentA.totalScore() - assignmentB.totalScore());
            // 비선호 인원 수가 우선이다. 점수 차이가 작아도 비선호 인원이 늘어나면 선택하지 않는다.
            // 두 기준이 모두 같으면 먼저 찾은 결과를 유지한다.
            if (violations < bestViolations || (violations == bestViolations && difference < bestDifference)) {
                bestA = assignmentA;
                bestB = assignmentB;
                bestViolations = violations;
                bestDifference = difference;
            }
        }
        // 모든 분할을 비교한 최적 결과에도 비선호가 필요할 때만 허용 여부를 확인한다.
        if (bestViolations > 0 && !allowNonPreferredPositions) {
            throw new BusinessException(ErrorCode.PREFERRED_POSITION_UNAVAILABLE);
        }
        List<MatchParticipant> result = new ArrayList<>();
        append(result, bestA, slots, MatchParticipant.Team.A);
        append(result, bestB, slots, MatchParticipant.Team.B);
        return List.copyOf(result);
    }

    /** DB의 존재·활성 여부는 Service가 확인하고, 여기서는 계산 입력의 기본 규칙을 검증한다. */
    private void validate(Game game, List<PreviewCandidate> candidates) {
        if (game == null || candidates == null || candidates.size() != 10
                || candidates.stream().anyMatch(candidate -> candidate == null || candidate.participantId() == null)
                || candidates.stream().map(PreviewCandidate::participantId).distinct().count() != 10) {
            throw new BusinessException(ErrorCode.INVALID_TEAM_COMPOSITION);
        }
        if (candidates.stream().anyMatch(candidate -> candidate.preferredPositions().isEmpty()
                || candidate.preferredPositions().stream().anyMatch(position -> !position.supports(game)))) {
            throw new BusinessException(ErrorCode.INVALID_PREFERRED_POSITION);
        }
    }

    /** 각 항목은 한 사람이 들어갈 자리다. 오버워치의 딜러·서포터는 각각 두 자리를 차지한다. */
    private List<Position> slots(Game game) {
        return switch (game) {
            case LOL -> List.of(Position.LOL_TOP, Position.LOL_JUNGLE, Position.LOL_MID,
                    Position.LOL_BOTTOM, Position.LOL_SUPPORT);
            case OVERWATCH -> List.of(Position.OVERWATCH_TANK, Position.OVERWATCH_DAMAGE,
                    Position.OVERWATCH_DAMAGE, Position.OVERWATCH_SUPPORT, Position.OVERWATCH_SUPPORT);
        };
    }

    /**
     * 이미 정해진 팀원 5명을 역할 자리 5개에 배정한다. 팀원 자체는 바꾸지 않는다.
     * 선택된 사람들을 앞쪽 역할에 배정하는 최소 비용을 메모하고, 더 큰 집합의 계산에 재사용한다.
     * 여기서 비용은 비선호 배정 인원 수이며, 선택한 사람 집합을 비트마스크로 표현한다.
     */
    private TeamAssignment assign(List<PreviewCandidate> candidates, List<Position> slots) {
        // 5명 각각의 포함/미포함 조합은 2^5 = 32개다. 아무도 포함하지 않은 상태도 센다.
        int states = 1 << candidates.size();
        // costs[mask]: 선택된 사람들을 첫 번째부터 k번째 역할에 배정하는 최소 비선호 인원.
        // k는 mask에 포함된 사람 수다. 예: 00101은 0·2번을 첫 두 역할에 배정하는 문제다.
        int[] costs = new int[states];
        // chosen[mask]: 위 최솟값을 만들 때 k번째(마지막) 역할에 배정한 참가자 번호.
        int[] chosen = new int[states];
        Arrays.fill(costs, Integer.MAX_VALUE);
        // 아무도 배정하지 않은 상태의 비선호 인원은 0명이다. 나머지 계산의 출발점이다.
        costs[0] = 0;
        for (int mask = 1; mask < states; mask++) {
            // k명이면 마지막 역할의 인덱스는 k-1이다. 롤에서 2명이면 탑·정글 중 정글을 검토한다.
            Position position = slots.get(Integer.bitCount(mask) - 1);
            for (int index = 0; index < candidates.size(); index++) {
                // 현재 집합에 포함된 사람만 마지막 역할의 후보로 검토한다.
                if ((mask & (1 << index)) == 0) continue;
                // 후보를 마지막 역할에 넣는다고 가정하고, 그 사람을 제외한 이전 상태를 구한다.
                // 포함된 비트에 XOR(^)를 적용하므로 1이 0으로 바뀐다. 예: 00101 ^ 00100 = 00001.
                // previous는 mask보다 작아 이미 계산됐으며, 나머지 사람들은 앞쪽 역할들을 채운다.
                int previous = mask ^ (1 << index);
                // 나머지 사람들의 최소 비용 + 현재 배정의 비용(선호면 0, 비선호면 1).
                int cost = costs[previous] + (candidates.get(index).preferredPositions().contains(position) ? 0 : 1);
                if (cost < costs[mask]) {
                    costs[mask] = cost;
                    // 전체 배정을 복사하지 않고 마지막 선택만 기억해 두었다가 아래에서 역추적한다.
                    chosen[mask] = index;
                }
            }
        }
        // assignments[역할 인덱스] = 그 역할에 배정한 참가자 인덱스.
        int[] assignments = new int[slots.size()];
        // 32-1 = 31(11111): 팀원 5명 전체를 배정한 최종 상태에서 시작한다.
        int mask = states - 1;
        for (int slot = slots.size() - 1; slot >= 0; slot--) {
            // 마지막 역할의 담당자를 읽고 제외하면, 앞 역할들의 최적 배정을 기록한 상태로 돌아간다.
            // 롤은 서포터 → 원딜 → 미드 → 정글 → 탑 순서로 복원한다.
            assignments[slot] = chosen[mask];
            mask ^= 1 << chosen[mask];
        }
        // 팀 점수 합계는 포지션과 무관하며, int 범위를 넘을 수 있으므로 long으로 합산한다.
        return new TeamAssignment(candidates, assignments, costs[states - 1],
                candidates.stream().mapToLong(PreviewCandidate::score).sum());
    }

    /** 역할별 참가자 인덱스를 실제 참가자 정보로 바꾸어 결과에 추가한다. DB에 저장하지 않는다. */
    private void append(List<MatchParticipant> result, TeamAssignment assignment, List<Position> slots,
                        MatchParticipant.Team team) {
        for (int slot = 0; slot < slots.size(); slot++) {
            // 역할 자리 → 담당자 번호 → 팀원 명단의 실제 참가자 순서로 찾는다.
            PreviewCandidate candidate = assignment.candidates().get(assignment.assignments()[slot]);
            result.add(new MatchParticipant(candidate.participantId(), candidate.name(), candidate.score(), slots.get(slot), team));
        }
    }

    /** 팀원 명단·역할별 담당자 인덱스·비선호 인원·팀 총점을 묶은 내부 계산 결과. */
    private record TeamAssignment(List<PreviewCandidate> candidates, int[] assignments, int violations, long totalScore) {
    }
}
