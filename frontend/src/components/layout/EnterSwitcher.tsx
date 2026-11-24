import { useState } from 'react';
import { LuBuilding2 } from 'react-icons/lu';
import { canSwitchEnter } from '../../api';
import { useData, useStore } from '../../state/store';
import { cx } from '../ui';

/**
 * 현재 법인 이름(#120). 그룹 관리자(ENTER_SWITCH 권한)에게만 법인 전환 목록을 보여 준다.
 * 권한 검사는 서버(POST /api/auth/switch-enter)가 하고, 이 화면은 메뉴 표시만 정한다.
 */
export function EnterSwitcher({ className }: { className?: string }) {
  const { me, companies } = useData();
  const { switchEnter } = useStore();
  const [busy, setBusy] = useState(false);
  const switchable = canSwitchEnter(me.permissions) && companies.length > 1;

  const change = async (enterCd: string) => {
    if (enterCd === me.enterCd || busy) return;
    setBusy(true);
    try { await switchEnter(enterCd); } finally { setBusy(false); }
  };

  return (
    <span className={cx('enter-switch', className)}>
      <LuBuilding2 aria-hidden />
      {switchable ? (
        <select aria-label="법인 전환" value={me.enterCd} disabled={busy} onChange={(e) => void change(e.target.value)}>
          {/* 사용 중 목록에 현재 법인이 없으면(전환 뒤 사용 중지 등) 코드라도 보이게 둔다 */}
          {!companies.some((c) => c.enterCd === me.enterCd) && <option value={me.enterCd}>{me.enterName}</option>}
          {companies.map((c) => <option key={c.enterCd} value={c.enterCd}>{c.enterName}</option>)}
        </select>
      ) : (
        <span aria-label="현재 법인">{me.enterName}</span>
      )}
    </span>
  );
}
