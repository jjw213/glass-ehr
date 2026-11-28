import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { LuPlus, LuRefreshCw, LuShieldAlert } from 'react-icons/lu';
import { Badge, Card, CardHead, Modal, PageHead } from '../components/ui';
import {
  COPY_LABELS, enterCompanyAdminApi, validateNewCompany,
  type AdminCompany, type CreateCompanyResult, type EnterCompanyAdminApi, type NewCompany,
} from '../api/enterCompanies';
import { canManageEnterCompanies } from '../nav';
import { useData, useStore } from '../state/store';

/**
 * 시스템관리 > 법인 관리 (#130). 그룹 관리자가 같은 고객사의 법인 목록을 보고, 새 법인을 만들고(원본 법인의 기준 데이터 복사 +
 * 첫 관리자 계정), 법인을 사용 중지·다시 사용한다. 실제 차단은 서버 ENTER_COMPANY_MANAGE 검사가 한다.
 */
export function EnterCompaniesPage() {
  const { me } = useData();
  const { toast } = useStore();
  if (!canManageEnterCompanies(me.permissions)) {
    return (
      <>
        <PageHead title="법인 관리" />
        <Card i={1}>
          <CardHead title="그룹 관리자만 볼 수 있어요" sub="법인 관리는 그룹 관리자 계정으로 해요" right={<LuShieldAlert className="muted" />} />
        </Card>
      </>
    );
  }
  return <EnterCompanyManager api={enterCompanyAdminApi} toast={toast} locked={[me.homeEnterCd, me.enterCd]} />;
}

const errText = (e: unknown, fallback: string) => (e instanceof Error ? e.message : fallback);
const EMPTY: NewCompany = { enterCd: '', enterName: '', bizRegNo: '', sourceEnterCd: '', adminLoginId: '', adminPassword: '' };

/** 화면 본문. 스토어 없이 api·toast 만 받아서 테스트할 수 있다. locked = 사용 중지할 수 없는 법인(로그인·보고 있는 법인). */
export function EnterCompanyManager({ api, toast, locked }: { api: EnterCompanyAdminApi; toast: (text: string) => void; locked: string[] }) {
  const [companies, setCompanies] = useState<AdminCompany[] | null>(null);
  const [reload, setReload] = useState(0);
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState<NewCompany>(EMPTY);
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [result, setResult] = useState<CreateCompanyResult | null>(null);

  const fail = useCallback((e: unknown) => toast(errText(e, '알 수 없는 오류가 발생했어요')), [toast]);

  useEffect(() => {
    let alive = true;
    api.list().then((c) => { if (alive) setCompanies(c); }, fail);
    return () => { alive = false; };
  }, [api, reload, fail]);

  const openNew = () => {
    setForm({ ...EMPTY, sourceEnterCd: companies?.find((c) => c.active)?.enterCd ?? '' });
    setFormError(null);
    setOpen(true);
  };
  const close = useCallback(() => setOpen(false), []);
  const set = (k: keyof NewCompany) => (e: { target: { value: string } }) => setForm((f) => ({ ...f, [k]: e.target.value }));

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const err = validateNewCompany(form);
    if (err) { setFormError(err); return; }
    setSaving(true);
    try {
      const r = await api.create(form);
      setOpen(false);
      setResult(r);
      setReload((n) => n + 1);
      toast(`${r.company.enterName} 법인을 만들었어요`);
    } catch (ex) {
      setFormError(errText(ex, '만들지 못했어요'));
    } finally {
      setSaving(false);
    }
  };

  const toggle = async (c: AdminCompany) => {
    try {
      await api.setActive(c.enterCd, !c.active);
      setReload((n) => n + 1);
      toast(`${c.enterName} 법인을 ${c.active ? '사용 중지' : '다시 사용'}했어요`);
    } catch (ex) {
      fail(ex);
    }
  };

  return (
    <>
      <PageHead
        title="법인 관리"
        sub="같은 고객사의 법인이에요. 새 법인은 원본 법인의 기준 데이터(공통코드·사업장·결재 양식·근무유형·휴일)를 복사해 만들어요"
        right={
          <>
            <button className="glass pill" onClick={() => setReload((n) => n + 1)} aria-label="새로 고침"><LuRefreshCw /> 새로 고침</button>
            <button className="glass pill" onClick={openNew} aria-label="법인 추가" disabled={companies === null}><LuPlus /> 법인 추가</button>
          </>
        }
      />

      {result && (
        <Card i={1}>
          <CardHead title={`${result.company.enterName}(${result.company.enterCd}) 만들기 완료`} sub={`첫 관리자 아이디: ${result.adminLoginId}`} />
          <p className="sm" aria-label="복사 결과">
            {Object.entries(result.copied).map(([k, n]) => `${COPY_LABELS[k] ?? k} ${n}건`).join(' · ')}
          </p>
          {result.skippedTemplates.length > 0 && (
            <p className="badge badge--red" role="status">
              특정 사원을 결재자로 지정한 결재 양식은 복사하지 않았어요: {result.skippedTemplates.join(', ')}
            </p>
          )}
          <p className="muted sm">사원·조직·발령·근태 같은 업무 데이터와 근로자대표 서면합의는 복사하지 않아요.</p>
        </Card>
      )}

      <Card i={2}>
        <CardHead title="법인 목록" sub="사용 중지한 법인은 로그인 화면에서 고를 수 없어요" />
        {companies === null ? (
          <p className="muted code-empty">불러오는 중…</p>
        ) : (
          <table className="code-table" aria-label="법인 목록">
            <thead>
              <tr><th>법인 코드</th><th>이름</th><th className="hide-sm">사업자등록번호</th><th>상태</th><th /></tr>
            </thead>
            <tbody>
              {companies.map((c) => {
                const isLocked = c.active && locked.includes(c.enterCd);
                return (
                  <tr key={c.enterCd}>
                    <td>{c.enterCd}</td>
                    <td>{c.enterName}</td>
                    <td className="hide-sm">{c.bizRegNo}</td>
                    <td>{c.active ? <Badge tone="blue">사용 중</Badge> : <Badge>사용 중지</Badge>}</td>
                    <td className="code-table__actions">
                      <button
                        className="btn btn--ghost"
                        onClick={() => void toggle(c)}
                        disabled={isLocked}
                        title={isLocked ? '로그인한 법인이나 지금 보고 있는 법인은 사용 중지할 수 없어요' : undefined}
                        aria-label={`${c.enterCd} ${c.active ? '사용 중지' : '다시 사용'}`}
                      >
                        {c.active ? '사용 중지' : '다시 사용'}
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </Card>

      <Modal open={open} onClose={close} title="법인 추가">
        <form className="form" onSubmit={(e) => void submit(e)} noValidate>
          <label className="field">
            <span className="field__label">법인 코드 (영문 대문자·숫자 2~10자, 나중에 바꿀 수 없어요)</span>
            <input className="input" aria-label="법인 코드" value={form.enterCd} onChange={set('enterCd')} />
          </label>
          <label className="field">
            <span className="field__label">법인 이름</span>
            <input className="input" aria-label="법인 이름" value={form.enterName} onChange={set('enterName')} />
          </label>
          <label className="field">
            <span className="field__label">사업자등록번호 (숫자 10자리)</span>
            <input className="input" inputMode="numeric" aria-label="사업자등록번호" value={form.bizRegNo} onChange={set('bizRegNo')} />
          </label>
          <label className="field">
            <span className="field__label">기준 데이터를 복사할 원본 법인</span>
            <select className="input" aria-label="원본 법인" value={form.sourceEnterCd} onChange={set('sourceEnterCd')}>
              <option value="">고르세요</option>
              {(companies ?? []).map((c) => <option key={c.enterCd} value={c.enterCd}>{c.enterName} ({c.enterCd})</option>)}
            </select>
          </label>
          <label className="field">
            <span className="field__label">첫 관리자 아이디 (새 법인의 ADMIN)</span>
            <input className="input" aria-label="관리자 아이디" autoComplete="off" value={form.adminLoginId} onChange={set('adminLoginId')} />
          </label>
          <label className="field">
            <span className="field__label">관리자 초기 비밀번호 (8자 이상)</span>
            <input className="input" type="password" aria-label="관리자 초기 비밀번호" autoComplete="new-password" value={form.adminPassword} onChange={set('adminPassword')} />
          </label>
          {formError && <p className="badge badge--red" role="alert">{formError}</p>}
          <div className="form__actions">
            <button type="button" className="btn btn--ghost" onClick={close}>취소</button>
            <button type="submit" className="btn btn--primary" disabled={saving}>{saving ? '만드는 중…' : '만들기'}</button>
          </div>
        </form>
      </Modal>
    </>
  );
}
