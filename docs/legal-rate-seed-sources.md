# #7 법령 기준값 확인 기록

확인일: 2026-11-11. 사용자가 에이전트의 직접 조사·입력을 요청했다. 아래는 사람이 검수한 테스트 정답이 아니다.

## 제공 범위

29개 기존 코드에 대한 현행 법령 버전의 기준값이다. 근로기준법은 2026-10-08부터, 남녀고용평등법은 2026-09-18부터 제공한다. 이는 **검증한 데이터 범위의 시작**이며 각 숫자의 최초 시행일이 아니다. 이전 날짜의 값을 임의 소급하지 않는다. 과거 구간 보완 전에는 해당 날짜 재계산이 미등록 예외로 실패한다.

SQL: backend/src/main/resources/db/oracle/seed/legal_rate_hr_current.sql. V001~V004 이후 별도 실행하며 실제 DB에는 아직 적용하지 않았다. SQL*Plus 오류 시 롤백하고 재실행은 중복 오류를 낸다.

| 항목코드 | 값 | 단위 | 근거 | 원문 |
|---|---:|---|---|---|
| WEEKLY_OVERTIME_LIMIT_HOURS | 12 | HOURS | 근로기준법 제53조 제1항 | [원문](https://law.go.kr/LSW/lsInfoP.do?lsiSeq=285279) |
| OVERTIME_PREMIUM_RATE | 50 | PERCENT | 근로기준법 제56조 제1항 | [원문](https://www.law.go.kr/lsLinkCommonInfo.do?chrClsCd=010202&lsJoLnkSeq=1034322805) |
| HOLIDAY_PREMIUM_RATE | 50 | PERCENT | 근로기준법 제56조 제2항 제1호 | [원문](https://www.law.go.kr/lsLinkCommonInfo.do?chrClsCd=010202&lsJoLnkSeq=1034322805) |
| HOLIDAY_EXCESS_PREMIUM_RATE | 100 | PERCENT | 근로기준법 제56조 제2항 제2호 | [원문](https://www.law.go.kr/lsLinkCommonInfo.do?chrClsCd=010202&lsJoLnkSeq=1034322805) |
| HOLIDAY_PREMIUM_THRESHOLD_HOURS | 8 | HOURS | 근로기준법 제56조 제2항 | [원문](https://www.law.go.kr/lsLinkCommonInfo.do?chrClsCd=010202&lsJoLnkSeq=1034322805) |
| NIGHT_PREMIUM_RATE | 50 | PERCENT | 근로기준법 제56조 제3항 | [원문](https://www.law.go.kr/lsLinkCommonInfo.do?chrClsCd=010202&lsJoLnkSeq=1034322805) |
| NIGHT_WORK_START_HOUR | 22 | HOUR_OF_DAY | 근로기준법 제56조 제3항 | [원문](https://www.law.go.kr/lsLinkCommonInfo.do?chrClsCd=010202&lsJoLnkSeq=1034322805) |
| NIGHT_WORK_END_HOUR | 6 | HOUR_OF_DAY | 근로기준법 제56조 제3항 | [원문](https://www.law.go.kr/lsLinkCommonInfo.do?chrClsCd=010202&lsJoLnkSeq=1034322805) |
| ANNUAL_LEAVE_BASE_DAYS | 15 | DAYS | 근로기준법 제60조 제1항 | [원문](https://law.go.kr/LSW/lsLinkCommonInfo.do?lsJoLnkSeq=1012828043) |
| ANNUAL_LEAVE_ATTENDANCE_RATE | 80 | PERCENT | 근로기준법 제60조 제1항 | [원문](https://law.go.kr/LSW/lsLinkCommonInfo.do?lsJoLnkSeq=1012828043) |
| ANNUAL_LEAVE_MONTHLY_DAYS | 1 | DAYS | 근로기준법 제60조 제2항 | [원문](https://law.go.kr/LSW/lsLinkCommonInfo.do?lsJoLnkSeq=1012828043) |
| ANNUAL_LEAVE_INCREMENT_BASE_YEARS | 1 | YEARS | 근로기준법 제60조 제4항 | [원문](https://law.go.kr/LSW/lsLinkCommonInfo.do?lsJoLnkSeq=1012828043) |
| ANNUAL_LEAVE_INCREMENT_CYCLE_YEARS | 2 | YEARS | 근로기준법 제60조 제4항 | [원문](https://law.go.kr/LSW/lsLinkCommonInfo.do?lsJoLnkSeq=1012828043) |
| ANNUAL_LEAVE_INCREMENT_DAYS | 1 | DAYS | 근로기준법 제60조 제4항 | [원문](https://law.go.kr/LSW/lsLinkCommonInfo.do?lsJoLnkSeq=1012828043) |
| ANNUAL_LEAVE_MAX_DAYS | 25 | DAYS | 근로기준법 제60조 제4항 | [원문](https://law.go.kr/LSW/lsLinkCommonInfo.do?lsJoLnkSeq=1012828043) |
| MENSTRUAL_LEAVE_DAYS_PER_MONTH | 1 | DAYS | 근로기준법 제73조 | [원문](https://law.go.kr/lsLinkCommonInfo.do?lsJoLnkSeq=1007688443) |
| MATERNITY_LEAVE_DAYS | 90 | DAYS | 근로기준법 제74조 제1항 | [원문](https://www.law.go.kr/LSW/lsInfoP.do?lsiSeq=285279) |
| MATERNITY_LEAVE_MULTIPLE_DAYS | 120 | DAYS | 근로기준법 제74조 제1항 | [원문](https://www.law.go.kr/LSW/lsInfoP.do?lsiSeq=285279) |
| MATERNITY_LEAVE_POSTNATAL_MIN_DAYS | 45 | DAYS | 근로기준법 제74조 제1항 | [원문](https://www.law.go.kr/LSW/lsInfoP.do?lsiSeq=285279) |
| MATERNITY_LEAVE_POSTNATAL_MIN_MULTIPLE_DAYS | 60 | DAYS | 근로기준법 제74조 제1항 | [원문](https://www.law.go.kr/LSW/lsInfoP.do?lsiSeq=285279) |
| SPOUSE_MATERNITY_LEAVE_DAYS | 20 | DAYS | 남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제18조의2 제1항 | [원문](https://law.go.kr/lsInfoP.do?ancNo=21065&ancYd=20251001&ancYnChk=0&chrClsCd=010202&efGubun=Y&efYd=20251001&lsiSeq=276851&nwJoYnInfo=Y) |
| SPOUSE_MATERNITY_LEAVE_CLAIM_DAYS | 120 | DAYS | 남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제18조의2 | [원문](https://law.go.kr/lsInfoP.do?ancNo=21065&ancYd=20251001&ancYnChk=0&chrClsCd=010202&efGubun=Y&efYd=20251001&lsiSeq=276851&nwJoYnInfo=Y) |
| PARENTAL_LEAVE_MAX_MONTHS | 12 | MONTHS | 남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제19조 | [원문](https://www.law.go.kr/lsLinkCommonInfo.do?lsJoLnkSeq=1034314827) |
| PARENTAL_LEAVE_EXTENSION_MONTHS | 6 | MONTHS | 남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제19조 | [원문](https://www.law.go.kr/lsLinkCommonInfo.do?lsJoLnkSeq=1034314827) |
| CHILDCARE_REDUCED_HOURS_MAX_MONTHS | 12 | MONTHS | 남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제19조의2 | [원문](https://law.go.kr/LSW/lsLawLinkInfo.do?chrClsCd=010202&lsJoLnkSeq=1010788173) |
| CHILDCARE_REDUCED_WEEKLY_HOURS_MIN | 15 | HOURS | 남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제19조의2 | [원문](https://law.go.kr/LSW/lsLawLinkInfo.do?chrClsCd=010202&lsJoLnkSeq=1010788173) |
| CHILDCARE_REDUCED_WEEKLY_HOURS_MAX | 35 | HOURS | 남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제19조의2 | [원문](https://law.go.kr/LSW/lsLawLinkInfo.do?chrClsCd=010202&lsJoLnkSeq=1010788173) |
| FAMILY_CARE_LEAVE_OF_ABSENCE_DAYS_PER_YEAR | 90 | DAYS | 남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제22조의2 | [원문](https://law.go.kr/LSW/lsLawLinkInfo.do?chrClsCd=010202&lsJoLnkSeq=1000446262) |
| FAMILY_CARE_LEAVE_DAYS_PER_YEAR | 10 | DAYS | 남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제22조의2 | [원문](https://law.go.kr/LSW/lsLawLinkInfo.do?chrClsCd=010202&lsJoLnkSeq=1000446262) |

## 값의 의미와 적용 조건

- 가산율은 기본임금을 제외한 추가 퍼센트다. 50을 150으로 저장하지 않는다. 야간은 22시부터 다음날 6시까지다.
- 연차 가산 BASE_YEARS=1은 가산에서 제외하는 최초 근속연수다. 최초 가산 발생 근속연수 3을 이 코드에 넣으면 안 된다. 출근율·근속기간·단시간근로자 적용은 별도 판단한다.
- 육아휴직 12개월은 기본 한도, 6개월은 제19조제2항 각 호 요건 충족 시 추가 기간이다. 모두에게 18개월을 부여하지 않는다.
- CHILDCARE_REDUCED_HOURS_MAX_MONTHS=12는 제19조의2제4항 본문의 기본 기간으로 해석했다. 개인별 총 한도가 아니다. 미사용 기본 육아휴직 기간의 두 배를 더할 수 있다. 추가 육아휴직 6개월을 이 가산에 포함하지 않는다. 이 계산에 필요한 가산배수 코드 추가는 후속 작업이다.
- 가족돌봄휴가 10일은 일반 한도이며 가족돌봄휴직 90일에 포함된다. 재난에 따른 특별 연장 고시 적용은 별도 판단한다.
- 출산전후휴가 90일은 일반 경우다. 미숙아 100일은 기존 29개 코드에 없어 후속 코드 추가가 필요하다. 다태아 120일, 산후 최소 45/60일은 별도 코드다.
- SPOUSE_MATERNITY_LEAVE_CLAIM_DAYS라는 기존 이름에도 불구하고 현행 120일은 출산 후 **사용 기한**이다. 2026-09-18부터는 출산예정일 50일 전부터 사용 가능하다. 해당 시작시점 코드와 명칭 정비는 후속 작업이다.

## 개정 이력과 남은 작업

2025-02-23 시행 법률 제20521호에서 배우자 휴가는 10→20일, 청구 90일→사용 120일로 변경됐다. 육아휴직 조건부 6개월 추가와 육아기 단축 미사용 기간 가산배수 변경도 이때 시행됐다.
출처: https://law.go.kr/LSW/lsRvsDocListP.do?lsId=000130

부칙 제2조는 시행 당시 배우자 휴가 사용 중이거나 종전 청구기한이 남은 근로자에도 적용한다. 제4조는 기존 육아휴직 사용자에도 적용하고, 제5조는 단축 사용 중인 경우 시행 후 남은 육아휴직 기간에 대한 가산을 정한다. 따라서 출산일 하나로 전후 값을 고르는 것만으로는 충분하지 않다.
출처: https://law.go.kr/lsInfoP.do?ancNo=20521&ancYd=20241022&ancYnChk=0&chrClsCd=010202&lsId=000130&lsiSeq=265999&urlMode=lsEfInfoR&viewCls=lsOldAndNew

이 PR은 현행 29개 값 입력이며 #7 전체 완료 선언이 아니다. 남은 작업: 과거 구간별 원문·최초 시행일 대조 및 시드 추가, 위 예외 코드와 적용례 계산 구현, 사람이 검수한 법령 fixture 확보, Oracle 실행 검증. tests/legal-fixtures는 변경하지 않았다.
