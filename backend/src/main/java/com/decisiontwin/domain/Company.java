package com.decisiontwin.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="companies")
public class Company {
    @Id @GeneratedValue(strategy=GenerationType.UUID) public UUID id;
    @Column(nullable=false, length=160) public String name;
    @Column(name="created_at", nullable=false) public Instant createdAt;
    protected Company() {}
    public Company(String name) { this.name=name; this.createdAt=Instant.now(); }
    public UUID getId(){return id;}
    public String getName(){return name;}
}
