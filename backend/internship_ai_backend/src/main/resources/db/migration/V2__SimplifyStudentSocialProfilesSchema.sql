-- Simplify student_social_profiles schema
-- Remove analytics columns - we only fetch live data from APIs, no DB caching needed

ALTER TABLE student_social_profiles 
DROP COLUMN problems_solved,
DROP COLUMN contest_rating,
DROP COLUMN global_rank,
DROP COLUMN days_active,
DROP COLUMN repositories_count,
DROP COLUMN total_commits,
DROP COLUMN followers,
DROP COLUMN connections,
DROP COLUMN posts;
