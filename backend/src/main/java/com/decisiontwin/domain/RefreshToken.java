package com.decisiontwin.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="refresh_tokens")
public class RefreshToken {
    @Id @GeneratedValue(strategy=GenerationType.UUID) public UUID id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="user_id",nullable=false) public UserAccount user;
    @Column(name="token_hash",nullable=false,unique=true,length=64) public String tokenHash;
    @Column(name="expires_at",nullable=false) public Instant expiresAt;
    @Column(name="revoked_at") public Instant revokedAt;
    @Column(name="created_at",nullable=false) public Instant createdAt;
    protected RefreshToken() {}
    public RefreshToken(UserAccount user,String tokenHash,Instant expiresAt){this.user=user;this.tokenHash=tokenHash;this.expiresAt=expiresAt;this.createdAt=Instant.now();}
}
