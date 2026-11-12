import { describe, expect, it } from 'vitest';
import { canRegisterEmployee, canRevealRrn, canViewPersonal, maskAddress, maskBirthDate, shownPersonal } from './privacy';

const emp = { birthDate: '1990-04-04', address: '가상시 예시구 샘플로 4길 40', careerHistory: '예시회사(가상) 3년', rrnMasked: '000000-*******' };

describe('개인정보 표시 권한', () => {
  it('주민등록번호 원문은 인사·급여 담당만 (관리자·일반 직원 불가)', () => {
    expect(canRevealRrn('HR')).toBe(true);
    expect(canRevealRrn('PAYROLL')).toBe(true);
    expect(canRevealRrn('ADMIN')).toBe(false);
    expect(canRevealRrn('EMPLOYEE')).toBe(false);
  });

  it('인적사항은 인사·급여 담당과 관리자만 그대로 본다', () => {
    expect(['HR', 'PAYROLL', 'ADMIN'].every((r) => canViewPersonal(r as 'HR'))).toBe(true);
    expect(canViewPersonal('EMPLOYEE')).toBe(false);
  });

  it('신규 등록은 인사담당·관리자만', () => {
    expect(canRegisterEmployee('HR')).toBe(true);
    expect(canRegisterEmployee('ADMIN')).toBe(true);
    expect(canRegisterEmployee('PAYROLL')).toBe(false);
    expect(canRegisterEmployee('EMPLOYEE')).toBe(false);
  });
});

describe('마스킹', () => {
  it('생년월일은 전부, 주소는 첫 단위만 남긴다', () => {
    expect(maskBirthDate('1990-04-04')).toBe('****-**-**');
    expect(maskBirthDate(null)).toBe('');
    expect(maskAddress('가상시 예시구 샘플로 4길 40')).toBe('가상시 ***');
    expect(maskAddress('  ')).toBe('');
  });

  it('인사담당에게는 주민등록번호도 마스킹 값만 준다', () => {
    expect(shownPersonal('HR', emp)).toEqual({ birthDate: '1990-04-04', address: emp.address, careerHistory: emp.careerHistory, rrn: '000000-*******' });
    expect(shownPersonal('ADMIN', { ...emp, rrnMasked: null }).rrn).toBe('미등록');
  });

  it('일반 직원에게는 생년월일·주소를 가리고 이력·주민등록번호를 숨긴다', () => {
    const p = shownPersonal('EMPLOYEE', emp);
    expect(p).toEqual({ birthDate: '****-**-**', address: '가상시 ***', careerHistory: '비공개', rrn: '비공개' });
    expect(JSON.stringify(p)).not.toContain('000000');
    expect(JSON.stringify(p)).not.toContain('1990');
  });
});
