package com.decisiontwin.api;

import com.decisiontwin.api.ApiDtos.SimulationRequest;
import com.decisiontwin.service.SimulationService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@RestController @RequestMapping("/api")
public class SimulationController {
  private final SimulationService simulations;
  public SimulationController(SimulationService simulations){this.simulations=simulations;}
  @PostMapping("/simulations") public JsonNode run(Authentication auth,@Valid @RequestBody SimulationRequest request){return simulations.start(userId(auth),request.decisionId());}
  @PostMapping("/simulations/{id}/retry") public JsonNode retry(Authentication auth,@PathVariable UUID id){return simulations.retry(userId(auth),id);}
  @GetMapping("/simulations/{id}") public JsonNode get(Authentication auth,@PathVariable UUID id){return simulations.get(userId(auth),id);}
  @GetMapping(path="/simulations/{id}/events",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter events(Authentication auth,@PathVariable UUID id){
    SseEmitter emitter=new SseEmitter(120000L);UUID uid=userId(auth);
    CompletableFuture.runAsync(()->{
      String[] messages={"Simulation started","Virtual customer population generated","Pricing scenarios compared","Results aggregated","Decision report generated"};
      String[] stages={"simulation.started","market.generated","scenario.completed","results.aggregated","simulation.completed"};int stage=-1;
      try{
        long started=System.currentTimeMillis();
        while(true){
          JsonNode status=simulations.get(uid,id);String current=status.path("status").asText();
          if(current.equals("COMPLETED")){emitter.send(SseEmitter.event().name(stages[4]).data(Map.of("stage",stages[4],"message",messages[4],"progress",100)));break;}
          if(current.equals("FAILED")){emitter.send(SseEmitter.event().name("simulation.failed").data(Map.of("stage","simulation.failed","message","Simulation failed","progress",100)));break;}
          int next=Math.min(3,(int)((System.currentTimeMillis()-started)/700));
          if(next!=stage){stage=next;int progress=new int[]{5,28,70,91}[stage];emitter.send(SseEmitter.event().name(stages[stage]).data(Map.of("stage",stages[stage],"message",messages[stage],"progress",progress)));}
          Thread.sleep(300);
        }
        emitter.complete();
      }catch(Exception e){emitter.completeWithError(e);}
    });
    return emitter;
  }
  @GetMapping("/results/{simulationId}") public JsonNode result(Authentication auth,@PathVariable UUID simulationId){return simulations.get(userId(auth),simulationId);}
  private UUID userId(Authentication auth){return UUID.fromString(auth.getName());}
}
