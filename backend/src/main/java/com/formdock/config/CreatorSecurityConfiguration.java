package com.formdock.config;

import java.util.List;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.CompositeLogoutHandler;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import com.formdock.auth.CreatorAuthenticationProvider;
import com.formdock.web.ApiErrorWriter;
import com.formdock.web.SessionDependencyFailureFilter;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class CreatorSecurityConfiguration {

    static final String SESSION_COOKIE_NAME = "SESSION";

    @Bean
    CookieSerializer sessionCookieSerializer(Environment environment) {
        DefaultCookieSerializer cookieSerializer = new DefaultCookieSerializer();
        cookieSerializer.setCookieName(environment.getProperty(
                "server.servlet.session.cookie.name",
                SESSION_COOKIE_NAME));
        cookieSerializer.setCookiePath(environment.getProperty(
                "server.servlet.session.cookie.path",
                "/"));
        cookieSerializer.setUseHttpOnlyCookie(environment.getProperty(
                "server.servlet.session.cookie.http-only",
                Boolean.class,
                true));
        cookieSerializer.setUseSecureCookie(environment.getProperty(
                "server.servlet.session.cookie.secure",
                Boolean.class,
                true));
        cookieSerializer.setSameSite(environment.getProperty(
                "server.servlet.session.cookie.same-site",
                "Lax"));
        return cookieSerializer;
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        return new HttpSessionCsrfTokenRepository();
    }

    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy(
            CsrfTokenRepository csrfTokenRepository) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(),
                new CsrfAuthenticationStrategy(csrfTokenRepository)));
    }

    @Bean
    AuthenticationManager authenticationManager(
            CreatorAuthenticationProvider creatorAuthenticationProvider) {
        return new ProviderManager(creatorAuthenticationProvider);
    }

    @Bean
    LogoutHandler creatorLogoutHandler(SecurityContextRepository securityContextRepository) {
        SecurityContextLogoutHandler securityContextLogoutHandler =
                new SecurityContextLogoutHandler();
        securityContextLogoutHandler.setSecurityContextRepository(securityContextRepository);
        return new CompositeLogoutHandler(
                securityContextLogoutHandler,
                new CookieClearingLogoutHandler(SESSION_COOKIE_NAME));
    }

    @Bean
    SecurityFilterChain creatorSecurityFilterChain(
            HttpSecurity http,
            SecurityContextRepository securityContextRepository,
            CsrfTokenRepository csrfTokenRepository,
            ApiErrorWriter apiErrorWriter) throws Exception {
        RequestMatcher publicResponsePost = PathPatternRequestMatcher.pathPattern(
                HttpMethod.POST,
                "/api/public/surveys/{slug}/responses");
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/api/auth/csrf",
                                "/api/auth/login")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/public/surveys/{slug}")
                        .permitAll()
                        .requestMatchers(publicResponsePost)
                        .permitAll()
                        .requestMatchers("/api/**")
                        .authenticated()
                        .anyRequest()
                        .denyAll())
                .securityContext(context -> context
                        .securityContextRepository(securityContextRepository)
                        .requireExplicitSave(true))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .ignoringRequestMatchers(publicResponsePost))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                apiErrorWriter.write(
                                        response,
                                        HttpStatus.UNAUTHORIZED,
                                        "AUTH_REQUIRED",
                                        "Authentication is required."))
                        .accessDeniedHandler((request, response, exception) -> {
                            boolean csrfFailure = exception instanceof CsrfException;
                            apiErrorWriter.write(
                                    response,
                                    HttpStatus.FORBIDDEN,
                                    csrfFailure ? "CSRF_INVALID" : "FORBIDDEN",
                                    csrfFailure
                                            ? "CSRF token is invalid or missing."
                                            : "Access is forbidden.");
                        }))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .build();
    }

    @Bean
    FilterRegistrationBean<SessionDependencyFailureFilter> sessionDependencyFailureFilter(
            ApiErrorWriter apiErrorWriter) {
        FilterRegistrationBean<SessionDependencyFailureFilter> registration =
                new FilterRegistrationBean<>(new SessionDependencyFailureFilter(apiErrorWriter));
        registration.setName("sessionDependencyFailureFilter");
        registration.setOrder(SessionRepositoryFilter.DEFAULT_ORDER - 1);
        return registration;
    }
}
