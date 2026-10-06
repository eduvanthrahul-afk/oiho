package com.decisiontwin.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public final class ApiDtos {
  private ApiDtos() {}
  public record RegisterRequest(@NotBlank @Size(max=120) String name, @NotBlank @Email @Size(max=254) String email, @NotBlank @Size(min=8,max=100) String password) {}
  public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
  public record RefreshRequest(@NotBlank String refreshToken) {}
  public record AuthResponse(String accessToken, String refreshToken, String tokenType) {}
  public record UserDto(UUID id, String name, String email, String role, UUID companyId, String companyName) {}
  public record ScenarioInput(@NotBlank @Size(max=80) String label, @NotNull @DecimalMin("1.0") BigDecimal price) {}
  public record DecisionRequest(@NotBlank @Size(max=160) String title, @Size(max=1200) String productDescription,
      @Size(max=240) String targetMarket, @Size(max=240) String targetCustomer, @Size(max=160) String location,
      @Min(100) @Max(10000000) Integer marketSize, @DecimalMin("0.0") BigDecimal currentPrice,
      @Min(100) @Max(500) Integer customerCount, @NotNull @Size(min=2,max=3) List<@Valid ScenarioInput> scenarios) {}
  public record DecisionDto(UUID id, String title, String productDescription, String targetMarket, String targetCustomer,
      String location, int marketSize, BigDecimal currentPrice, int customerCount, List<ScenarioInput> scenarios,
      UUID latestSimulationId, String recommendation, Instant createdAt, Instant updatedAt, int scenarioCount) {}
  public record SimulationRequest(@NotNull UUID decisionId) {}
}
