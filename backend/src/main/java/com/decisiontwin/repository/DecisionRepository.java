package com.decisiontwin.repository;
import com.decisiontwin.domain.Decision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface DecisionRepository extends JpaRepository<Decision, UUID> {
  List<Decision> findAllByCompanyIdOrderByCreatedAtDesc(UUID companyId);
  Optional<Decision> findByIdAndCompanyId(UUID id, UUID companyId);
  @Modifying
  @Query("update Decision d set d.latestSimulationId=:simulationId, d.recommendation=:recommendation, d.updatedAt=:updatedAt where d.id=:decisionId")
  int markSimulationComplete(@Param("decisionId") UUID decisionId, @Param("simulationId") UUID simulationId, @Param("recommendation") String recommendation, @Param("updatedAt") java.time.Instant updatedAt);
}
