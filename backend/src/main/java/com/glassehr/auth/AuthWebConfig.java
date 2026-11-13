package com.glassehr.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** /api/** 에 권한 검사를 걸고, 컨트롤러가 {@link AuthUser} 인자를 받을 수 있게 한다. */
@Configuration
public class AuthWebConfig implements WebMvcConfigurer {

    private final AuthUserResolver resolver;
    private final PrivacyAccessLogger privacyLog;

    public AuthWebConfig(AuthUserResolver resolver, PrivacyAccessLogger privacyLog) {
        this.resolver = resolver;
        this.privacyLog = privacyLog;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuthInterceptor(resolver, privacyLog)).addPathPatterns("/api/**");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter p) {
                return p.getParameterType() == AuthUser.class;
            }

            @Override
            public Object resolveArgument(MethodParameter p, ModelAndViewContainer mav, NativeWebRequest web,
                    WebDataBinderFactory binder) {
                Object u = web.getNativeRequest(HttpServletRequest.class).getAttribute(AuthInterceptor.ATTR);
                if (!(u instanceof AuthUser user)) {
                    // 권한 검사를 거치지 않은 경로에서 AuthUser 를 요구하면 설정 오류다. 열어 주지 않는다.
                    throw new IllegalStateException("권한 검사를 거치지 않은 요청입니다");
                }
                return user;
            }
        });
    }
}
