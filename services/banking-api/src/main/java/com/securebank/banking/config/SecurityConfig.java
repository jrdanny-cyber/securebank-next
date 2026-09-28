package com.securebank.banking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import jakarta.servlet.DispatcherType;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/api/**"))
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health")
                            .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/accounts")
                            .hasAuthority("SCOPE_accounts:read")
		        .requestMatchers(HttpMethod.POST, "/api/v1/transfers")
                        .hasAuthority("SCOPE_transfers:write")
			.requestMatchers(HttpMethod.GET, "/api/v1/accounts/*/transactions")
                        .hasAuthority("SCOPE_accounts:read")
                        .anyRequest()
                            .denyAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(Customizer.withDefaults()))
                .build();
    }
}
