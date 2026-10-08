package com.recruiterbot.service;

import com.recruiterbot.model.Candidate;
import com.recruiterbot.model.Job;
import com.recruiterbot.model.MatchResult;
import com.recruiterbot.model.SkillMatch;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Pure, deterministic scoring logic. Never touches the database.
 *
 * finalScore = skillScore * 60 + experienceScore * 30 + availabilityScore * 10   (0..100)
 */
@Component
public class MatchingAlgorithm {

    public static final double SKILL_WEIGHT = 60.0;
    public static final double EXPERIENCE_WEIGHT = 30.0;
    public static final double AVAILABILITY_WEIGHT = 10.0;

    static final double AVAILABILITY_IMMEDIATE = 1.0;
    static final double AVAILABILITY_TWO_WEEKS = 0.5;
    static final double AVAILABILITY_NOT_LOOKING = 0.0;

    public MatchResult evaluate(Candidate candidate, Job job) {
        SkillMatch skillMatch = matchSkills(candidate.skills(), job.requiredSkills());
        double skillScore = skillScore(skillMatch);
        double experienceScore = experienceScore(candidate.experienceYears(), job.minExperienceYears());
        double availabilityScore = availabilityScore(candidate.availability());

        double finalScore = skillScore * SKILL_WEIGHT
                + experienceScore * EXPERIENCE_WEIGHT
                + availabilityScore * AVAILABILITY_WEIGHT;

        String reason = buildReason(skillMatch, candidate.experienceYears(), job.minExperienceYears(),
                availabilityScore);
        return new MatchResult(round2(finalScore), skillScore, experienceScore, availabilityScore,
                skillMatch, reason);
    }

    /** Only the job's required skills count; extra candidate skills are ignored. */
    SkillMatch matchSkills(Set<String> candidateSkills, Set<String> requiredSkills) {
        Set<String> candidateNormalized = normalizeAll(candidateSkills);
        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String required : normalizeAll(requiredSkills)) {
            if (candidateNormalized.contains(required)) {
                matched.add(required);
            } else {
                missing.add(required);
            }
        }
        return new SkillMatch(matched, missing);
    }

    double skillScore(SkillMatch skillMatch) {
        int total = skillMatch.totalRequired();
        if (total == 0) {
            return 1.0; // nothing required -> nothing missing
        }
        return (double) skillMatch.matched().size() / total;
    }

    /** min(candidate / required, 1.0): meeting the minimum earns full credit, more earns nothing extra. */
    double experienceScore(int candidateYears, int requiredYears) {
        if (requiredYears <= 0) {
            return 1.0;
        }
        return Math.min((double) candidateYears / requiredYears, 1.0);
    }

    double availabilityScore(String availability) {
        return switch (normalize(availability)) {
            case "immediate" -> AVAILABILITY_IMMEDIATE;
            case "2 weeks" -> AVAILABILITY_TWO_WEEKS;
            default -> AVAILABILITY_NOT_LOOKING; // "not looking" and anything unrecognised
        };
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static Set<String> normalizeAll(Set<String> values) {
        Set<String> result = new LinkedHashSet<>();
        for (String value : values) {
            String normalized = normalize(value);
            if (!normalized.isEmpty()) {
                result.add(normalized);
            }
        }
        return result;
    }

    private String buildReason(SkillMatch skillMatch, int candidateYears, int requiredYears,
                               double availabilityScore) {
        int matched = skillMatch.matched().size();
        int total = skillMatch.totalRequired();

        String skills;
        if (total == 0 || matched == total) {
            skills = "Matches all " + total + " required skills";
        } else if (matched == 0) {
            skills = "Matches none of the " + total + " required skills";
        } else {
            skills = "Matches " + matched + " of " + total + " required skills (missing: "
                    + String.join(", ", skillMatch.missing()) + ")";
        }

        String ratio = " (" + candidateYears + " vs " + requiredYears + " years)";
        String experience;
        if (candidateYears > requiredYears) {
            experience = "exceeds the minimum experience" + ratio;
        } else if (candidateYears == requiredYears) {
            experience = "meets the minimum experience" + ratio;
        } else {
            experience = "is below the minimum experience" + ratio;
        }

        String availability;
        if (availabilityScore == AVAILABILITY_IMMEDIATE) {
            availability = "is immediately available";
        } else if (availabilityScore == AVAILABILITY_TWO_WEEKS) {
            availability = "is available in 2 weeks";
        } else {
            availability = "is not currently looking";
        }

        return skills + ", " + experience + ", and " + availability + ".";
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
