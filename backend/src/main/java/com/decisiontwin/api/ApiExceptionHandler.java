package com.decisiontwin.api;

import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<Map<String,String>> status(ResponseStatusException ex){return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message",ex.getReason()==null?"Request failed.":ex.getReason()));}
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<Map<String,String>> validation(MethodArgumentNotValidException ex){
    String message=ex.getBindingResult().getFieldErrors().stream().findFirst().map(e->e.getField()+" "+(e.getDefaultMessage()==null?"is invalid":e.getDefaultMessage())).orElse("Request validation failed.");
    return ResponseEntity.badRequest().body(Map.of("message",message));
  }
}
