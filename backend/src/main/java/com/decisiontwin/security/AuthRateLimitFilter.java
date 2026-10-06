package com.decisiontwin.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {
  private static final int LIMIT=30;
  private static final long WINDOW_MILLIS=60_000;
  private final ConcurrentHashMap<String,Window> windows=new ConcurrentHashMap<>();
  @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
    String path=request.getRequestURI();
    if(!request.getMethod().equals("POST")||!(path.equals("/api/auth/login")||path.equals("/api/auth/register")||path.equals("/api/auth/refresh"))){chain.doFilter(request,response);return;}
    long now=System.currentTimeMillis();String key=request.getRemoteAddr();Window window=windows.compute(key,(k,current)->current==null||now-current.startedAt>=WINDOW_MILLIS?new Window(now,1):new Window(current.startedAt,current.count+1));
    if(window.count>LIMIT){response.setStatus(429);response.setContentType("application/json");response.setHeader("Retry-After",Long.toString(Math.max(1,(WINDOW_MILLIS-(now-window.startedAt))/1000)));response.getWriter().write("{\"message\":\"Too many authentication attempts. Try again shortly.\"}");return;}
    if(windows.size()>10000)windows.entrySet().removeIf(entry->now-entry.getValue().startedAt>WINDOW_MILLIS*2);
    chain.doFilter(request,response);
  }
  private record Window(long startedAt,int count){}
}
