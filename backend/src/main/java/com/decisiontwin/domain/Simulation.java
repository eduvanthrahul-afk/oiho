package com.decisiontwin.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="simulations")
public class Simulation {
    @Id @GeneratedValue(strategy=GenerationType.UUID) public UUID id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="company_id",nullable=false) public Company company;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="decision_id",nullable=false) public Decision decision;
    @Column(nullable=false,length=20) public String status;
    @Column(nullable=false) public int progress=0;
    @Column(name="request_json",nullable=false,columnDefinition="text") public String requestJson;
    @Column(name="result_json",columnDefinition="text") public String resultJson;
    @Column(name="created_at",nullable=false) public Instant createdAt;
    @Column(name="completed_at") public Instant completedAt;
    public Simulation() {}
}
