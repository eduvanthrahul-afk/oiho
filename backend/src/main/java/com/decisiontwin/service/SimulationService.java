package com.decisiontwin.service;

import com.decisiontwin.api.ApiDtos.ScenarioInput;
import com.decisiontwin.domain.*;
import com.decisiontwin.repository.DecisionRepository;
import com.decisiontwin.repository.SimulationRepository;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;

@Service
public class SimulationService {
  private final SimulationRepository simulations; private final DecisionRepository decisionRows; private final DecisionService decisions; private final ObjectMapper mapper; private final SimulationRunner runner;
  public SimulationService(SimulationRepository simulations,DecisionRepository decisionRows,DecisionService decisions,ObjectMapper mapper,SimulationRunner runner){this.simulations=simulations;this.decisionRows=decisionRows;this.decisions=decisions;this.mapper=mapper;this.runner=runner;}
  @Transactional public JsonNode start(UUID userId,UUID decisionId){
    UserAccount user=decisions.user(userId);Decision decision=decisions.get(userId,decisionId);
    Simulation simulation=new Simulation();simulation.company=user.company;simulation.decision=decision;simulation.status="RUNNING";simulation.createdAt=Instant.now();
    Map<String,Object> body=enginePayload(decision);try{simulation.requestJson=mapper.writeValueAsString(body);}catch(Exception e){throw new IllegalStateException(e);}
    simulation.progress=4;simulations.saveAndFlush(simulation);
    decision.latestSimulationId=simulation.id;decision.recommendation=null;decisionRows.save(decision);
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){runner.execute(simulation.id);}});
    return mapper.createObjectNode().put("simulationId",simulation.id.toString()).put("status","RUNNING").put("progress",4);
  }
  @Transactional public JsonNode retry(UUID userId,UUID failedSimulationId){
    UserAccount user=decisions.user(userId);
    Simulation failed=simulations.findByIdAndCompanyId(failedSimulationId,user.company.getId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Simulation not found."));
    if(!"FAILED".equals(failed.status))throw new ResponseStatusException(HttpStatus.CONFLICT,"Only failed simulations can be retried.");
    Simulation simulation=new Simulation();simulation.company=user.company;simulation.decision=failed.decision;
    simulation.requestJson=failed.requestJson;simulation.status="RUNNING";simulation.progress=4;simulation.createdAt=Instant.now();
    simulations.saveAndFlush(simulation);
    Decision decision=failed.decision;decision.latestSimulationId=simulation.id;decision.recommendation=null;decisionRows.save(decision);
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){runner.execute(simulation.id);}});
    return mapper.createObjectNode().put("simulationId",simulation.id.toString()).put("status","RUNNING").put("progress",4);
  }
  @Transactional(readOnly=true) public JsonNode get(UUID userId,UUID simulationId){
    Simulation s=simulations.findByIdAndCompanyId(simulationId,decisions.user(userId).company.getId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Simulation not found."));
    if(s.resultJson==null) return mapper.createObjectNode().put("simulationId",s.id.toString()).put("status",s.status).put("progress",s.progress);
    try{JsonNode result=mapper.readTree(s.resultJson);if(result instanceof ObjectNode object){object.put("status",s.status);object.put("progress",s.progress);}return result;}catch(Exception e){throw new IllegalStateException(e);}
  }
  private Map<String,Object> enginePayload(Decision d){
    List<ScenarioInput> scenarios;try{scenarios=mapper.readValue(d.scenariosJson,mapper.getTypeFactory().constructCollectionType(List.class,ScenarioInput.class));}catch(Exception e){throw new IllegalStateException(e);}
    Map<String,Object> body=new LinkedHashMap<>();body.put("decision_id",d.id.toString());body.put("title",d.title);body.put("product_description",d.productDescription);body.put("target_customer",d.targetCustomer);body.put("location",d.location);body.put("market_size",d.marketSize);body.put("current_price",d.currentPrice);body.put("customer_count",d.customerCount);
    body.put("scenarios",scenarios.stream().map(s->Map.of("label",s.label(),"price",s.price())).toList());return body;
  }
}
