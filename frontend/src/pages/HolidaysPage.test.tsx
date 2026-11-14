import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createMockHolidayApi, isIsoDate, parseHolidayCsv, type HolidayApi } from '../api/holidays';
import { canManageHolidays, navFor } from '../nav';
import { HolidayCalendar } from './HolidaysPage';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

/*
 * 날짜·이름은 형식 확인용 가짜 값(2099년, '시험 공휴일')이며 실제 공휴일이 아니다. 실제 공휴일 자료는 사람이 넣는다.
 */
const TODAY = '2099-01-10';
const CSV = '날짜,이름,종류,출처,검증일\n'
  + '2099-03-01,시험 공휴일,법정공휴일,"시험 규정 제1조, https://example.invalid",2099-01-01\n'
  + '2099-03-02,시험 대체공휴일,대체공휴일,시험 규정 제2조,2099-01-01\n';

const flush = () => act(async () => { await new Promise((r) => setTimeout(r, 0)); });
function setValue(el: HTMLInputElement | HTMLSelectElement, value: string) {
  const proto = el instanceof HTMLSelectElement ? HTMLSelectElement.prototype : HTMLInputElement.prototype;
  Object.getOwnPropertyDescriptor(proto, 'value')!.set!.call(el, value);
  el.dispatchEvent(new Event(el instanceof HTMLSelectElement ? 'change' : 'input', { bubbles: true }));
}
const buttonByText = (host: HTMLElement, text: string) =>
  [...host.querySelectorAll('button')].find((b) => b.textContent?.includes(text))!;
const byLabel = <T extends HTMLElement>(host: HTMLElement, label: string) => host.querySelector<T>(`[aria-label="${label}"]`)!;

describe('휴일 달력 메뉴 권한', () => {
  it('달력은 모두 보고, 관리는 인사담당자·관리자만', () => {
    expect(navFor('EMPLOYEE').map((n) => n.id)).toContain('holidays');
    expect(canManageHolidays('HR')).toBe(true);
    expect(canManageHolidays('ADMIN')).toBe(true);
    expect(canManageHolidays('EMPLOYEE')).toBe(false);
  });
});

describe('공휴일 CSV 형식 검사 (백엔드 PublicHolidayCsv 와 같은 규칙)', () => {
  it('따옴표 안 쉼표를 허용하고 종류를 읽는다', () => {
    const rows = parseHolidayCsv('﻿' + CSV);
    expect(Array.isArray(rows) && rows.map((r) => [r.kind, r.source])).toEqual([
      ['PUBLIC', '시험 규정 제1조, https://example.invalid'], ['SUBSTITUTE', '시험 규정 제2조'],
    ]);
  });
  it('출처·검증일이 없거나, 종류가 공휴일이 아니거나, 없는 날짜면 줄 번호와 함께 거부', () => {
    expect(parseHolidayCsv('날짜,이름,종류\n')).toContain('출처');
    expect(parseHolidayCsv('날짜,이름,종류,출처,검증일\n2099-01-01,시험,법정공휴일,,2099-01-01')).toContain('2번째 줄: 출처');
    expect(parseHolidayCsv('날짜,이름,종류,출처,검증일\n2099-01-01,시험,회사휴일,출처,2099-01-01')).toContain('종류');
    expect(parseHolidayCsv('날짜,이름,종류,출처,검증일\n2099-02-29,시험,법정공휴일,출처,2099-01-01')).toContain('날짜');
    expect(parseHolidayCsv('날짜,이름,종류,출처,검증일\n')).toContain('행이 없');
  });
  it('윤일을 날짜로 인정한다', () => {
    expect(isIsoDate('2096-02-29')).toBe(true);
    expect(isIsoDate('2100-02-29')).toBe(false);
  });
});

describe('HolidayCalendar', () => {
  let host: HTMLDivElement;
  let root: Root;
  beforeEach(() => { host = document.createElement('div'); document.body.appendChild(host); root = createRoot(host); });
  afterEach(() => { act(() => root.unmount()); host.remove(); });

  async function mount(api: HolidayApi, canManage = true) {
    const toast = vi.fn();
    await act(async () => { root.render(<HolidayCalendar api={api} toast={toast} canManage={canManage} today={TODAY} />); });
    await flush();
    return toast;
  }

  it('공휴일 자료 없이 시작하고(에이전트가 만들지 않음), 일반 사원에게는 관리 버튼이 없다', async () => {
    const api = createMockHolidayApi(() => TODAY);
    await mount(api, false);
    expect(host.querySelector('[data-testid="year"]')!.textContent).toBe('2099년');
    expect((await api.year(2099)).every((r) => r.kind === 'COMPANY')).toBe(true);
    expect(host.textContent).not.toContain('공휴일 자료 불러오기');
    expect(host.textContent).not.toContain('휴일 대체');
  });

  it('CSV 를 불러온 뒤 서면합의를 등록하고 공휴일을 근로일로 대체한다', async () => {
    const api = createMockHolidayApi(() => TODAY);
    await api.importCsv(CSV);
    const substitute = vi.spyOn(api, 'substitute');
    const toast = await mount(api);
    expect(host.querySelector('[data-date="2099-03-01"]')!.className).toContain('holiday-day--PUBLIC');

    await act(async () => { buttonByText(host, '휴일 대체').click(); });
    // 서면합의 없이 제출하면 막는다
    await act(async () => { setValue(byLabel(host, '대체할 공휴일'), String((await api.year(2099)).find((r) => r.kind === 'PUBLIC')!.holidayId)); });
    await act(async () => { setValue(byLabel(host, '대신 쉴 근로일'), '2099-03-05'); });
    await act(async () => { host.querySelector<HTMLFormElement>('.modal form')!.requestSubmit(); });
    expect(host.querySelector('[role="alert"]')!.textContent).toContain('서면합의');
    expect(substitute).not.toHaveBeenCalled();

    await act(async () => { buttonByText(host, '서면합의 등록').click(); });
    await act(async () => {
      setValue(byLabel(host, '합의서 제목'), '시험 휴일대체 합의');
      setValue(byLabel(host, '체결일'), '2099-01-05');
      setValue(byLabel(host, '효력 시작일'), '2099-01-01');
      setValue(byLabel(host, '합의서 문서'), '문서함 TEST-001');
    });
    await act(async () => { buttonByText(host, '합의 등록').click(); });
    await flush();
    expect(byLabel<HTMLSelectElement>(host, '서면합의').value).toBe('1');

    await act(async () => { host.querySelector<HTMLFormElement>('.modal form')!.requestSubmit(); });
    await flush();
    expect(substitute).toHaveBeenCalledOnce();
    expect(toast).toHaveBeenCalledWith('시험 공휴일을 03.05(목)로 대체했어요');
    expect(host.querySelector('[data-date="2099-03-01"]')!.className).toContain('holiday-day--moved');
    expect(host.querySelector('[data-date="2099-03-05"]')!.className).toContain('holiday-day--REPLACEMENT');
    expect(host.textContent).toContain('근로일로 대체됨');
  });

  it('이미 휴일인 날로는 대체할 수 없다(서버 오류를 보인다)', async () => {
    const api = createMockHolidayApi(() => TODAY);
    await api.importCsv(CSV);
    const a = await api.createAgreement({ title: '합의', repType: 'MAJORITY_REP', signedDate: '2099-01-01', effectiveFrom: '2099-01-01', effectiveTo: null, documentRef: 'DOC', note: null });
    const original = (await api.year(2099)).find((r) => r.kind === 'PUBLIC')!;
    await expect(api.substitute({ originalHolidayId: original.holidayId, substituteDate: '2099-03-02', agreementId: a.agreementId, note: null }))
      .rejects.toThrow('근로일로만');
    await expect(api.substitute({ originalHolidayId: original.holidayId, substituteDate: '2099-03-05', agreementId: null, note: null }))
      .rejects.toThrow('서면합의');
  });

  it('회사휴일을 추가하고 앞으로 올 날만 지울 수 있다', async () => {
    const api = createMockHolidayApi(() => TODAY);
    const toast = await mount(api);
    await act(async () => { buttonByText(host, '회사휴일 추가').click(); });
    await act(async () => {
      setValue(byLabel(host, '회사휴일 날짜'), '2099-01-05');
      setValue(byLabel(host, '회사휴일 이름'), '지난 휴일');
    });
    await act(async () => { host.querySelector<HTMLFormElement>('.modal form')!.requestSubmit(); });
    await flush();
    expect(toast).toHaveBeenCalledWith('회사휴일 지난 휴일을 추가했어요');
    expect(host.querySelector('[aria-label="지난 휴일 삭제"]')).toBeNull();
    expect(host.querySelector('[aria-label="창립기념일(예시) 삭제"]')).not.toBeNull();
  });
});
