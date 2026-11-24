import type { EnterCompany } from '../api/types';

/** 로그인 화면에서 마지막으로 고른 회사(#120). 법인 코드만 브라우저에 둔다(개인정보 아님). */
const KEY = 'glassehr.lastEnterCd';

export function readLastEnter(): string | null {
  try { return window.localStorage.getItem(KEY); } catch { return null; }
}

export function saveLastEnter(enterCd: string) {
  try { window.localStorage.setItem(KEY, enterCd); } catch { /* 저장소를 못 쓰는 브라우저 */ }
}

/**
 * 처음 선택할 회사. 법인이 하나면 그 법인(화면은 선택 칸을 숨김), 마지막 선택이 아직 사용 중이면 그 법인,
 * 아니면 아무것도 고르지 않는다(''). 사용 중지된 법인을 기억하고 있어도 고르지 않는다.
 */
export function initialEnter(companies: EnterCompany[], last: string | null): string {
  if (companies.length === 1) return companies[0].enterCd;
  return last && companies.some((c) => c.enterCd === last) ? last : '';
}
