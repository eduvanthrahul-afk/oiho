package com.decisiontwin.security;

import org.springframework.context.annotation.*;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
public class SecurityConfig {
  @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder(12);}
  @Bean FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter){FilterRegistrationBean<JwtAuthenticationFilter> registration=new FilterRegistrationBean<>(filter);registration.setEnabled(false);return registration;}
  @Bean FilterRegistrationBean<AuthRateLimitFilter> rateLimitFilterRegistration(AuthRateLimitFilter filter){FilterRegistrationBean<AuthRateLimitFilter> registration=new FilterRegistrationBean<>(filter);registration.setEnabled(false);return registration;}
  @Bean CorsConfigurationSource corsConfigurationSource(){
    CorsConfiguration config=new CorsConfiguration();config.setAllowedOriginPatterns(List.of("http://localhost:*","http://127.0.0.1:*"));
    config.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));config.setAllowedHeaders(List.of("Authorization","Content-Type","Accept","Last-Event-ID"));config.setAllowCredentials(false);
    UrlBasedCorsConfigurationSource source=new UrlBasedCorsConfigurationSource();source.registerCorsConfiguration("/api/**",config);return source;
  }
  @Bean SecurityFilterChain securityFilterChain(HttpSecurity http,JwtAuthenticationFilter jwtFilter,AuthRateLimitFilter rateLimitFilter) throws Exception {
    return http.csrf(csrf->csrf.disable()).cors(Customizer.withDefaults())
        .headers(h->h.frameOptions(f->f.deny()).contentTypeOptions(Customizer.withDefaults()).httpStrictTransportSecurity(Customizer.withDefaults()))
        .exceptionHandling(e->e.authenticationEntryPoint((request,response,exception)->{
          response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
          response.setContentType("application/json");
          response.getWriter().write("{\"message\":\"Sign in again to run this action.\"}");
        }))
        .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(a->a.dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll().requestMatchers("/","/index.html","/css/**","/js/**","/favicon.ico","/error","/actuator/health","/api/auth/**").permitAll().anyRequest().authenticated())
        .addFilterBefore(jwtFilter,UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(rateLimitFilter,JwtAuthenticationFilter.class).build();
  }
}
