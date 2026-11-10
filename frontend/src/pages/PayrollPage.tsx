import { useState } from 'react';
import { LuEye, LuEyeOff, LuPrinter, LuInfo } from 'react-icons/lu';
import { Card, CardHead, PageHead, vars, cx, sum, won } from '../components/ui';
import { useData } from '../state/store';

export function PayrollPage() {
  const { payslips: PAYSLIPS } = useData();
  const [idx, setIdx] = useState(0);
  const [hidden, setHidden] = useState(false);
  if (!PAYSLIPS.length) return <><PageHead title="급여명세서" /><Card i={1} className="empty">아직 발급된 급여명세서가 없어요</Card></>;
  const p = PAYSLIPS[idx];
  const gross = sum(p.earnings), ded = sum(p.deductions), net = gross - ded;
  const nets = PAYSLIPS.map((x) => sum(x.earnings) - sum(x.deductions));
  const maxNet = Math.max(...nets);
  const money = (n: number) => (hidden ? '•••••••원' : won(n));

  return (
    <>
      <PageHead
        title="급여명세서"
        sub="월별 지급 내역과 공제 내역을 확인하세요"
        right={
          <>
            <button className="glass pill" onClick={() => setHidden((v) => !v)}>{hidden ? <LuEye /> : <LuEyeOff />} {hidden ? '금액 보기' : '금액 숨기기'}</button>
            <button className="glass pill" onClick={() => { try { window.print(); } catch { /* blocked */ } }}><LuPrinter /> 인쇄</button>
          </>
        }
      />

      <div className="chips rise" style={vars({ i: 1 })}>
        {PAYSLIPS.map((x, i) => (
          <button key={x.month} className={cx('glass pill', i === idx && 'active')} onClick={() => setIdx(i)}>{x.month}</button>
        ))}
      </div>

      <div className="grid grid--pay" key={idx}>
        <Card i={2} className="net-card span-2">
          <p className="muted sm">{p.month} 실지급액 · 지급일 {p.payDate}</p>
          <strong className="net-card__value">{money(net)}</strong>
          <div className="split">
            <span className="split__a" style={{ flex: net }} />
            <span className="split__b" style={{ flex: ded }} />
          </div>
          <div className="net-card__legend">
            <span><i className="lg-hire" />지급 총액 {money(gross)}</span>
            <span><i className="lg-leave" />공제 총액 {money(ded)}</span>
          </div>
        </Card>

        <Card i={3}>
          <CardHead title="최근 5개월" sub="실지급액" />
          <div className="mini-bars">
            {nets.slice().reverse().map((n, i, arr) => {
              const real = arr.length - 1 - i;
              return (
                <button key={real} className={cx('mini-bars__col', real === idx && 'now')} onClick={() => setIdx(real)} style={vars({ i, h: `${(n / maxNet) * 100}%` })}>
                  <span className="mini-bars__bar" />
                  <span className="muted sm">{PAYSLIPS[real].month.replace(/^\d+년 /, '')}</span>
                </button>
              );
            })}
          </div>
        </Card>

        <Card i={4}>
          <CardHead title="지급 내역" sub={money(gross)} />
          <table className="lines"><tbody>
            {p.earnings.map((l) => <tr key={l.label}><td>{l.label}</td><td>{money(l.amount)}</td></tr>)}
          </tbody></table>
        </Card>

        <Card i={5}>
          <CardHead title="공제 내역" sub={money(ded)} />
          <table className="lines"><tbody>
            {p.deductions.map((l) => <tr key={l.label}><td>{l.label}</td><td>-{money(l.amount)}</td></tr>)}
          </tbody></table>
        </Card>

        <Card i={6} className="note-card">
          <LuInfo />
          <p className="sm">샘플 데이터예요. 공제 금액은 요율을 단순 적용한 예시로, 실제 세액과 다를 수 있어요. 실서비스에서는 급여 시스템 API에서 받아온 값을 그대로 보여주면 돼요.</p>
        </Card>
      </div>
    </>
  );
}
