/**
 * 공통코드 공통 훅 (#11).
 *
 *   const { codes, nameOf, loading, error } = useCodes(CODE_GROUP.EMPLOYMENT_TYPE);
 *   <select>{codes.map((c) => <option key={c.code} value={c.code}>{c.name}</option>)}</select>
 *   nameOf(emp.employmentType)  // 'REGULAR' → '정규직'
 *
 * - 사용 중인 코드만, 정렬 순서대로 준다(사용 중지 코드는 관리 화면에서만 보인다).
 * - 그룹별로 캐시한다. 같은 틱에 여러 화면이 서로 다른 그룹을 요청해도 그룹마다 한 번만 받아 온다.
 * - 코드 관리 화면에서 고치면 invalidateCodes(group) 로 캐시를 비우고, 그 그룹을 쓰는 훅이 다시 받아 온다.
 * - nameOf 는 사용 중지된 코드(과거 이력이 참조)나 모르는 코드에 대해 코드값을 그대로 돌려준다.
 */
import { useCallback, useEffect, useMemo, useState } from 'react';
import { codeApi, type Code, type CodeApi } from '../api/codes';

type Cache = Map<string, Promise<Code[]>>;
const caches = new WeakMap<CodeApi, Cache>();
const listeners = new Set<(group: string | null) => void>();

const cacheOf = (api: CodeApi): Cache => {
  let c = caches.get(api);
  if (!c) { c = new Map(); caches.set(api, c); }
  return c;
};

/** 여러 그룹을 한 번에 불러온다. 캐시에 없는 그룹만 한 요청으로 받아 온다. 실패한 그룹은 캐시에 남기지 않는다. */
export function loadCodes(groups: string[], api: CodeApi = codeApi): Promise<Record<string, Code[]>> {
  const cache = cacheOf(api);
  const wanted = [...new Set(groups)];
  const missing = wanted.filter((g) => !cache.has(g));
  if (missing.length > 0) {
    const req = api.activeCodes(missing);
    for (const g of missing) {
      const p = req.then((r) => r[g] ?? []);
      cache.set(g, p);
      p.catch(() => { if (cache.get(g) === p) cache.delete(g); });
    }
  }
  return Promise.all(wanted.map((g) => cache.get(g)!)).then((lists) => Object.fromEntries(wanted.map((g, i) => [g, lists[i]])));
}

/** 캐시를 비우고 훅들에게 다시 불러오라고 알린다. group 을 빼면 전체. */
export function invalidateCodes(group?: string, api: CodeApi = codeApi) {
  const cache = cacheOf(api);
  if (group) cache.delete(group); else cache.clear();
  for (const fn of listeners) fn(group ?? null);
}

interface State { key: string; codes: Code[]; error: string | null }

export function useCodes(group: string, api: CodeApi = codeApi) {
  const [version, setVersion] = useState(0);
  const [state, setState] = useState<State>({ key: '', codes: [], error: null });
  const key = `${group}#${version}`;

  useEffect(() => {
    const fn = (g: string | null) => { if (g === null || g === group) setVersion((v) => v + 1); };
    listeners.add(fn);
    return () => { listeners.delete(fn); };
  }, [group]);

  useEffect(() => {
    let alive = true;
    loadCodes([group], api).then(
      (r) => { if (alive) setState({ key, codes: r[group], error: null }); },
      (e: unknown) => { if (alive) setState({ key, codes: [], error: e instanceof Error ? e.message : '코드를 불러오지 못했어요' }); },
    );
    return () => { alive = false; };
  }, [group, key, api]);

  const fresh = state.key === key;
  const codes = useMemo(() => (state.key.startsWith(`${group}#`) ? state.codes : []), [state, group]);
  const names = useMemo(() => new Map(codes.map((c) => [c.code, c.name])), [codes]);
  const nameOf = useCallback((code: string | null | undefined) => (code ? names.get(code) ?? code : ''), [names]);

  return { codes, nameOf, loading: !fresh, error: fresh ? state.error : null };
}
