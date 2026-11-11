# #7 기준값 시행일 교정

확인일: 2026-11-11. PR 초안의 2026-09-18/2026-10-08 일괄 시작일을 폐기했다. 아래 날짜는 해당 조문·부칙의 시행 또는 규모별 적용일에 근거한다. 법정 기준이 처음 생긴 날을 모두 추적한 것은 아니다. 시드가 제공하는 가장 이른 구간과 전체 법제 연혁은 구별한다.

## 범위와 해석

- 대상은 30인 이상 사업장이다. 사업장 규모 컬럼이 없으므로 규모별 시행이 다른 초기 구간은 공통 적용 가능한 시점부터 제공한다. 연차·생리휴가는 20~49인까지 적용된 2008-07-01부터, 현행 주 단위 연장한도는 5~49인까지 휴일 포함 주 정의가 적용된 2021-07-01부터 제공한다. 더 큰 사업장에 앞서 시행된 구간을 지원한다고 주장하지 않는다.
- 2007-04-11 출산휴가 행은 법률 제8372호의 해당 조문 시행에 근거한 재법전화 시점이다. 90일 제도의 최초 도입일이라는 뜻이 아니다. 그 이전은 미지원이다.
- 제56조 현행 항 구조 및 휴일 8시간 구분은 법률 제15513호 부칙 제1조제5항에 따라 2018-03-20부터다. 연장·야간의 종전 동일 수치도 이 시드에서는 그 이전 구간을 제공하지 않는다. 당시 한시 가산율 특례나 종전 휴일 중복가산을 현재 값으로 역산하지 않는다.
- 가족돌봄휴직은 법률 제11274호에서 300인 미만 시행이 2013-02-02로 늦춰졌다. 300인 이상의 2012-08-02~2013-02-01은 현 스키마에서 미지원이다.
- 배우자 휴가 일수는 단일 확정 일수인 2019-10-01의 10일부터 제공한다. 이전의 3~5일 범위와 유급 3일을 단일 값으로 축약하지 않았다. 청구기한은 2008-06-22의 30일부터 제공한다.
- 육아휴직 추가기간의 2025-02-23 이전 0은 제19조제2항의 1년 한도 및 추가기간 신설 전 상태를 표현한다. 미등록을 0으로 대체한 기본값이 아니다.

## 기간별 입력

종료일은 배타적이다. 값이 바뀌는 날에는 새 행만 조회된다. 단위는 V003 등록값을 유지한다.

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
- 다태아 변경은 2014-07-01 이후 출산 기준이다. 미숙아 별도 100일 코드는 기존 29개에 없어 추가 구현이 필요하다.
- 육아기 단축 기본 12개월은 개인별 총 한도가 아니다. 2019-10-01 전은 육아휴직과 합산 1년, 이후 미사용 육아휴직 1배 가산, 2025-02-23부터 2배 가산이다. 추가 6개월 육아휴직은 그 가산에 포함하지 않는다. 2008년 제도는 임의 허용, 2012-08-02부터 원칙적 허용 의무라는 차이가 있다.
- 배우자 CLAIM_DAYS는 2025-02-23 이전 청구기한, 이후 사용기한이다. 2025년 부칙에 따라 시행 당시 사용 중이거나 종전 청구기한이 남은 사람도 새 규정을 적용한다. 출산일만으로 구간을 고르면 안 된다. 2026-09-18부터 출산예정일 50일 전 사용이 가능하지만 일수 20·기한 120은 그대로여서 동일 수치 행을 새로 만들지 않는다.
- 육아휴직 6개월 연장은 법정 자격 충족자만 적용한다. 가족돌봄휴가 10일은 일반 한도이며 90일 휴직 한도에 포함된다. 재난 특별연장은 별도 고시 판단이 필요하다.

## 실행과 검증

V001~V004를 적용한 Oracle에서 SQL*Plus/SQLcl로 별도 실행한다. 기존 대상 행이 하나라도 있으면 전체 중단하며 임의 삭제·덮어쓰기를 하지 않는다. 따라서 잘못된 초안을 이미 실행한 DB에는 별도 교정 절차가 필요하다. 이번 세션에서는 DB 실행을 하지 않았다.

정적 검증: 29개 코드 및 단위 일치, 기간 역전·중복·겹침 없음, 각 구간 시작일·전날·종료일의 반개구간 조회 검증. 이는 구조 검증이며 사람이 검수한 법령 fixture 테스트가 아니다. Oracle 통합 실행과 적용례 계산 검증은 남아 있다. 보호된 tests/legal-fixtures는 변경하지 않았다.
