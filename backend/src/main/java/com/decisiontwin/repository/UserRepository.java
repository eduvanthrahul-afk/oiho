package com.decisiontwin.repository;
import com.decisiontwin.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface UserRepository extends JpaRepository<UserAccount, UUID> { Optional<UserAccount> findByEmailIgnoreCase(String email); }
