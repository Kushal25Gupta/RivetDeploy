package com.rivetdeploy.backend.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.http.SessionCreationPolicy;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;

    @Value("${rivetdeploy.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    public SecurityConfig(CustomOAuth2UserService customOAuth2UserService) {
        this.customOAuth2UserService = customOAuth2UserService;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // Disable CSRF for simplify API calls right now
            .authorizeHttpRequests(a -> a
                .requestMatchers("/", "/error", "/api/webhooks/github", "/ws/**", "/actuator/**").permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(e -> e
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            )
            .oauth2Login(o -> o
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(customOAuth2UserService)
                )
                .defaultSuccessUrl(frontendUrl + "/dashboard", true)
            )
            .logout(l -> l
                .logoutRequestMatcher(new org.springframework.security.web.util.matcher.AntPathRequestMatcher("/logout", "GET"))
                .logoutSuccessUrl(frontendUrl + "/").permitAll()
            );

        return http.build();
    }
}
