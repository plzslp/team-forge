package com.example.teamforge.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** 업무 API 테스트에 로그인한 사용자와 유효한 CSRF 토큰을 제공한다. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@AutoConfigureMockMvc
@WithMockUser
@Import(AuthenticatedMockMvc.DefaultRequestConfig.class)
public @interface AuthenticatedMockMvc {
    @TestConfiguration(proxyBeanMethods = false)
    class DefaultRequestConfig {
        @Bean
        MockMvcBuilderCustomizer authenticatedRequest() {
            return builder -> builder.defaultRequest(get("/").with(csrf()));
        }
    }
}
