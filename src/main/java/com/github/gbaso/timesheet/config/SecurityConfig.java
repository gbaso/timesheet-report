package com.github.gbaso.timesheet.config;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, OAuth2UserService<OAuth2UserRequest, OAuth2User> userService) throws Exception {
        return http.authorizeRequests(req -> req
                .antMatchers("/", "/login-failure")
                .permitAll()
                .anyRequest()
                .authenticated())
                .oauth2Login(oauth -> oauth
                        .loginPage("/")
                        .redirectionEndpoint(endpoint -> endpoint.baseUri("/login/oauth2/code/*"))
                        .userInfoEndpoint(endpoint -> endpoint.userService(userService))
                        .defaultSuccessUrl("/login-success")
                        .failureUrl("/login-failure"))
                .build();
    }

    @Bean
    OAuth2UserService<OAuth2UserRequest, OAuth2User> userService() {
        return userRequest -> new DefaultJiraUser();
    }

    static class DefaultJiraUser implements OAuth2User {
        @Override
        public String getName() {
            return "jira-user";
        }

        @Override
        public Collection<? extends GrantedAuthority> getAuthorities() {
            return List.of();
        }

        @Override
        public Map<String, Object> getAttributes() {
            return new HashMap<>();
        }
    }

}
