package com.glassehr.contract;

import com.glassehr.contract.ContractDtos.AlertRow;
import com.glassehr.contract.ContractDtos.ContractRequest;
import com.glassehr.contract.ContractDtos.ContractView;
import com.glassehr.contract.ContractDtos.DeliveryRequest;
import com.glassehr.contract.ContractDtos.UndeliveredRow;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 근로계약 API. 등록·수정 응답의 {@code missingTerms}가 명시사항 누락 경고다(비어 있으면 누락 없음).
 * <ul>
 *   <li>GET  /api/employees/{empId}/contracts — 사원의 계약 이력(시작일 순)</li>
 *   <li>POST /api/employees/{empId}/contracts — 계약 등록</li>
 *   <li>GET  /api/contracts/{contractId} — 계약 1건</li>
 *   <li>PUT  /api/contracts/{contractId} — 계약 수정(서면 교부 전만)</li>
 *   <li>POST /api/contracts/{contractId}/delivery — 서면 교부일 기록</li>
 *   <li>GET  /api/contracts/undelivered — 서면 미교부 계약 목록</li>
 *   <li>GET  /api/contracts/alerts?asOf=YYYY-MM-DD — 계약 만료·기간제 사용기간 알림</li>
 * </ul>
 * 계약을 지우는 API는 두지 않는다(근로기준법 제42조 보존).
 */
@RestController
public class ContractController {

    private final ContractService service;

    public ContractController(ContractService service) {
        this.service = service;
    }

    @GetMapping("/api/employees/{empId}/contracts")
    public List<ContractView> list(@PathVariable long empId) {
        return service.list(empId);
    }

    @PostMapping("/api/employees/{empId}/contracts")
    @ResponseStatus(HttpStatus.CREATED)
    public ContractView create(@PathVariable long empId, @Valid @RequestBody ContractRequest req) {
        return service.create(empId, req);
    }

    @GetMapping("/api/contracts/{contractId}")
    public ContractView get(@PathVariable long contractId) {
        return service.get(contractId);
    }

    @PutMapping("/api/contracts/{contractId}")
    public ContractView update(@PathVariable long contractId, @Valid @RequestBody ContractRequest req) {
        return service.update(contractId, req);
    }

    @PostMapping("/api/contracts/{contractId}/delivery")
    public ContractView recordDelivery(@PathVariable long contractId, @Valid @RequestBody DeliveryRequest req) {
        return service.recordDelivery(contractId, req);
    }

    @GetMapping("/api/contracts/undelivered")
    public List<UndeliveredRow> undelivered() {
        return service.undelivered();
    }

    @GetMapping("/api/contracts/alerts")
    public List<AlertRow> alerts(@RequestParam(name = "asOf", required = false) LocalDate asOf) {
        return service.alerts(asOf);
    }
}
