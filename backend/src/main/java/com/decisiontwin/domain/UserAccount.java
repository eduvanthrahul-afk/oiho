package com.decisiontwin.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="users")
public class UserAccount {
    @Id @GeneratedValue(strategy=GenerationType.UUID) public UUID id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="company_id", nullable=false) public Company company;
    @Column(nullable=false, length=120) public String name;
    @Column(nullable=false, unique=true, length=254) public String email;
    @Column(name="password_hash", nullable=false, length=100) public String passwordHash;
    @Column(nullable=false, length=20) public String role="OWNER";
    @Column(name="created_at", nullable=false) public Instant createdAt;
    protected UserAccount() {}
    public UserAccount(Company company, String name, String email, String passwordHash) { this.company=company;this.name=name;this.email=email;this.passwordHash=passwordHash;this.createdAt=Instant.now(); }
}
