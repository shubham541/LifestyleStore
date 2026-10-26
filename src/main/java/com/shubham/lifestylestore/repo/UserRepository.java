package com.shubham.lifestylestore.repo;

import com.shubham.lifestylestore.domain.UserAccount;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserRepository extends MongoRepository<UserAccount, String> {
    Optional<UserAccount> findByEmail(String email);
}
