package com.decisiontwin.api;

import com.decisiontwin.api.ApiDtos.*;
import com.decisiontwin.domain.Decision;
import com.decisiontwin.service.DecisionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/decisions")
public class DecisionController {
  private final DecisionService service;
  public DecisionController(DecisionService service){this.service=service;}
  @GetMapping public List<DecisionDto> list(Authentication auth){return service.list(userId(auth));}
  @PostMapping @ResponseStatus(HttpStatus.CREATED) public DecisionDto create(Authentication auth,@Valid @RequestBody DecisionRequest request){return service.create(userId(auth),request);}
  @GetMapping("/{id}") public DecisionDto get(Authentication auth,@PathVariable UUID id){return service.dto(service.get(userId(auth),id));}
  private UUID userId(Authentication auth){return UUID.fromString(auth.getName());}
}
