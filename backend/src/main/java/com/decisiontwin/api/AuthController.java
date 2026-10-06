package com.decisiontwin.api;

import com.decisiontwin.api.ApiDtos.*;
import com.decisiontwin.repository.UserRepository;
import com.decisiontwin.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

@RestController @RequestMapping("/api/auth")
public class AuthController {
  private final AuthService auth; private final UserRepository users;
  public AuthController(AuthService auth,UserRepository users){this.auth=auth;this.users=users;}
  @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED) public AuthResponse register(@Valid @RequestBody RegisterRequest r){return auth.register(r);}
  @PostMapping("/login") public AuthResponse login(@Valid @RequestBody LoginRequest r){return auth.login(r);}
  @PostMapping("/refresh") public AuthResponse refresh(@Valid @RequestBody RefreshRequest r){return auth.refresh(r.refreshToken());}
  @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT) public void logout(@RequestBody(required=false) RefreshRequest r){auth.logout(r==null?null:r.refreshToken());}
  @GetMapping("/me") @Transactional(readOnly=true) public UserDto me(Authentication authentication){return auth.dto(users.findById(java.util.UUID.fromString(authentication.getName())).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED)));}
}
