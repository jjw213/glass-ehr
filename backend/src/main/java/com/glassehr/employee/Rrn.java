package com.glassehr.employee;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 주민등록번호(외국인등록번호 포함) 원문. docs/rrn-design.md.
 *
 * <p>원문이 애플리케이션 로그·예외 메시지에 찍히지 않도록 {@link #toString()}은 마스킹 값을 돌려준다.
 * 원문은 {@link #plain()}으로만 꺼낸다(암호화·원문 조회 응답에서만 씀).
 *
 * <p>형식만 검사한다(숫자 6자리-숫자 7자리). 검증번호·생년월일 일치 검사는 하지 않는다(테스트는 명백한 가짜 값만 씀).
 */
public final class Rrn {

    private static final Pattern FORMAT = Pattern.compile("^[0-9]{6}-?[0-9]{7}$");

    /** 앞 6자리만 보이고 뒷자리 7자리는 모두 가린다(V009 ck_emp_rrn_masked). */
    static final String MASK = "*******";

    private final String value; // 000000-0000000 형식으로 정규화

    private Rrn(String value) {
        this.value = value;
    }

    /** 하이픈은 있어도 없어도 된다. 형식이 틀리면 {@link IllegalArgumentException}(메시지에 값을 넣지 않음). */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static Rrn parse(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("주민등록번호가 비어 있어요");
        }
        String s = raw.strip();
        if (!FORMAT.matcher(s).matches()) {
            throw new IllegalArgumentException("주민등록번호 형식이 올바르지 않아요(숫자 6자리-숫자 7자리)");
        }
        String digits = s.replace("-", "");
        return new Rrn(digits.substring(0, 6) + "-" + digits.substring(6));
    }

    /** 원문. 암호화·HMAC 계산과 원문 조회 응답에서만 쓴다. */
    public String plain() {
        return value;
    }

    /** 화면 표시용 마스킹 값. 예: 000000-*******. */
    public String masked() {
        return value.substring(0, 7) + MASK;
    }

    @Override
    public String toString() {
        return masked();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Rrn r && value.equals(r.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
