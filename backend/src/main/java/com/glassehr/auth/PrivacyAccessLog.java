package com.glassehr.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.Immutable;

/**
 * 개인정보 조회·다운로드 로그(추가만). 테이블: V013 privacy_access_log. 원문 값은 남기지 않는다.
 * 개인정보 보호법 제29조·개인정보의 안전성 확보조치 기준(접속기록 보관): 누가(accessor), 언제(accessedAt),
 * 누구의(targetEmpId), 무엇을(item·action), 결과, 접속 IP.
 */
@Entity
@Immutable
@Table(name = "privacy_access_log")
public class PrivacyAccessLog {

    /** 무엇을(V013 ck_privacy_log_item 과 같아야 함). */
    public enum Item {
        /** 인적사항(생년월일·주소·이력·메모) */
        EMP_PERSONAL,
        /** 사원 목록 파일 */
        EMP_LIST,
        /** 주민등록번호 */
        RRN
    }

    /** V013 ck_privacy_log_action 과 같아야 함. */
    public enum Action {
        VIEW,
        DOWNLOAD
    }

    /** V013 ck_privacy_log_result 와 같아야 함. */
    public enum Result {
        SUCCESS,
        DENIED,
        NOT_FOUND
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id", nullable = false, updatable = false)
    private Long logId;

    @Column(name = "accessor_id", nullable = false, updatable = false)
    private String accessorId;

    @Column(name = "accessor_roles", updatable = false)
    private String accessorRoles;

    @Column(name = "target_emp_id", updatable = false)
    private Long targetEmpId;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_code", nullable = false, updatable = false)
    private Item item;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_code", nullable = false, updatable = false)
    private Action action;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, updatable = false)
    private Result result;

    @Column(name = "client_ip", updatable = false)
    private String clientIp;

    @Column(name = "request_path", updatable = false)
    private String requestPath;

    @Column(name = "accessed_at", nullable = false, updatable = false)
    private LocalDateTime accessedAt;

    protected PrivacyAccessLog() {
    }

    PrivacyAccessLog(String accessorId, String accessorRoles, Long targetEmpId, Item item, Action action,
            Result result, String clientIp, String requestPath, LocalDateTime accessedAt) {
        this.accessorId = cut(accessorId, 50);
        this.accessorRoles = cut(accessorRoles, 200);
        this.targetEmpId = targetEmpId;
        this.item = item;
        this.action = action;
        this.result = result;
        this.clientIp = cut(clientIp, 45);
        this.requestPath = cut(requestPath, 200);
        this.accessedAt = accessedAt;
    }

    private static String cut(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) : s;
    }

    public Long getLogId() {
        return logId;
    }

    public String getAccessorId() {
        return accessorId;
    }

    public String getAccessorRoles() {
        return accessorRoles;
    }

    public Long getTargetEmpId() {
        return targetEmpId;
    }

    public Item getItem() {
        return item;
    }

    public Action getAction() {
        return action;
    }

    public Result getResult() {
        return result;
    }

    public String getClientIp() {
        return clientIp;
    }

    public String getRequestPath() {
        return requestPath;
    }

    public LocalDateTime getAccessedAt() {
        return accessedAt;
    }
}
