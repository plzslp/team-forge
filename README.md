# TeamForge

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

프론트 화면은 별도로 실행합니다. 아직 API가 없어 백엔드 루트 주소에서는 화면이 표시되지 않습니다.

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
