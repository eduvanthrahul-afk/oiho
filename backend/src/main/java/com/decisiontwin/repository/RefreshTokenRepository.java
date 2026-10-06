package com.decisiontwin.repository;
import com.decisiontwin.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> { Optional<RefreshToken> findByTokenHash(String hash); }
