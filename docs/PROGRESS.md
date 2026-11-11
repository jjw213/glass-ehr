# 진행 기록

작성자 에이전트가 작업을 마칠 때마다 한두 줄씩 추가합니다. 최신 항목이 아래로 갑니다.

- 2026-11-05: 에이전트 협업 설정(AGENTS.md, CLAUDE.md, CI, 이슈 템플릿) 추가
- 2026-11-10: #1 백엔드 스택 결정(Spring Boot 3.5 + Java 21 + JPA/MyBatis, Maven) 기록, `backend/` 뼈대(헬스 엔드포인트+테스트) 추가, CI backend 잡 초안 `docs/ci-backend-job.md` 작성 — ci.yml 반영은 사람이 할 일
- 2026-11-10: #1 리뷰 반영 — `spring.config.import`로 `backend/.env`를 자동 로드(파일 없으면 무시, OS 환경변수 우선)
- 2026-11-10: #2 화면 코드를 `frontend/`로 옮김(`shared/`는 루트 유지, 서버 정적 경로는 `frontend/dist`). ESLint 9·Vitest·샘플 테스트 4개를 추가했고 lint·typecheck·test·build가 모두 통과함
- 2026-11-11: #3 `legal_rate` 설계안 추가 — Oracle 기간 모델/겹침 방지 쓰기 계약, MyBatis 날짜 조회 인터페이스, 경계·무결성 테스트 및 수치 입력 포인트를 `docs/legal-rate-design.md`에 정리. 법정 수치는 미입력.
- 2026-11-11: #3 구현 — Oracle DDL `backend/src/main/resources/db/oracle/V001__legal_rate.sql`, MyBatis `LegalRateMapper` + `LegalRateService.getEffective/findEffective`(반개구간, 0건·겹침은 예외), 경계일 단위 테스트 10개 통과. Oracle 통합 테스트 9개는 `ORACLE_IT=true`일 때만 실행(로컬 미실행). 시드는 TODO 템플릿(`db/oracle/seed/legal_rate_seed.TODO.sql`)만 있고 실제 값은 사람 입력 대기.
- 2026-11-11: #3 요율 저장 방식 결정 반영 — 요율은 퍼센트 숫자(`unit = 'PERCENT'`)로 저장하고, `LegalRateRow.toRatio()`로 비율로 바꾼다(테스트 추가). DECISIONS·설계안·시드 템플릿을 고침. 시드 수치는 사람이 원문 URL과 수치 목록을 주면 작성할 예정.
- 2026-11-11: #8 조직 변경 이력 관리 방식(유효기간 방식) 결정을 DECISIONS에 기록하고, 테이블 구조·변경 유형별 처리·테스트 항목 초안을 `docs/org-history-design.md`에 정리. 코드 변경 없음(구현은 별도 이슈).
