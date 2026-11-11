package com.example.teamforge.auth;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.endsWith;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:shared-account-security-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class SharedAccountSecurityTests {
    @Autowired MockMvc mvc;
    @Autowired UserDetailsService sharedAccount;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void registersBcryptHashFromConfiguredPassword() {
        // when
        String storedPassword = sharedAccount.loadUserByUsername("test-user").getPassword();

        // then
        assertTrue(storedPassword.startsWith("{bcrypt}"));
        assertTrue(passwordEncoder.matches("password", storedPassword));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/auth/me", "/api/auth/csrf", "/api/participants", "/api/game-profiles?game=LOL",
            "/api/matches?game=LOL"})
    void unauthenticatedApiRequestsReturn401(String path) throws Exception {
        // when / then
        mvc.perform(get(path)).andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/swagger-ui.html", "/h2-console/"})
    void protectedPagesRedirectToLogin(String path) throws Exception {
        // when / then
        mvc.perform(get(path)).andExpect(status().isFound())
                .andExpect(header().string("Location", endsWith("/login")));
    }

    @Test
    void rejectsWrongPassword() throws Exception {
        // when / then
        login("incorrect").andExpect(redirectedUrl("/login?error")).andExpect(unauthenticated());
    }

    @Test
    void loginUsesSessionAndRequiresCsrfForChangesAndLogout() throws Exception {
        // given: 브라우저의 기본 로그인 폼과 CSRF 쿠키로 세션을 얻는다.
        MvcResult login = login("password").andExpect(redirectedUrl("/swagger-ui.html"))
                .andExpect(authenticated().withUsername("test-user")).andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertNotNull(session);
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("test-user"));
        Cookie token = mvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isNoContent()).andExpect(cookie().exists("XSRF-TOKEN"))
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(token);

        // when / then: 세션만으로는 변경을 허용하지 않으며, 쿠키와 일치하는 헤더가 필요하다.
        String participant = "{\"name\":\"세션 테스트\"}";
        mvc.perform(post("/api/participants").session(session).cookie(token)
                        .contentType(MediaType.APPLICATION_JSON).content(participant))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/participants").session(session).cookie(token)
                        .header("X-XSRF-TOKEN", token.getValue()).contentType(MediaType.APPLICATION_JSON).content(participant))
                .andExpect(status().isCreated());

        // when / then: 로그아웃도 CSRF를 검증하고 세션을 만료시킨다.
        mvc.perform(post("/logout").session(session).cookie(token)).andExpect(status().isForbidden());
        mvc.perform(post("/logout").session(session).cookie(token).header("X-XSRF-TOKEN", token.getValue()))
                .andExpect(redirectedUrl("/login?logout")).andExpect(unauthenticated());
        assertTrue(session.isInvalid());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions login(String password) throws Exception {
        MvcResult form = mvc.perform(get("/login")).andExpect(status().isOk()).andReturn();
        Cookie csrfCookie = form.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(csrfCookie);
        Matcher csrfField = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"")
                .matcher(form.getResponse().getContentAsString());
        assertTrue(csrfField.find(), "로그인 폼에 CSRF 필드가 있어야 한다");
        return mvc.perform(post("/login").cookie(csrfCookie).param("_csrf", csrfField.group(1))
                .param("username", "test-user").param("password", password));
    }
}
