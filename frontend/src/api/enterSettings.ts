/**
 * 법인별 회사 설정 API (#131). 백엔드: backend/ EnterSettingController. 법인은 세션의 현재 법인(요청에 넣지 않는다).
 * 값은 법정 수치가 아니라 회사 운영 설정이다. 법정 기준값(적용 규모 하한 등)은 legal_rate 에 있다.
 */
import { call } from './http';
import { ApiError } from './types';

export interface SettingEntry { effectiveFrom: string; value: number; updatedBy: string; updatedAt: string }
/** value·effectiveFrom = 오늘 적용되는 값(effectiveFrom null = 기본값 사용). entries = 적용시작일별 값(앞으로 적용될 값 포함). */
export interface EnterSetting {
  key: string; label: string; unit: string; min: number; max: number; defaultValue: number;
  value: number; effectiveFrom: string | null; entries: SettingEntry[];
}
/** oldValue null = 그 적용시작일에 새로 등록. */
export interface SettingChange {
  histId: number; key: string; label: string; effectiveFrom: string; oldValue: number | null; newValue: number;
  changedBy: string; changedAt: string;
}
export interface SettingUpdate { effectiveFrom: string; value: number }

export interface EnterSettingApi {
  list(): Promise<EnterSetting[]>;
  update(key: string, input: SettingUpdate): Promise<EnterSetting>;
  history(): Promise<SettingChange[]>;
}

export const httpEnterSettingApi: EnterSettingApi = {
  list: () => call('GET', '/enter-settings'),
  update: (key, input) => call('PUT', `/enter-settings/${encodeURIComponent(key)}`, input),
  history: () => call('GET', '/enter-settings/history'),
};

const DATE = /^\d{4}-\d{2}-\d{2}$/;

/** 입력 검사. 백엔드 EnterSettingService.update 와 같은 규칙(서버도 다시 검사한다). */
export function validateSetting(s: Pick<EnterSetting, 'label' | 'unit' | 'min' | 'max'>, effectiveFrom: string, value: string): string | null {
  if (!DATE.test(effectiveFrom) || Number.isNaN(Date.parse(effectiveFrom))) return '적용 시작일을 넣어 주세요';
  const n = Number(value);
  if (value.trim() === '' || !Number.isInteger(n) || n < s.min || n > s.max) {
    return `${s.label}은(는) ${s.min}~${s.max}${s.unit} 사이 정수로 넣어 주세요`;
  }
  return null;
}

const SEED: Omit<EnterSetting, 'value' | 'effectiveFrom' | 'entries'>[] = [
  { key: 'WORKPLACE_REGULAR_HEADCOUNT', label: '상시근로자 수', unit: '명', min: 1, max: 1_000_000, defaultValue: 30 },
  { key: 'CONTRACT_EXPIRY_ALERT_DAYS', label: '근로계약 만료 알림', unit: '일', min: 0, max: 3650, defaultValue: 30 },
  { key: 'CONTRACT_FIXED_TERM_LIMIT_ALERT_DAYS', label: '기간제 사용기간 상한 알림', unit: '일', min: 0, max: 3650, defaultValue: 30 },
  { key: 'AGREEMENT_EXPIRY_ALERT_DAYS', label: '서면합의서 만료 예정 알림', unit: '일', min: 0, max: 3650, defaultValue: 30 },
  { key: 'WEEKLY_HOURS_WARN_REMAIN_MINUTES', label: '주 52시간 근접 경고 기준', unit: '분', min: 0, max: 10_080, defaultValue: 240 },
];

/** 데모 모드·테스트용 메모리 구현. today 기준으로 오늘 값을 고른다. */
export function createMockEnterSettingApi(today: () => string = () => new Date().toISOString().slice(0, 10)): EnterSettingApi {
  const entries = new Map<string, SettingEntry[]>();
  const changes: SettingChange[] = [];
  let seq = 0;
  const view = (s: (typeof SEED)[number]): EnterSetting => {
    const es = [...(entries.get(s.key) ?? [])].sort((a, b) => a.effectiveFrom.localeCompare(b.effectiveFrom));
    const cur = es.filter((e) => e.effectiveFrom <= today()).pop();
    return { ...s, value: cur?.value ?? s.defaultValue, effectiveFrom: cur?.effectiveFrom ?? null, entries: es };
  };
  return {
    list: async () => SEED.map(view),
    update: async (key, input) => {
      const s = SEED.find((x) => x.key === key);
      if (!s) throw new ApiError(404, `없는 설정이에요: ${key}`);
      const err = validateSetting(s, input.effectiveFrom, String(input.value));
      if (err) throw new ApiError(400, err);
      const es = entries.get(key) ?? [];
      const old = es.find((e) => e.effectiveFrom === input.effectiveFrom);
      const now = new Date().toISOString();
      if (!old || old.value !== input.value) {
        changes.unshift({ histId: ++seq, key, label: s.label, effectiveFrom: input.effectiveFrom, oldValue: old?.value ?? null,
          newValue: input.value, changedBy: 'demo', changedAt: now });
        if (old) Object.assign(old, { value: input.value, updatedBy: 'demo', updatedAt: now });
        else es.push({ effectiveFrom: input.effectiveFrom, value: input.value, updatedBy: 'demo', updatedAt: now });
        entries.set(key, es);
      }
      return view(s);
    },
    history: async () => [...changes],
  };
}

export const enterSettingApi = import.meta.env.VITE_API === 'mock' ? createMockEnterSettingApi() : httpEnterSettingApi;
