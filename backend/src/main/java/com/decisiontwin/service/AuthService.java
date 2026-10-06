package com.decisiontwin.service;

import com.decisiontwin.api.ApiDtos.*;
import com.decisiontwin.domain.*;
import com.decisiontwin.repository.*;
import com.decisiontwin.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;

@Service
public class AuthService {
  private final UserRepository users; private final CompanyRepository companies; private final RefreshTokenRepository tokens;
  private final PasswordEncoder passwords; private final JwtService jwt; private final long refreshDays;
  public AuthService(UserRepository users,CompanyRepository companies,RefreshTokenRepository tokens,PasswordEncoder passwords,JwtService jwt,@Value("${app.refresh-ttl-days}") long refreshDays){this.users=users;this.companies=companies;this.tokens=tokens;this.passwords=passwords;this.jwt=jwt;this.refreshDays=refreshDays;}
  @Transactional public AuthResponse register(RegisterRequest input){
    String email=input.email().trim().toLowerCase(Locale.ROOT);
    if(users.findByEmailIgnoreCase(email).isPresent())throw new ResponseStatusException(HttpStatus.CONFLICT,"An account with this email already exists.");
    String companyName=input.name().trim().split("\\s+")[0]+"'s workspace";
    Company company=companies.save(new Company(companyName));
    UserAccount user=users.save(new UserAccount(company,input.name().trim(),email,passwords.encode(input.password())));
    return issue(user);
  }
  @Transactional public AuthResponse login(LoginRequest input){
    UserAccount user=users.findByEmailIgnoreCase(input.email().trim()).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Email or password is incorrect."));
    if(!passwords.matches(input.password(),user.passwordHash))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Email or password is incorrect.");
    return issue(user);
  }
  @Transactional public AuthResponse refresh(String raw){
    RefreshToken stored=tokens.findByTokenHash(hash(raw)).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Refresh session is invalid."));
    if(stored.revokedAt!=null||stored.expiresAt.isBefore(Instant.now()))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Refresh session has expired.");
    stored.revokedAt=Instant.now(); return issue(stored.user);
  }
  @Transactional public void logout(String raw){if(raw!=null)tokens.findByTokenHash(hash(raw)).ifPresent(t->t.revokedAt=Instant.now());}
  private AuthResponse issue(UserAccount user){String refresh=UUID.randomUUID()+"."+UUID.randomUUID();tokens.save(new RefreshToken(user,hash(refresh),Instant.now().plusSeconds(refreshDays*86400)));return new AuthResponse(jwt.issueAccess(user),refresh,"Bearer");}
  public UserDto dto(UserAccount user){return new UserDto(user.id,user.name,user.email,user.role,user.company.getId(),user.company.getName());}
  private String hash(String raw){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
