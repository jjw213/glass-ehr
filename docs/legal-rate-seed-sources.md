# #7 기준값 시행일 교정

확인일: 2026-11-12. PR 초안의 2026-09-18/2026-10-08 일괄 시작일을 폐기했다. 아래 날짜는 해당 조문·부칙의 시행 또는 규모별 적용일에 근거한다. 법정 기준이 처음 생긴 날을 모두 추적한 것은 아니다. 시드가 제공하는 가장 이른 구간과 전체 법제 연혁은 구별한다.

## 범위와 해석

- 대상은 30인 이상 사업장이다. 사업장 규모 컬럼이 없으므로 규모별 시행이 다른 초기 구간은 공통 적용 가능한 시점부터 제공한다. 연차·생리휴가는 20~49인까지 적용된 2008-07-01부터, 현행 주 단위 연장한도는 5~49인까지 휴일 포함 주 정의가 적용된 2021-07-01부터 제공한다. 더 큰 사업장에 앞서 시행된 구간을 지원한다고 주장하지 않는다.
- 2007-04-11 출산휴가 행은 법률 제8372호의 해당 조문 시행에 근거한 재법전화 시점이다. 90일 제도의 최초 도입일이라는 뜻이 아니다. 그 이전은 미지원이다.
- 제56조 현행 항 구조 및 휴일 8시간 구분은 법률 제15513호 부칙 제1조제5항에 따라 2018-03-20부터다. 연장·야간의 종전 동일 수치도 이 시드에서는 그 이전 구간을 제공하지 않는다. 당시 한시 가산율 특례나 종전 휴일 중복가산을 현재 값으로 역산하지 않는다.
- 가족돌봄휴직은 법률 제11274호에서 300인 미만 시행이 2013-02-02로 늦춰졌다. 300인 이상의 2012-08-02~2013-02-01은 현 스키마에서 미지원이다.
- 배우자 휴가 일수는 단일 확정 일수인 2019-10-01의 10일부터 제공한다. 이전의 3~5일 범위와 유급 3일을 단일 값으로 축약하지 않았다. 청구기한은 2008-06-22의 30일부터 제공한다.
- 육아휴직 추가기간의 2025-02-23 이전 0은 제19조제2항의 1년 한도 및 추가기간 신설 전 상태를 표현한다. 미등록을 0으로 대체한 기본값이 아니다.

## 기간별 입력

종료일은 배타적이다. 값이 바뀌는 날에는 새 행만 조회된다. 단위는 V003·V005 등록값을 유지한다.

| 코드 | 시작일 | 종료일(제외) | 값 | 단위 |
|---|---|---|---:|---|
| ANNUAL_LEAVE_ATTENDANCE_RATE | 2008-07-01 | 미정 | 80 | PERCENT |
| ANNUAL_LEAVE_BASE_DAYS | 2008-07-01 | 미정 | 15 | DAYS |
| ANNUAL_LEAVE_INCREMENT_BASE_YEARS | 2008-07-01 | 미정 | 1 | YEARS |
| ANNUAL_LEAVE_INCREMENT_CYCLE_YEARS | 2008-07-01 | 미정 | 2 | YEARS |
| ANNUAL_LEAVE_INCREMENT_DAYS | 2008-07-01 | 미정 | 1 | DAYS |
| ANNUAL_LEAVE_MAX_DAYS | 2008-07-01 | 미정 | 25 | DAYS |
| ANNUAL_LEAVE_MONTHLY_DAYS | 2008-07-01 | 미정 | 1 | DAYS |
| CHILDCARE_REDUCED_HOURS_MAX_MONTHS | 2008-06-22 | 미정 | 12 | MONTHS |
| CHILDCARE_REDUCED_WEEKLY_HOURS_MAX | 2008-06-22 | 2019-10-01 | 30 | HOURS |
| CHILDCARE_REDUCED_WEEKLY_HOURS_MAX | 2019-10-01 | 미정 | 35 | HOURS |
| CHILDCARE_REDUCED_WEEKLY_HOURS_MIN | 2008-06-22 | 미정 | 15 | HOURS |
| FAMILY_CARE_LEAVE_DAYS_PER_YEAR | 2020-01-01 | 미정 | 10 | DAYS |
| FAMILY_CARE_LEAVE_OF_ABSENCE_DAYS_PER_YEAR | 2013-02-02 | 미정 | 90 | DAYS |
| HOLIDAY_EXCESS_PREMIUM_RATE | 2018-03-20 | 미정 | 100 | PERCENT |
| HOLIDAY_PREMIUM_RATE | 2018-03-20 | 미정 | 50 | PERCENT |
| HOLIDAY_PREMIUM_THRESHOLD_HOURS | 2018-03-20 | 미정 | 8 | HOURS |
| MATERNITY_LEAVE_DAYS | 2007-04-11 | 미정 | 90 | DAYS |
| MATERNITY_LEAVE_MULTIPLE_DAYS | 2007-04-11 | 2014-07-01 | 90 | DAYS |
| MATERNITY_LEAVE_MULTIPLE_DAYS | 2014-07-01 | 미정 | 120 | DAYS |
| MATERNITY_LEAVE_POSTNATAL_MIN_DAYS | 2007-04-11 | 미정 | 45 | DAYS |
| MATERNITY_LEAVE_POSTNATAL_MIN_MULTIPLE_DAYS | 2007-04-11 | 2014-07-01 | 45 | DAYS |
| MATERNITY_LEAVE_POSTNATAL_MIN_MULTIPLE_DAYS | 2014-07-01 | 미정 | 60 | DAYS |
| MENSTRUAL_LEAVE_DAYS_PER_MONTH | 2008-07-01 | 미정 | 1 | DAYS |
| NIGHT_PREMIUM_RATE | 2018-03-20 | 미정 | 50 | PERCENT |
| NIGHT_WORK_END_HOUR | 2018-03-20 | 미정 | 6 | HOUR_OF_DAY |
| NIGHT_WORK_START_HOUR | 2018-03-20 | 미정 | 22 | HOUR_OF_DAY |
| OVERTIME_PREMIUM_RATE | 2018-03-20 | 미정 | 50 | PERCENT |
| PARENTAL_LEAVE_EXTENSION_MONTHS | 2008-06-22 | 2025-02-23 | 0 | MONTHS |
| PARENTAL_LEAVE_EXTENSION_MONTHS | 2025-02-23 | 미정 | 6 | MONTHS |
| PARENTAL_LEAVE_MAX_MONTHS | 2008-06-22 | 미정 | 12 | MONTHS |
| SPOUSE_MATERNITY_LEAVE_CLAIM_DAYS | 2008-06-22 | 2019-10-01 | 30 | DAYS |
| SPOUSE_MATERNITY_LEAVE_CLAIM_DAYS | 2019-10-01 | 2025-02-23 | 90 | DAYS |
| SPOUSE_MATERNITY_LEAVE_CLAIM_DAYS | 2025-02-23 | 미정 | 120 | DAYS |
| SPOUSE_MATERNITY_LEAVE_DAYS | 2019-10-01 | 2025-02-23 | 10 | DAYS |
| SPOUSE_MATERNITY_LEAVE_DAYS | 2025-02-23 | 미정 | 20 | DAYS |
| WEEKLY_OVERTIME_LIMIT_HOURS | 2021-07-01 | 미정 | 12 | HOURS |

## 원문 및 시행 근거

- [근로기준법 제정·개정문](https://law.go.kr/LSW/lsRvsDocListP.do?lsId=001872&lsRvsGubun=all): 제8372호 본문·부칙, 제11270호 연차 적용례, 제12325호 다태아 출산휴가와 출산일 적용례.
- [규모별 시행 부칙](https://www.law.go.kr/LSW/lsInfoP.do?docType=JO&joNo=006200&lsiSeq=105168): 제8372호 부칙 제4조제5호, 20~49인 2008-07-01.
- [제15513호 개정문·부칙](https://www.law.go.kr/nwRvsLsInfoR.do?lsiSeq=202870): 제56조 2018-03-20, 주 정의의 규모별 시행.
- [남녀고용평등법 제정·개정문](https://law.go.kr/LSW/lsRvsDocListP.do?chrClsCd=010202&lsId=000130&lsRvsGubun=all): 제8781호 2008-06-22, 제11274호 규모별 시행, 제16558호 2019-10-01 및 가족돌봄휴가 2020-01-01, 제20521호 2025-02-23, 제21476호 2026-09-18. SQL source_url은 수치와 부칙을 함께 볼 수 있는 개정문이다.

## 값만으로 결정할 수 없는 조건

- 연차 월 발생 1일의 대상 확대(2012-08-02), 첫해 사용분 공제 삭제(2018-05-29)는 숫자 변경이 아닌 자격·계산 변경이다. 종전 계산에 현재 로직을 적용하면 안 된다. 가산 BASE_YEARS=1은 최초 가산 근속 3년이 아니라 제외하는 최초 1년이다.
- 다태아 변경은 2014-07-01 이후 출산 기준이다. 미숙아 별도 100일은 2025-02-23 이후 출산에 한해 별도 코드로 조회한다. 다태아 120일과 중복 합산하지 않는다.
- 육아기 단축 기본 12개월은 개인별 총 한도가 아니다. 2019-10-01 전은 육아휴직과 합산 1년, 이후 미사용 육아휴직 1배 가산, 2025-02-23부터 2배 가산이다. 추가 6개월 육아휴직은 그 가산에 포함하지 않는다. 2008년 제도는 임의 허용, 2012-08-02부터 원칙적 허용 의무라는 차이가 있다.
- 배우자 CLAIM_DAYS는 2025-02-23 이전 청구기한, 이후 사용기한이다. 2025년 부칙에 따라 시행 당시 사용 중이거나 종전 청구기한이 남은 사람도 새 규정을 적용한다. 출산일만으로 구간을 고르면 안 된다. 2026-09-18부터 출산예정일 50일 전 사용이 가능하지만 일수 20·기한 120은 그대로여서 동일 수치 행을 새로 만들지 않는다.
- 육아휴직 6개월 연장은 법정 자격 충족자만 적용한다. 가족돌봄휴가 10일은 일반 한도이며 90일 휴직 한도에 포함된다. 재난 특별연장은 별도 고시 판단이 필요하다.

## 실행과 검증

V001~V005를 적용한 Oracle에서 SQL*Plus/SQLcl로 별도 실행한다. 기존 대상 행이 하나라도 있으면 전체 중단하며 임의 삭제·덮어쓰기를 하지 않는다. 따라서 잘못된 초안을 이미 실행한 DB에는 별도 교정 절차가 필요하다. 이번 세션에서는 DB 실행을 하지 않았다.

정적 검증: 47개 코드 및 단위 일치, 기간 역전·중복·겹침 없음, 각 구간 시작일·전날·종료일의 반개구간 조회 검증. 이는 구조 검증이며 사람이 검수한 법령 fixture 테스트가 아니다. Oracle 통합 실행과 적용례 계산 검증은 남아 있다. 보호된 tests/legal-fixtures는 변경하지 않았다.

## 2026-11-12 추가 18개 항목의 연혁

등록된 코드는 총 47개, 시드 행은 총 59개다. 기존 29개 항목의 기간은 그대로 유지했다.

| 코드 | 시작일 | 종료일(제외) | 값 | 단위 |
|---|---|---|---:|---|
| MATERNITY_LEAVE_PREMATURE_DAYS | 2025-02-23 | 미정 | 100 | DAYS |
| MISCARRIAGE_LEAVE_TIER1_DAYS | 2012-08-02 | 2025-02-23 | 5 | DAYS |
| MISCARRIAGE_LEAVE_TIER1_DAYS | 2025-02-23 | 미정 | 10 | DAYS |
| MISCARRIAGE_LEAVE_TIER1_FROM_WEEKS | 2012-08-02 | 미정 | 0 | WEEKS |
| MISCARRIAGE_LEAVE_TIER2_DAYS | 2012-08-02 | 2025-02-23 | 10 | DAYS |
| MISCARRIAGE_LEAVE_TIER2_FROM_WEEKS | 2012-08-02 | 2025-02-23 | 12 | WEEKS |
| MISCARRIAGE_LEAVE_TIER3_DAYS | 2012-08-02 | 미정 | 30 | DAYS |
| MISCARRIAGE_LEAVE_TIER3_FROM_WEEKS | 2012-08-02 | 미정 | 16 | WEEKS |
| MISCARRIAGE_LEAVE_TIER4_DAYS | 2012-08-02 | 미정 | 60 | DAYS |
| MISCARRIAGE_LEAVE_TIER4_FROM_WEEKS | 2012-08-02 | 미정 | 22 | WEEKS |
| MISCARRIAGE_LEAVE_TIER5_DAYS | 2012-08-02 | 미정 | 90 | DAYS |
| MISCARRIAGE_LEAVE_TIER5_FROM_WEEKS | 2012-08-02 | 미정 | 28 | WEEKS |
| PARENTAL_LEAVE_SPLIT_COUNT | 2019-10-01 | 2020-12-08 | 1 | COUNT |
| PARENTAL_LEAVE_SPLIT_COUNT | 2020-12-08 | 2025-02-23 | 2 | COUNT |
| PARENTAL_LEAVE_SPLIT_COUNT | 2025-02-23 | 미정 | 3 | COUNT |
| PREGNANCY_REDUCED_HOURS_EARLY_WEEKS | 2016-03-25 | 미정 | 12 | WEEKS |
| PREGNANCY_REDUCED_HOURS_LATE_FROM_WEEKS | 2016-03-25 | 2025-02-23 | 36 | WEEKS |
| PREGNANCY_REDUCED_HOURS_LATE_FROM_WEEKS | 2025-02-23 | 미정 | 32 | WEEKS |
| PREGNANCY_REDUCED_HOURS_PER_DAY | 2016-03-25 | 미정 | 2 | HOURS |
| PREGNANCY_REDUCED_SHORT_DAY_TARGET_HOURS | 2016-03-25 | 미정 | 6 | HOURS |
| PREGNANCY_REDUCED_SHORT_DAY_THRESHOLD_HOURS | 2016-03-25 | 미정 | 8 | HOURS |
| SPOUSE_MATERNITY_LEAVE_SPLIT_COUNT | 2019-10-01 | 2025-02-23 | 1 | COUNT |
| SPOUSE_MATERNITY_LEAVE_SPLIT_COUNT | 2025-02-23 | 미정 | 3 | COUNT |

### 원문 확인과 코드 의미

- [근로기준법 개정문·부칙](https://law.go.kr/LSW/lsRvsDocListP.do?lsId=001872&lsRvsGubun=all): 제12527호 제74조제7항 및 부칙 제1조제2호(300명 미만은 공포 후 2년 경과). 사업장 규모별 시행을 구분할 수 없어 임신기 단축은 모든 대상 사업장에 공통 적용되는 2016-03-25부터 제공한다. 300명 이상의 2014-09-25~2016-03-24는 미지원이다. 제20520호 부칙 제1조제2호·제7조에 따라 미숙아 100일은 2025-02-23 이후 출산부터 적용한다. 후기 단축 기준은 같은 날 36주에서 32주로 바뀐다.
- [시행령 개정문·부칙](https://law.go.kr/LSW/lsRvsDocListP.do?chrClsCd=010102&lsId=003058): 제23868호(2012-08-02)에서 11주 이내 5일·12~15주 10일을 신설. 제35276호(2025-02-23)에서 제1호를 15주 이내 10일로 바꾸고 제2호를 삭제. 새 기준은 그날 이후 유산·사산한 경우부터 적용한다. 이 시드는 2012-08-02 이전은 제공하지 않는다.
- TIER 번호는 시행령의 호 번호를 보존한다. 2025-02-23 이전 유효한 TIER는 1·2·3·4·5, 이후는 1·3·4·5다. TIER2의 두 행은 그날 종료한다. TIER1의 상한은 **그 날짜에 유효한 다음 구간**의 시작 전이며, 폐지된 TIER2를 참조하면 안 된다. TIER1_FROM_WEEKS=0은 법령에 0주 제한이 있다는 뜻이 아니라 하한 없는 첫 구간을 비음수 임신기간에서 표현한 값이다. 임신 주수의 일 단위 해석은 별도 검수해야 한다.
- 현재 서비스는 항목별 값 조회만 제공하며 유산·사산휴가 계산기는 아직 없다. 후속 구현은 유효 구간을 명시적으로 구성하고 경계·일수 쌍을 함께 검증해야 한다. 등록된 다섯 쌍 모두를 고정 조회하거나 일반 조회의 NOT_FOUND 예외를 잡아 임의로 건너뛰는 계산은 금지한다. 폐지 구간과 누락 데이터의 구별이 필요하다. 인사 계산 모듈 구현 전 사람 검수 fixture를 준비한다.
- [남녀고용평등법 개정문·부칙](https://law.go.kr/LSW/lsRvsDocListP.do?lsId=000130): 제16558호(2019-10-01) 제18조의2제4항·제19조의4제1항은 각 1회. 제17602호는 공포일 2020-12-08부터 육아휴직을 2회로 확대. 제20521호는 2025-02-23부터 둘 다 3회로 확대했다. 이 시드는 분할 횟수의 2019-10-01 이전은 미지원이다.
- COUNT는 **분할 횟수**이고 사용 구간 수가 아니다(예: 3회 분할이면 최대 4구간). 제17602호 부칙 제2조 및 제20521호 부칙 제2·4조의 기사용자·사용 중인 사람 적용례를 확인해야 하므로 최초 출산일·휴직 시작일만으로 구간을 고정하면 안 된다.
- 임신기 단축의 고위험 임신 전 기간 허용은 제74조제7항 및 시행규칙 제12조의3·별표3 자격 판단이 별도로 필요하다. [시행규칙 개정문](https://law.go.kr/LSW/lsRvsDocListP.do?lsId=006859)의 제436호는 2025-02-23 시행이며 미숙아 정의·서류 제출 절차도 규정한다. 주수·체중·중환자실 입원 조건 등을 이번 18개 코드만으로 결정할 수 없다.
- 육아휴직 분할의 산전 모성보호 예외, 2026-08-20 시행 주 단위 육아휴직 예외, 2026-09-18 시행 고위험 임신 배우자 돌봄 예외는 별도 자격·횟수 집계 로직이 필요하다. 숫자 3만 확인해 모든 사용을 차감하지 않는다.

### 맥미니 실행 순서

1. PR #45 구현이 병합된 `main`에 이 시드 PR을 통합하고 V001~V005와 legal_rate_api 유효 상태를 확인한다. 앱 계정 EXECUTE·SELECT 권한도 확인한다.
2. `python3 scripts/check_legal_rate_seed.py`로 구조를 검사한다. 이 검사는 시드에서 도출한 날짜·단위를 검증하며 법령 정답 검수나 Oracle 실행을 대신하지 않는다.
3. DB 소유자 세션에서 기존 대상 데이터를 확인한 뒤 SQL*Plus/SQLcl로 `@backend/src/main/resources/db/oracle/seed/legal_rate_hr_current.sql`을 실행한다. 기존 값이 있으면 전체 중단한다. 값이 있는 DB의 증분 변경은 이 파일을 고쳐 재실행하지 말고 별도 교정 계획을 세운다.
4. Oracle 연결 환경에서 `ORACLE_IT=true mvn -f backend/pom.xml test`로 기존 통합 테스트를 실행한다. 기존 테스트 통과는 이 법령 시드의 사람 검수 통과를 뜻하지 않는다. 시드의 실제 날짜별 조회·적용례 검증도 따로 진행한다.
5. 신규 시드를 빈 대상 DB에 실행했다면 47개 코드·59행인지 확인한다. 2025-02-22에는 TIER2가 조회되고 2025-02-23에는 NOT_FOUND여야 한다. 이는 의도적인 폐지이며 0일 휴가가 아니다.

Oracle 적용, 실제 날짜별 조회, 사람 검수 법령 fixture는 이번 클라우드 작업에서 실행하지 않았다. 이슈 #7의 완료나 배포 승인을 뜻하지 않는다.

검증 기록(2026-11-12): 구조·경계 146건 통과. 중복 행·가드 누락·단위 불일치·역전 기간을 주입한 4가지 음성 검사도 모두 검출했다. Maven 테스트 재실행은 Java 21 컴파일러 부재로 `release version 21 not supported`에서 실패했으므로 이번 실행의 단위 테스트 통과를 주장하지 않는다.
