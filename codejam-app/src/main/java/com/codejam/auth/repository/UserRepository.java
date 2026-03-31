package com.codejam.auth.repository;

import com.codejam.auth.util.AuthProvider;
import com.codejam.auth.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByEmailAndProvider(String email, AuthProvider provider);
}
