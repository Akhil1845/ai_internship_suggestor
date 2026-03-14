package com.example.internship_ai_backend.repository;

import com.example.internship_ai_backend.entity.InterviewTopic;
import com.example.internship_ai_backend.entity.InterviewTopic.Category;
import com.example.internship_ai_backend.entity.InterviewTopic.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InterviewTopicRepository extends JpaRepository<InterviewTopic, Integer> {

    List<InterviewTopic> findByCategoryOrderByTimesViewedDesc(Category category);

    List<InterviewTopic> findByCompanyOrderByTimesViewedDesc(Company company);

    List<InterviewTopic> findByCategoryAndCompanyOrderByTimesViewedDesc(Category category, Company company);

    Optional<InterviewTopic> findBySourceUrl(String sourceUrl);

    @Query("SELECT t FROM InterviewTopic t WHERE " +
           "LOWER(t.title)    LIKE LOWER(CONCAT('%', :kw, '%')) OR " +
           "LOWER(t.keywords) LIKE LOWER(CONCAT('%', :kw, '%')) OR " +
           "LOWER(t.content)  LIKE LOWER(CONCAT('%', :kw, '%'))")
    List<InterviewTopic> searchByKeyword(@Param("kw") String keyword);

    @Query("SELECT t FROM InterviewTopic t ORDER BY t.timesViewed DESC")
    List<InterviewTopic> findTopTrending(org.springframework.data.domain.Pageable pageable);

    @Query("SELECT t FROM InterviewTopic t WHERE " +
           "LOWER(t.keywords) LIKE LOWER(CONCAT('%', :kw, '%')) OR " +
           "LOWER(t.title)    LIKE LOWER(CONCAT('%', :kw, '%'))")
    List<InterviewTopic> findByKeywordMatch(@Param("kw") String keyword);

    List<InterviewTopic> findAllByOrderByScrapedAtDesc();
}
