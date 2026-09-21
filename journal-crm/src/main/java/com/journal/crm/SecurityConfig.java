package com.journal.crm;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.Customizer;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults()) // Enables CORS using the WebMvcConfigurer we just created
            .csrf(csrf -> csrf.disable()) // Disable CSRF for local testing/prototype
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/h2-console/**").permitAll() // Allow access to H2 console
                .anyRequest().authenticated() // Require basic auth for all other requests
            )
            .headers(headers -> headers.frameOptions(frame -> frame.disable())) // Required for H2 console to work in an iframe
            .httpBasic(Customizer.withDefaults()); // Enable Basic Authentication

        return http.build();
    }
}
