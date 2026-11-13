package com.glassehr.common;

import com.glassehr.auth.PublicApi;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 백엔드 기동 확인용 엔드포인트. DB에 접근하지 않는다. 로그인 없이 부를 수 있다. */
@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    @PublicApi
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
