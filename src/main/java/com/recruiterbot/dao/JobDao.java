package com.recruiterbot.dao;

import com.recruiterbot.model.Job;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;

/** All job-related SQL. Every statement is handwritten and parameterized. */
@Repository
public class JobDao {

    private final JdbcTemplate jdbcTemplate;

    public JobDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** One query (LEFT JOIN) loads every job with its required skills - no N+1. */
    public List<Job> findAll() {
        String sql = """
                SELECT j.id, j.title, j.min_experience_years, j.culture_keywords, j.tagline, js.skill
                FROM jobs j
                LEFT JOIN job_required_skills js ON js.job_id = j.id
                ORDER BY j.id, js.skill
                """;
        return jdbcTemplate.query(sql, (ResultSetExtractor<List<Job>>) JobDao::extract);
    }

    public Optional<Job> findById(long id) {
        String sql = """
                SELECT j.id, j.title, j.min_experience_years, j.culture_keywords, j.tagline, js.skill
                FROM jobs j
                LEFT JOIN job_required_skills js ON js.job_id = j.id
                WHERE j.id = ?
                ORDER BY js.skill
                """;
        List<Job> result = jdbcTemplate.query(sql, (ResultSetExtractor<List<Job>>) JobDao::extract, id);
        return result.stream().findFirst();
    }

    /** Insert or update by primary key, so repeated ingestion never duplicates a job. */
    public void upsert(Job job) {
        String sql = """
                INSERT INTO jobs (id, title, min_experience_years, culture_keywords, tagline)
                VALUES (?, ?, ?, ?, ?) AS new_row
                ON DUPLICATE KEY UPDATE
                    title = new_row.title,
                    min_experience_years = new_row.min_experience_years,
                    culture_keywords = new_row.culture_keywords,
                    tagline = new_row.tagline
                """;
        jdbcTemplate.update(sql, job.id(), job.title(), job.minExperienceYears(),
                job.cultureKeywords(), job.tagline());
    }

    public void deleteRequiredSkills(long jobId) {
        String sql = """
                DELETE FROM job_required_skills
                WHERE job_id = ?
                """;
        jdbcTemplate.update(sql, jobId);
    }

    public int insertRequiredSkills(long jobId, Collection<String> skills) {
        String sql = """
                INSERT INTO job_required_skills (job_id, skill)
                VALUES (?, ?)
                """;
        List<String> skillList = new ArrayList<>(skills);
        jdbcTemplate.batchUpdate(sql, skillList, skillList.size(), (ps, skill) -> {
            ps.setLong(1, jobId);
            ps.setString(2, skill);
        });
        return skillList.size();
    }

    private static List<Job> extract(ResultSet rs) throws SQLException {
        Map<Long, Job> byId = new LinkedHashMap<>();
        while (rs.next()) {
            long id = rs.getLong("id");
            Job job = byId.get(id);
            if (job == null) {
                job = new Job(id, rs.getString("title"), rs.getInt("min_experience_years"),
                        rs.getString("culture_keywords"), rs.getString("tagline"),
                        new LinkedHashSet<>());
                byId.put(id, job);
            }
            String skill = rs.getString("skill");
            if (skill != null) {
                job.requiredSkills().add(skill);
            }
        }
        return new ArrayList<>(byId.values());
    }
}
