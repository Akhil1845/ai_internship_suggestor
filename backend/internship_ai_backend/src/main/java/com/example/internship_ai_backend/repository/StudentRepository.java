package com.example.internship_ai_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.internship_ai_backend.entity.Student;

import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Integer> {

    Optional<Student> findByEmail(String email);

    boolean existsByEmail(String email);
}