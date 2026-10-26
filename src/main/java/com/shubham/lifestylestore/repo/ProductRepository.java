package com.shubham.lifestylestore.repo;

import com.shubham.lifestylestore.domain.Product;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProductRepository extends MongoRepository<Product, String> {
}
