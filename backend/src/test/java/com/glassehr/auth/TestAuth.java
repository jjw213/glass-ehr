package com.glassehr.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * 권한과 무관한 기존 WebMvc 테스트용. {@link TestAuthUsers}에 더해 모든 요청에 기본으로 인사담당(hr1)
 * {@code X-Actor-Id}를 붙인다. 요청에서 직접 X-Actor-Id 를 주면 그 값이 쓰인다.
 * 권한 우회 테스트(AuthBypassTest)는 이 설정을 쓰지 않는다(헤더 없는 요청을 보내야 하므로).
 */
@TestConfiguration
@Import(TestAuthUsers.class)
public class TestAuth {

    @Bean
    MockMvcBuilderCustomizer defaultActorHeader() {
        return builder -> builder.defaultRequest(get("/").header(AuthInterceptor.ACTOR_ID_HEADER, "hr1"));
    }
}
