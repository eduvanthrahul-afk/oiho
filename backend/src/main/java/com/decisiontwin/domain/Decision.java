package com.decisiontwin.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="decisions")
public class Decision {
    @Id @GeneratedValue(strategy=GenerationType.UUID) public UUID id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="company_id",nullable=false) public Company company;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="created_by",nullable=false) public UserAccount createdBy;
    @Column(nullable=false,length=160) public String title;
    @Column(name="product_description",nullable=false,columnDefinition="text") public String productDescription="";
    @Column(name="target_market",nullable=false,length=240) public String targetMarket="";
    @Column(name="target_customer",nullable=false,length=240) public String targetCustomer="";
    @Column(nullable=false,length=160) public String location="";
    @Column(name="market_size",nullable=false) public int marketSize;
    @Column(name="current_price",nullable=false,precision=12,scale=2) public BigDecimal currentPrice=BigDecimal.ZERO;
    @Column(name="customer_count",nullable=false) public int customerCount=300;
    @Column(name="scenarios_json",nullable=false,columnDefinition="text") public String scenariosJson;
    @Column(name="latest_simulation_id") public UUID latestSimulationId;
    @Column(length=40) public String recommendation;
    @Column(name="created_at",nullable=false) public Instant createdAt;
    @Column(name="updated_at",nullable=false) public Instant updatedAt;
    public Decision() {}
    public UUID getId(){return id;}
}
