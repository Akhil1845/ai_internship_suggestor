package com.example.internship_ai_backend.repository;

import com.example.internship_ai_backend.entity.Internship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InternshipRepository extends JpaRepository<Internship, Integer> {
    List<Internship> findAll();
}
