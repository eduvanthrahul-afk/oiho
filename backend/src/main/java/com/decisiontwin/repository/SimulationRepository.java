package com.decisiontwin.repository;
import com.decisiontwin.domain.Simulation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SimulationRepository extends JpaRepository<Simulation, UUID> { Optional<Simulation> findByIdAndCompanyId(UUID id, UUID companyId); }
