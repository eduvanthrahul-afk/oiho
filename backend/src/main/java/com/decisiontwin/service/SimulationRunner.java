package com.decisiontwin.service;

import com.decisiontwin.domain.*;
import com.decisiontwin.repository.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class SimulationRunner {
  private static final Logger log=LoggerFactory.getLogger(SimulationRunner.class);
  private final SimulationRepository simulations; private final DecisionRepository decisions; private final ObjectMapper mapper; private final RestClient engine;
  public SimulationRunner(SimulationRepository simulations,DecisionRepository decisions,ObjectMapper mapper,RestClient.Builder clientBuilder,@Value("${app.simulation-engine-url}") String engineUrl){this.simulations=simulations;this.decisions=decisions;this.mapper=mapper;this.engine=clientBuilder.baseUrl(engineUrl).build();}
  @Async("simulationExecutor") @Transactional(propagation=Propagation.REQUIRES_NEW)
  public void execute(UUID simulationId){
    Simulation simulation=simulations.findById(simulationId).orElse(null);if(simulation==null)return;
    try {
      simulation.progress=12;simulations.flush();
      var enginePayload=mapper.readValue(simulation.requestJson,new TypeReference<java.util.Map<String,Object>>(){});
      JsonNode result=engine.post().uri("/simulate").body(enginePayload).retrieve().body(JsonNode.class);
      if(result==null)throw new IllegalStateException("Simulation engine returned no result.");
      ObjectNode response=mapper.createObjectNode().put("simulationId",simulation.id.toString());response.setAll((ObjectNode)result);
      simulation.status="COMPLETED";simulation.progress=100;simulation.resultJson=mapper.writeValueAsString(response);simulation.completedAt=Instant.now();
      UUID decisionId=simulation.decision.getId();
      int updated=decisions.markSimulationComplete(decisionId,simulation.id,result.path("recommendation").asText("MODIFY"),Instant.now());
      if(updated!=1)throw new IllegalStateException("Could not attach the completed run to its decision.");
    } catch(Exception e){
      log.error("Simulation {} failed while calling or persisting engine output",simulationId,e);
      simulation.status="FAILED";simulation.progress=100;simulation.completedAt=Instant.now();
      String message="Simulation engine could not complete this run.";
      if(e instanceof RestClientResponseException response){
        String detail=response.getResponseBodyAsString();
        if(detail.contains("AI_NOT_CONFIGURED"))message="AI analysis is not configured. Set GROQ_API_KEY for the simulation engine, then create a new run.";
        else if(detail.contains("AI_AUTH_FAILED"))message="The simulation engine could not authenticate with Groq. Check GROQ_API_KEY.";
        else if(detail.contains("AI_RATE_LIMITED"))message="Groq is rate limited or out of API credits. Check the configured API project and retry.";
        else if(detail.contains("AI_PROVIDER_UNAVAILABLE"))message="Groq could not be reached. Check the connection and retry.";
        else if(detail.contains("AI_REQUEST_REJECTED"))message="Groq rejected the simulation request. Check GROQ_MODEL and retry.";
        else if(detail.contains("AI_PROVIDER_ERROR"))message="Groq returned an unexpected error while generating the analysis. Check the configured model and retry.";
        else if(detail.contains("AI_REQUEST_TOO_LARGE"))message="Groq rejected this analysis as too large. The discussion size has been reduced; retry the run.";
        else if(detail.contains("AI_DISCUSSION_INCOMPLETE"))message="Groq could not generate every requested persona exchange. The run was not saved; retry it.";
      }
      simulation.resultJson=mapper.createObjectNode().put("simulationId",simulation.id.toString()).put("status","FAILED").put("message",message).toString();
    }
    simulations.save(simulation);
  }
}
