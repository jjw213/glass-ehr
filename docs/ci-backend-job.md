# CI backend 잡 (사람이 붙여 넣기)

`.github/**`는 보호된 경로라서 에이전트가 수정하지 않습니다.
아래 블록을 `.github/workflows/ci.yml`의 `jobs:` 아래, 맨 끝 주석
(`# 백엔드 스택이 결정되면 ...`) 자리에 붙여 넣어 주세요. (이슈 #1)

현재 백엔드 테스트(`@WebMvcTest`)는 DB 없이 돌아갑니다.
Oracle이 필요한 통합 테스트가 생기면 아래 주석 처리된 `services:` 블록을 살립니다.

```yaml
  backend:
    runs-on: ubuntu-latest
    if: ${{ hashFiles('backend/pom.xml') != '' }}
    # Oracle 통합 테스트가 생기면 주석 해제
    # services:
    #   oracle:
    #     image: gvenzl/oracle-free:23-slim
    #     env:
    #       ORACLE_PASSWORD: ci-only-password
    #       APP_USER: glass_ehr
    #       APP_USER_PASSWORD: ci-only-password
    #     ports:
    #       - 1521:1521
    #     options: >-
    #       --health-cmd healthcheck.sh
    #       --health-interval 10s
    #       --health-timeout 5s
    #       --health-retries 20
    defaults:
      run:
        working-directory: backend
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21
          cache: maven
      - name: 빌드·테스트
        run: mvn -B verify
        # env:
        #   DB_URL: jdbc:oracle:thin:@//localhost:1521/FREEPDB1
        #   DB_USER: glass_ehr
        #   DB_PASSWORD: ci-only-password
```
