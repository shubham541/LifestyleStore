package com.shubham.lifestylestore.domain;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("users")
public record UserAccount(
        @Id String id,
        String name,
        @Indexed(unique = true) String email,
        String passwordHash,
        String contact,
        String city,
        String address,
        int failedAttempts,
        Instant lockedUntil,
        Instant createdAt) {

    public UserAccount withPasswordHash(String hash) {
        return new UserAccount(id, name, email, hash, contact, city, address, 0, null, createdAt);
    }

    public UserAccount withLoginState(int attempts, Instant lockUntil) {
        return new UserAccount(id, name, email, passwordHash, contact, city, address, attempts, lockUntil, createdAt);
    }
}
