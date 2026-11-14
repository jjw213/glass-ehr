package com.glassehr.employee;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주민등록번호 저장·마스킹 조회·원문 조회. docs/rrn-design.md.
 *
 * <ul>
 *   <li>개인정보 보호법 제24조의2: 주민등록번호는 법령 근거가 있을 때만 처리한다. 저장 목적은 4대보험 신고·원천징수
 *       (처리 근거 개별 조문은 docs/rrn-design.md 6장 미결 — 사람 확인 대기).</li>
 *   <li>개인정보 보호법 제24조의2 제2항·제29조: 암호화해 저장하고 접근을 통제한다. 원문 조회는 인사·급여 담당자만,
 *       조회할 때마다(거부·대상 없음 포함) 로그를 남긴다.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class RrnService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final EmpRrnRepository rrns;
    private final RrnCrypto crypto;
    private final RrnAccessLogger accessLogger;
    private final Clock clock;

    @Autowired
    public RrnService(EmpRrnRepository rrns, RrnCrypto crypto, RrnAccessLogger accessLogger) {
        this(rrns, crypto, accessLogger, Clock.system(SEOUL));
    }

    RrnService(EmpRrnRepository rrns, RrnCrypto crypto, RrnAccessLogger accessLogger, Clock clock) {
        this.rrns = rrns;
        this.crypto = crypto;
        this.accessLogger = accessLogger;
        this.clock = clock.withZone(SEOUL);
    }

    /** 이미 어떤 사원에게 등록된 번호인가(일괄 등록 미리보기용). 누구인지는 돌려주지 않는다. */
    public boolean isRegistered(Rrn rrn) {
        return rrns.existsByRrnHash(crypto.hash(rrn));
    }

    /** 등록하거나 정정한다. 다른 사원에게 같은 번호가 있으면 409(누구인지는 알리지 않음). */
    @Transactional
    public String save(long empId, Rrn rrn, RequestActor actor) {
        byte[] hash = crypto.hash(rrn);
        if (rrns.existsByRrnHashAndEmpIdNot(hash, empId)) {
            throw EmployeeException.conflict("이미 다른 사원에게 등록된 주민등록번호예요");
        }
        RrnCrypto.Sealed sealed = crypto.encrypt(rrn, empId);
        LocalDateTime now = LocalDateTime.now(clock);
        EmpRrn row = rrns.findById(empId).orElse(null);
        if (row == null) {
            row = new EmpRrn(empId, sealed, rrn.masked(), hash, actor.actorId(), now);
        } else {
            row.replace(sealed, rrn.masked(), hash, actor.actorId(), now);
        }
        rrns.saveAndFlush(row);
        return rrn.masked();
    }

    /** 화면 표시용 마스킹 값. 복호화하지 않는다. */
    public Optional<String> masked(long empId) {
        return rrns.findById(empId).map(EmpRrn::getRrnMasked);
    }

    /**
     * 원문 조회. 인사담당·급여담당만 허용하고, 결과와 관계없이 한 건마다 로그 한 행을 남긴다.
     * 로그는 원문을 돌려주기 전에 커밋한다(로그를 못 남기면 원문도 주지 않음).
     */
    public Rrn reveal(long empId, RequestActor actor, RrnAccessLog.Purpose purpose) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (!actor.canRevealRrn()) {
            accessLogger.log(actor, empId, purpose, RrnAccessLog.Result.DENIED, now);
            throw EmployeeException.forbidden("주민등록번호 원문은 인사·급여 담당자만 볼 수 있어요");
        }
        EmpRrn row = rrns.findById(empId).orElse(null);
        if (row == null) {
            accessLogger.log(actor, empId, purpose, RrnAccessLog.Result.NOT_FOUND, now);
            throw new EmployeeException(org.springframework.http.HttpStatus.NOT_FOUND, "등록된 주민등록번호가 없어요");
        }
        Rrn rrn = crypto.decrypt(row.getRrnEnc(), row.getKeyVersion(), empId);
        accessLogger.log(actor, empId, purpose, RrnAccessLog.Result.SUCCESS, now);
        return rrn;
    }
}
