package com.example.teamforge.auth.config;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SharedAccountProperties.class)
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService sharedAccount(SharedAccountProperties account, PasswordEncoder passwordEncoder) {
        return new InMemoryUserDetailsManager(User.withUsername(account.username())
                .password(passwordEncoder.encode(account.password()))
                .roles("USER")
                .build());
    }

    @Bean
    SecurityFilterChain applicationSecurity(HttpSecurity http) throws Exception {
        RequestMatcher api = PathPatternRequestMatcher.withDefaults().matcher("/api/**");
        HttpSessionRequestCache pages = new HttpSessionRequestCache();
        pages.setRequestMatcher(request -> "GET".equals(request.getMethod()) && !api.matches(request));

        return http
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/h2-console/**").denyAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), api)
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/login"), AnyRequestMatcher.INSTANCE))
                .requestCache(cache -> cache.requestCache(pages))
                .formLogin(login -> login.defaultSuccessUrl("/swagger-ui.html").permitAll())
                .logout(Customizer.withDefaults())
                .csrf(csrf -> csrf.spa())
                .build();
    }

    @Bean
    @Order(1)
    @Profile("dev & !prod")
    SecurityFilterChain devConsoleSecurity(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/h2-console/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login")))
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .build();
    }
}
