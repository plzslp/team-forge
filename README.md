# TeamForge

[![Codecov](https://codecov.io/gh/plzslp/team-forge/branch/dev/graph/badge.svg)](https://app.codecov.io/gh/plzslp/team-forge/tree/dev)

롤·오버워치 내전 참가자 10명을 실력과 포지션을 고려해 5:5 팀으로 편성하는 개인용 웹 프로젝트입니다.

현재 개발 초기 단계이며, 프론트엔드는 실제 API·DB와 연결되지 않은 데모입니다.

## 기술 스택

| 구분 | 기술 |
|---|---|
| Backend | Java 17, Spring Boot, Spring Data JPA |
| Frontend | React, TypeScript, Vite |
| Database | H2, PostgreSQL |
| Build / Test | Gradle, JUnit |

## 로컬 실행

JDK 17과 Node.js·npm이 필요합니다. 아래 명령은 Windows 기준이며, 각 절은 저장소 루트에서 시작합니다.

### 프론트엔드

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev -- --port 5173 --strictPort
```

접속: [http://127.0.0.1:5173](http://127.0.0.1:5173)

데모 화면은 백엔드 없이 실행할 수 있습니다. 새로고침하면 참여자·편성 기록이 초기화됩니다.

### 백엔드

```powershell
.\gradlew.bat bootRun
```

접속 주소: [http://127.0.0.1:8080](http://127.0.0.1:8080)

프론트 화면은 별도로 실행합니다. 백엔드 루트 주소에서는 화면이 표시되지 않습니다.
참여자 등록·목록·상세 조회·이름 수정·Soft Delete API를 제공하며, 프론트 데모와는 아직 연결하지 않았습니다.
목록 조회는 `/api/participants?page=0&size=20`으로 요청하며, `content`와 페이지 정보를 반환합니다. 페이지 번호는 0부터, 크기는 1~100이며 이름·ID 오름차순으로 정렬합니다.

게임별 참여자·프로필 목록은 `/api/game-profiles?game=LOL&page=0&size=20`으로 조회합니다. game은 필수(`LOL`·`OVERWATCH`)이며 keyword(이름 부분 검색)와 position(선호 포지션)으로 필터링할 수 있습니다. 해당 게임 프로필이 있는 활성 참여자만 반환하고, 목록에 이름·계정·적용 점수·선호 포지션을 포함합니다. 같은 페이지 규칙을 사용하며 프론트 연동은 후속 작업입니다.
API 문서는 [Swagger UI](http://127.0.0.1:8080/swagger-ui.html)에서 확인할 수 있습니다.

실행 종료는 해당 터미널에서 `Ctrl+C`를 누릅니다.

## 테스트와 빌드

백엔드 테스트와 JAR 빌드:

```powershell
.\gradlew.bat test bootJar
```

프론트엔드 빌드:

```powershell
cd frontend
npm.cmd run build
```

결과물은 각각 `build/libs/`, `frontend/dist/`에 생성됩니다. 프론트 빌드 결과는 아직 백엔드 JAR에 포함되지 않습니다.

## 테스트 커버리지

백엔드 테스트 실행 시 JaCoCo 보고서가 `build/reports/jacoco/test/`에 생성됩니다.
`html/index.html`은 로컬 확인용이며, `jacocoTestReport.xml`은 Codecov 업로드용입니다.

GitHub Actions는 `dev`·`main` 대상 PR과 해당 브랜치의 push에서 테스트·JAR 빌드·커버리지 업로드를 실행합니다.
Codecov에서 이 GitHub 저장소를 연결해야 하며, 업로드에는 OIDC 인증을 사용하므로 `CODECOV_TOKEN`은 필요하지 않습니다.
포크 PR은 테스트와 보고서 생성만 실행하고 업로드는 생략합니다.
커버리지는 초기에는 참고 지표로 사용하며, 수치에 따른 병합 제한은 두지 않습니다. 업로드 오류는 CI 실패로 처리합니다.

## 문서

- [백엔드 구조](docs/BACKEND_STRUCTURE.md)
