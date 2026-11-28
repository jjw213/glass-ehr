package com.glassehr.auth;

/**
 * 고객사가 정지·해지되었거나 체험이 끝나 남은 세션을 쓸 수 없다(#151, docs/saas-design.md 2.2).
 * AuthInterceptor 가 세션을 끊고 401 과 {@link #MESSAGE} 안내 문구로 답한다.
 */
public class TenantUnavailableException extends RuntimeException {

    /** 로그인·남은 세션 거부 안내 문구(로그인 API 와 같은 문구). */
    public static final String MESSAGE = "회사의 서비스 이용이 정지되어 로그인할 수 없어요. 회사 담당자에게 문의해 주세요";

    public TenantUnavailableException() {
        super(MESSAGE);
    }
}
