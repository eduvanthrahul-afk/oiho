package com.decisiontwin.repository;
import com.decisiontwin.domain.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface CompanyRepository extends JpaRepository<Company, UUID> {}
