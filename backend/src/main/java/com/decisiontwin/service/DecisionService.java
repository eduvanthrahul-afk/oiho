package com.decisiontwin.service;

import com.decisiontwin.api.ApiDtos.*;
import com.decisiontwin.domain.*;
import com.decisiontwin.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
public class DecisionService {
  private final DecisionRepository decisions; private final UserRepository users; private final ObjectMapper mapper;
  public DecisionService(DecisionRepository decisions,UserRepository users,ObjectMapper mapper){this.decisions=decisions;this.users=users;this.mapper=mapper;}
  @Transactional(readOnly=true) public List<DecisionDto> list(UUID userId){UserAccount user=user(userId);return decisions.findAllByCompanyIdOrderByCreatedAtDesc(user.company.getId()).stream().map(this::dto).toList();}
  @Transactional(readOnly=true) public Decision get(UUID userId,UUID id){return decisions.findByIdAndCompanyId(id,user(userId).company.getId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Decision not found."));}
  @Transactional public DecisionDto create(UUID userId,DecisionRequest request){
    UserAccount user=user(userId);Decision d=new Decision();d.company=user.company;d.createdBy=user;d.title=request.title().trim();
    d.productDescription=orEmpty(request.productDescription());d.targetMarket=orEmpty(request.targetMarket());d.targetCustomer=orEmpty(request.targetCustomer());d.location=orEmpty(request.location());
    d.marketSize=request.marketSize()==null?10000:request.marketSize();d.currentPrice=request.currentPrice()==null?BigDecimal.ZERO:request.currentPrice();d.customerCount=request.customerCount()==null?300:request.customerCount();
    if(request.scenarios().stream().map(s->s.price().stripTrailingZeros()).distinct().count()!=request.scenarios().size())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Each scenario needs a different price.");
    try{d.scenariosJson=mapper.writeValueAsString(request.scenarios());}catch(JsonProcessingException e){throw new IllegalStateException(e);}
    d.createdAt=Instant.now();d.updatedAt=d.createdAt;return dto(decisions.save(d));
  }
  public DecisionDto dto(Decision d){
    try{return new DecisionDto(d.id,d.title,d.productDescription,d.targetMarket,d.targetCustomer,d.location,d.marketSize,d.currentPrice,d.customerCount,mapper.readValue(d.scenariosJson,new TypeReference<List<ScenarioInput>>(){}),d.latestSimulationId,d.recommendation,d.createdAt,d.updatedAt,mapper.readTree(d.scenariosJson).size());}
    catch(Exception e){throw new IllegalStateException("Could not read decision scenarios",e);}
  }
  @Transactional(readOnly=true) public UserAccount user(UUID id){return users.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED));}
  private String orEmpty(String s){return s==null?"":s.trim();}
}
