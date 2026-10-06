package com.decisiontwin.security;

import com.decisiontwin.domain.UserAccount;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {
  private final SecretKey key;
  private final long accessTtlMinutes;
  public JwtService(@Value("${app.jwt-secret}") String secret, @Value("${app.access-ttl-minutes}") long accessTtlMinutes) {
    byte[] bytes=secret.getBytes(StandardCharsets.UTF_8);
    if(bytes.length<32) throw new IllegalStateException("JWT_SECRET must contain at least 32 bytes");
    this.key=Keys.hmacShaKeyFor(bytes); this.accessTtlMinutes=accessTtlMinutes;
  }
  public String issueAccess(UserAccount user) {
    Instant now=Instant.now();
    return Jwts.builder().subject(user.id.toString()).claim("companyId",user.company.getId().toString()).claim("role",user.role)
        .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(accessTtlMinutes*60))).signWith(key).compact();
  }
  public String subject(String token) { return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject(); }
}
