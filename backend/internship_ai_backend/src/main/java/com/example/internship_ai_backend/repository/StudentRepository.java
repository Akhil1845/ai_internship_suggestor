package com.example.internship_ai_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.internship_ai_backend.entity.Student;

import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Integer> {

    Optional<Student> findByEmail(String email);

    Optional<Student> findByUsername(String username);

    Optional<Student> findByGoogleId(String googleId);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);
}