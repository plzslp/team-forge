# 백엔드 구조

## 아키텍처 결정

2026-09-19 기준, 단일 Gradle 프로젝트 안에서 **업무별 패키지 + 레이어드 아키텍처**를 사용한다. `participant`, `match`는 업무를 구분하는 일반 패키지이며 각 내부에 `controller`, `service`, `repository`, `entity`, `dto`를 둔다.

엄격한 DDD 모듈 경계와 Spring Modulith는 사용하지 않는다. 사용처·구현체가 없던 `ParticipantLookup`, `ParticipantSnapshot`과 Modulith 전용 경계 테스트를 제거했다. JPA 모델에 구현된 점수 보정, Soft Delete, 경기 기록 검증은 유지한다.

## 패키지 구조와 현재 구현

```text
com.example.teamforge
├── TeamForgeApplication
├── participant
│   ├── controller              # 패키지 안내만 존재, 구현 예정
│   ├── service                 # 패키지 안내만 존재, 구현 예정
│   ├── repository              # 패키지 안내만 존재, 구현 예정
│   ├── dto                    # 패키지 안내만 존재, 구현 예정
│   └── entity
│       ├── Game                # LOL / OVERWATCH enum
│       ├── Position            # 게임 정보를 포함한 포지션 enum
│       ├── Participant
│       └── GameProfile
├── match
│   ├── controller              # 패키지 안내만 존재, 구현 예정
│   ├── service                 # 패키지 안내만 존재, 구현 예정
│   ├── repository              # 패키지 안내만 존재, 구현 예정
│   ├── dto
│   │   └── TeamPreview         # 미저장 편성 결과 record
│   └── entity
│       ├── Match
│       ├── MatchParticipant
│       └── MatchResult         # Embeddable
└── external
    └── riot                   # 패키지 안내만 존재, 실제 연동 예정
```

아직 REST API, 서비스, repository, Riot 통신, 실제 팀 편성 알고리즘은 없다. 빈 구현 클래스는 만들지 않고 `package-info.java`로 계층별 책임을 남긴다. 실제 기능은 이후 작업에서 추가한다.

## 계층별 책임

| 계층 | 책임 |
|---|---|
| controller | HTTP 요청 수신, 입력 검증, service 호출, 응답·오류 변환 |
| service | 기능 처리, 트랜잭션, repository 호출, 외부 API 결과 반영 |
| repository | Spring Data JPA를 통한 저장·조회. 활성 참여자 조회에 Soft Delete 조건 적용 |
| entity | JPA 매핑, 상태 변경과 모델 자체의 기본 검증 |
| dto | 요청·응답과 서비스 처리 결과 전달 |

기본 흐름은 `controller → service → repository`다. Entity는 controller나 service를 참조하지 않는다. 다른 업무의 기능이 필요하면 해당 service를 호출하는 방식으로 시작하고 순환 호출은 피한다. 이를 위해 별도의 조회 인터페이스나 변환 계층을 의무적으로 만들지는 않는다.

`Game`은 참여자 게임 프로필에서 정의하며 match에서도 같은 enum을 사용한다. `TeamPreview`는 현재 내부 처리 결과이며 `MatchParticipant` 목록을 담는다. 향후 HTTP API에서는 필요한 필드만 담은 응답 DTO를 정의하고 JPA Entity를 그대로 직렬화하지 않는다.

`Match.validateTeams`는 미리보기와 확정 기록이 공유하는 인원·중복·5:5 검증이다. 실제 포지션 배정과 점수 차이 최소화 알고리즘은 이후 `match.service` 아래 별도 계산 클래스로 구현해 HTTP·DB 없이 테스트한다.

## 외부 API 분리

Riot 연동은 `external.riot` 패키지에 클라이언트, 외부 요청·응답 DTO, 통신 오류 처리를 모은다. 참여자 service가 클라이언트를 호출하고 결과를 내부 모델에 반영한다. API 키는 환경 변수 등 외부 설정에서 주입한다.

현재는 패키지 수준 분리이며 별도 Gradle 모듈이나 HTTP 클라이언트 의존성을 추가하지 않았다. 실제 연동 구현 시 필요한 통신 의존성을 선택한다.

## 저장 모델과 유지한 정책

| 모델 | 저장 형태 |
|---|---|
| Participant | `participants`, `deletedAt`으로 Soft Delete 표시 |
| GameProfile | `game_profiles` + `game_profile_positions`, 참여자 ID·게임 조합 유일 |
| Match | `matches`, MatchParticipant를 단방향 OneToMany로 소유 |
| MatchParticipant | `match_participants`, 참여자는 UUID로 참조하고 확정 당시 값을 복사 |
| MatchResult | Embeddable, `matches`에 스코어·메모 포함 |
| TeamPreview | record, 영구 저장하지 않음 |

Participant·GameProfile·Match에는 낙관적 잠금용 `@Version`이 있다. 별도 저장 Entity와 변환 계층은 만들지 않는다. Lombok은 Getter와 보호된 기본 생성자에 사용하며 Setter/Data는 사용하지 않는다. 컬렉션 getter는 읽기 전용 복사본을 반환한다.

- 수동 점수는 환산 점수보다 우선한다. 갱신해도 유지하며 `null`은 보정 해제, `0`은 유효한 지정값이다.
- 참여자 삭제는 과거 기록 삭제가 아니다. 확정 당시 닉네임·점수·포지션·팀은 이후 현재 정보 수정으로 바뀌지 않는다.
- 패키지 재배치 자체는 테이블·컬럼을 변경하지 않는다. 포지션 enum 전환의 저장값 변경은 아래 기준을 따른다.

## build.gradle 의존성

Spring Boot 4.1.1, Java 17과 기존 플러그인 버전은 유지한다. 라이브러리 버전은 Spring Boot의 의존성 관리에 맡긴다.

| 의존성 | 용도·범위 |
|---|---|
| Web MVC starter | REST API 기반, implementation |
| Data JPA starter | JPA Entity와 저장소 기반, implementation |
| Flyway starter | DB 마이그레이션 기반, implementation. 실제 스크립트는 아직 없음 |
| Flyway PostgreSQL 지원·PostgreSQL JDBC | 운영 DB 지원, runtimeOnly |
| H2 | 로컬 실행·테스트 DB, runtimeOnly. 현재 실행 호환성을 위해 유지하며 bootJar에도 포함 |
| H2 console | developmentOnly, 운영 bootJar에서 제외 |
| Lombok | compileOnly + annotationProcessor |
| Spring Boot test starter | 현재 단위·SpringBootTest 통합 테스트, testImplementation |
| JUnit platform launcher | 테스트 실행, testRuntimeOnly |

Spring Modulith core/JPA/runtime/test와 BOM을 제거했다. 현재 사용하지 않는 JPA·Flyway·Web MVC 테스트 전용 starter, 테스트용 Lombok 설정도 제거했다. 향후 테스트 슬라이스를 실제로 사용할 때 해당 starter를 추가한다.

## 남은 구현과 주의점

1. DB 스키마·Flyway 마이그레이션과 환경별 설정을 확정한다.
2. 참여자 등록·조회 기능을 controller → service → repository → DB 순서로 연결한다.
3. 게임 프로필 수정·수동 보정·Soft Delete 기능을 연결한다.
4. 포지션 우선 편성 알고리즘과 비선호 배정 승인 검증을 구현한다.
5. 확정 시 최신 정보 검증·스냅샷 저장·경기 결과·최근 내전 조회를 구현한다.
6. Riot API를 실제로 연동한다.

현재 버전 맵은 형태만 정의했다. 미리보기 이후 참여자·프로필·삭제 상태 변경을 모두 감지하도록 양쪽 Entity 버전을 전달하고, 검증과 저장 사이 경쟁 상태를 트랜잭션·잠금으로 처리해야 한다. 각 Entity의 `@Version`만으로 전체 변경 감지가 완성되는 것은 아니다.

Match/TeamPreview의 검증은 인원·중복·5:5까지만 구현됐다. 포지션 구성과 비선호 배정 승인은 미구현이다. 티어·LP 원본, 조회 시각, 계정 유일성과 UUID 참조 무결성도 후속 설계 대상이다. 백엔드는 `OVERWATCH`, 프론트는 `OW`를 사용하므로 API 연결 시 통일 또는 변환이 필요하다.

## 검증

프로젝트 루트에서 실행한다.

```text
gradlew.bat test bootJar
```

ParticipantEntityTests·MatchEntityTests로 기존 모델 규칙, JpaModelTests로 H2 저장·변경 감지·스냅샷 저장, TeamForgeApplicationTests로 컨텍스트 구성을 검증한다. Modulith 전용 경계 테스트는 새 구조에서 제거했다.

테스트 작성 시 `// given`, `// when`, `// then` 주석으로 준비·실행·검증을 구분한다. 예외 검증처럼 실행과 검증이 함께 이루어지는 경우 `// when / then`을 사용한다. 여러 상태 변경을 검증하는 테스트는 각 단계의 when/then을 반복해 표시한다.

## 포지션 타입

`participant.entity.Position`은 게임 정보를 가진 단일 enum이다. 롤은 `LOL_TOP`, `LOL_JUNGLE`, `LOL_MID`, `LOL_BOTTOM`, `LOL_SUPPORT`, 오버워치는 `OVERWATCH_TANK`, `OVERWATCH_DAMAGE`, `OVERWATCH_SUPPORT`를 사용한다.

GameProfile의 선호 포지션은 `Set<Position>`, MatchParticipant의 배정 포지션은 `Position`이다. GameProfile은 비어 있는 선택과 다른 게임의 포지션을 거절한다. 두 필드 모두 `@Enumerated(EnumType.STRING)`으로 이름을 저장한다.

기존 문자열 데이터가 있다면 게임에 따라 `TOP` → `LOL_TOP`, `SUPPORT` → `LOL_SUPPORT` 또는 `OVERWATCH_SUPPORT` 등으로 변환해야 한다. 운영 DB 마이그레이션은 아직 없으며 이번 테스트는 새 H2 스키마에서 검증한다. 프론트의 기존 포지션 문자열은 API 연결 시 변환한다. enum 도입만으로 팀의 포지션 구성·비선호 배정 승인 검증이 완성되는 것은 아니다.
