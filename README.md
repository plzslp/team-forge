# TeamForge

[![Codecov](https://codecov.io/gh/plzslp/team-forge/branch/dev/graph/badge.svg)](https://app.codecov.io/gh/plzslp/team-forge/tree/dev)

롤·오버워치 내전 참가자 10명을 실력과 선호 포지션에 따라 5:5로 편성합니다. 백엔드는 참가자와 게임 프로필, 확정 경기와 결과를 관리합니다. 프론트엔드는 백엔드 없이 실행되는 데모입니다.

## 로컬 실행

Windows 기준이며 각 명령은 저장소 루트에서 시작합니다.

**백엔드** · JDK 17, Spring Boot, H2

```powershell
.\gradlew.bat bootRun
```

[Swagger UI](http://127.0.0.1:8080/swagger-ui.html)에서 API를 실행할 수 있습니다.

**프론트엔드** · Node.js, npm, React, TypeScript, Vite

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev -- --port 5173 --strictPort
```

[데모 열기](http://127.0.0.1:5173) · 새로고침하면 데모의 참가자와 편성 기록이 초기화됩니다.

## 검증과 빌드

| 대상 | 명령 | 결과물 |
| --- | --- | --- |
| 백엔드 | `.\gradlew.bat test bootJar` | `build/libs/` |
| 프론트엔드 | `npm.cmd run build` (`frontend/`에서 실행) | `frontend/dist/` |

[백엔드 구조와 API 흐름](docs/BACKEND_STRUCTURE.md)
