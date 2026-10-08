package com.recruiterbot.dao;

import com.recruiterbot.model.Candidate;
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

/** All candidate-related SQL. Every statement is handwritten and parameterized. */
@Repository
public class CandidateDao {

    private final JdbcTemplate jdbcTemplate;

    public CandidateDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** One query (LEFT JOIN) loads every candidate with its skills - no N+1. */
    public List<Candidate> findAll() {
        String sql = """
                SELECT c.id, c.name, c.experience_years, c.availability, c.traits, c.quirk, cs.skill
                FROM candidates c
                LEFT JOIN candidate_skills cs ON cs.candidate_id = c.id
                ORDER BY c.id, cs.skill
                """;
        return jdbcTemplate.query(sql, (ResultSetExtractor<List<Candidate>>) CandidateDao::extract);
    }

    public Optional<Candidate> findById(long id) {
        String sql = """
                SELECT c.id, c.name, c.experience_years, c.availability, c.traits, c.quirk, cs.skill
                FROM candidates c
                LEFT JOIN candidate_skills cs ON cs.candidate_id = c.id
                WHERE c.id = ?
                ORDER BY cs.skill
                """;
        List<Candidate> result = jdbcTemplate.query(sql, (ResultSetExtractor<List<Candidate>>) CandidateDao::extract, id);
        return result.stream().findFirst();
    }

    /** Insert or update by primary key, so repeated ingestion never duplicates a candidate. */
    public void upsert(Candidate candidate) {
        String sql = """
                INSERT INTO candidates (id, name, experience_years, availability, traits, quirk)
                VALUES (?, ?, ?, ?, ?, ?) AS new_row
                ON DUPLICATE KEY UPDATE
                    name = new_row.name,
                    experience_years = new_row.experience_years,
                    availability = new_row.availability,
                    traits = new_row.traits,
                    quirk = new_row.quirk
                """;
        jdbcTemplate.update(sql, candidate.id(), candidate.name(), candidate.experienceYears(),
                candidate.availability(), candidate.traits(), candidate.quirk());
    }

    public void deleteSkills(long candidateId) {
        String sql = """
                DELETE FROM candidate_skills
                WHERE candidate_id = ?
                """;
        jdbcTemplate.update(sql, candidateId);
    }

    public int insertSkills(long candidateId, Collection<String> skills) {
        String sql = """
                INSERT INTO candidate_skills (candidate_id, skill)
                VALUES (?, ?)
                """;
        List<String> skillList = new ArrayList<>(skills);
        jdbcTemplate.batchUpdate(sql, skillList, skillList.size(), (ps, skill) -> {
            ps.setLong(1, candidateId);
            ps.setString(2, skill);
        });
        return skillList.size();
    }

    private static List<Candidate> extract(ResultSet rs) throws SQLException {
        Map<Long, Candidate> byId = new LinkedHashMap<>();
        while (rs.next()) {
            long id = rs.getLong("id");
            Candidate candidate = byId.get(id);
            if (candidate == null) {
                candidate = new Candidate(id, rs.getString("name"), rs.getInt("experience_years"),
                        rs.getString("availability"), rs.getString("traits"), rs.getString("quirk"),
                        new LinkedHashSet<>());
                byId.put(id, candidate);
            }
            String skill = rs.getString("skill");
            if (skill != null) {
                candidate.skills().add(skill);
            }
        }
        return new ArrayList<>(byId.values());
    }
}
