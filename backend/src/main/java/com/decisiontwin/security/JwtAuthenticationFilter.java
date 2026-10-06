package com.decisiontwin.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
  private final JwtService jwt;
  public JwtAuthenticationFilter(JwtService jwt){this.jwt=jwt;}
  @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
    String header=request.getHeader("Authorization");
    if(header!=null && header.startsWith("Bearer ")) try {
      String subject=jwt.subject(header.substring(7));
      var auth=new UsernamePasswordAuthenticationToken(subject,null,List.of());
      SecurityContextHolder.getContext().setAuthentication(auth);
    } catch(JwtException|IllegalArgumentException ignored) { SecurityContextHolder.clearContext(); }
    chain.doFilter(request,response);
  }
}
