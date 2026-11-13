/**
 * 데모(VITE_API=mock)용 근무유형 예시. 회사가 정하는 소정근로 예시일 뿐 법정 기준값이 아니다.
 * 법정 기준 검사는 서버가 legal_rate 로 한다.
 */
import type { WorkPattern, WorkPatternInput } from '../api/workPatterns';

const p = (patternId: number, i: WorkPatternInput, assigned: boolean): WorkPattern => {
  let span = i.workEndMin - i.workStartMin;
  if (span <= 0) span += 24 * 60;
  const daily = span - i.breakMinutes;
  const workDays = [...i.dayTypes].filter((c) => c === 'W').length;
  return { ...i, patternId, dailyContractMinutes: daily, weeklyContractMinutes: daily * workDays, assigned };
};

export const WORK_PATTERNS: WorkPattern[] = [
  p(1, { patternCode: 'DAY', patternName: '주간 근무', workStartMin: 540, workEndMin: 1080, breakStartMin: 720, breakMinutes: 60, dayTypes: 'WWWWWOH', active: true, note: null }, true),
  p(2, { patternCode: 'PART_AM', patternName: '오전 단시간', workStartMin: 540, workEndMin: 780, breakStartMin: null, breakMinutes: 0, dayTypes: 'WWWWWOH', active: true, note: '단시간근로자용 예시' }, false),
];
