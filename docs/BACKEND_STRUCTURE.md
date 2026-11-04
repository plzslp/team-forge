# 백엔드 구조

TeamForge 백엔드는 Java 17과 Spring Boot를 사용하는 단일 Gradle 프로젝트입니다. HTTP 요청은 Controller → Service → Repository 순으로 처리하고, Service에서 트랜잭션을 관리합니다. 각 도메인의 `Api` 추상 클래스에 Swagger 계약을, `dto/request`와 `dto/response`에 요청·응답 모델을 둡니다.

| 패키지 | 책임 |
| --- | --- |
| `participant` | 참가자 등록, 이름 수정, 조회, 논리 삭제 |
| `gameprofile` | 게임별 계정·선호 포지션·점수 관리 |
| `match` | 팀 편성, 경기 확정과 스냅샷 저장, 결과 입력·조회 |
| `common.exception` | 공통 오류 응답 |

로컬 실행과 테스트는 H2를 사용합니다. Flyway가 스키마를 관리하고 JPA는 매핑을 검증합니다. 운영 PostgreSQL 연결과 잠금 검증은 후속 작업입니다.

## API

요청과 응답의 전체 스키마는 [Swagger UI](http://127.0.0.1:8080/swagger-ui.html)에서 확인할 수 있습니다.

| 기능 | 경로 |
| --- | --- |
| 참가자 등록·목록 | `POST /api/participants`, `GET /api/participants` |
| 참가자 상세·이름 수정·삭제 | `GET/PATCH/DELETE /api/participants/{id}` |
| 게임 프로필 조회·전체 수정 | `GET/PUT /api/participants/{id}/profiles/{game}` |
| 게임별 프로필 목록 | `GET /api/game-profiles?game=LOL` |
| 팀 편성 미리보기 | `POST /api/team-previews` |
| 경기 확정 | `POST /api/matches` |
| 경기 목록·상세 | `GET /api/matches?game=LOL`, `GET /api/matches/{id}` |
| 경기 결과 입력·전체 수정 | `PUT /api/matches/{id}/result` |

`game`은 `LOL` 또는 `OVERWATCH`입니다. 목록은 `page=0`, `size=20`이 기본이며 최대 100건까지 요청할 수 있습니다. 참가자·프로필 목록은 이름·ID 오름차순, 경기 목록은 생성 시각·ID 내림차순입니다. 프로필 목록은 `keyword`와 `position`으로 필터링할 수 있습니다.

## 편성부터 결과 입력까지

1. **프로필 준비** · 활성 참가자의 게임 프로필을 사용합니다. 수동 점수는 환산 점수보다 우선하며, `0`도 유효한 수동 점수입니다.
2. **미리보기** · 중복 없는 참가자 10명을 5:5로 나눕니다. 비선호 배정을 최소화한 뒤 팀 점수 차이를 최소화합니다. 비선호 배정이 필요하면 `allowNonPreferredPositions: true`를 명시해야 합니다. 응답의 `participantVersions`는 확정 요청에 사용합니다. [편성 알고리즘 설명](TEAM_BALANCER_GUIDE.md)
3. **확정** · 최종 팀·포지션과 미리보기 버전을 전달합니다. 서버는 참가자 행, 게임 프로필 행 순으로 잠그고 최신 버전과 역할 구성을 확인합니다. 오래된 미리보기는 409로 거절합니다. 성공하면 확정 당시 이름·점수·포지션을 스냅샷으로 저장하고 상세 조회 `Location`을 반환합니다.
4. **결과 입력** · `scoreA`, `scoreB`는 0 이상의 JSON 정수이며 `memo`는 최대 2000자입니다. 메모를 생략하거나 `null`로 보내면 빈 문자열로 저장합니다. 미입력은 `result: null`, 입력된 0:0은 `DRAW`입니다. 참가자 수정·삭제 후에도 경기 스냅샷을 기준으로 조회하고 결과를 수정할 수 있습니다.

결과 수정은 같은 버전을 읽은 트랜잭션이 서로 다른 결과를 저장하려 할 때 낙관적 잠금으로 충돌을 감지합니다. 충돌 요청은 409 `CONCURRENT_MODIFICATION`을 반환합니다. 이전 트랜잭션이 완료된 뒤 새로 읽고 쓰는 요청은 기존 결과를 교체합니다.

오류 응답은 `code`와 `message`를 반환합니다. 잘못된 입력은 400, 리소스가 없으면 404, 버전·잠금 충돌은 409입니다.
