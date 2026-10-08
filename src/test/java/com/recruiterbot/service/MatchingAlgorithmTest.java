package com.recruiterbot.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.recruiterbot.model.Candidate;
import com.recruiterbot.model.Job;
import com.recruiterbot.model.MatchResult;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchingAlgorithmTest {

    private static final double DELTA = 0.001;

    private final MatchingAlgorithm algorithm = new MatchingAlgorithm();

    private final Job job = new Job(1, "Backend Detective", 3, "", "",
            new LinkedHashSet<>(List.of("deduction", "pattern-recognition", "forensics")));

    private static Candidate candidate(int years, String availability, String... skills) {
        return new Candidate(1, "Test", years, availability, "", "", new LinkedHashSet<>(Arrays.asList(skills)));
    }

    @Test
    void fullSkillMatchImmediateMeetingExperienceScores100() {
        MatchResult r = algorithm.evaluate(
                candidate(8, "Immediate", "deduction", "pattern-recognition", "forensics"), job);
        assertEquals(100.0, r.score(), DELTA);
        assertTrue(r.skillMatch().missing().isEmpty());
    }

    @Test
    void partialSkillMatchGivesProportionalSkillScore() {
        MatchResult r = algorithm.evaluate(candidate(8, "Immediate", "deduction", "forensics"), job);
        assertEquals(2.0 / 3.0, r.skillScore(), DELTA);
        assertEquals(80.0, r.score(), DELTA); // 40 + 30 + 10
        assertEquals(List.of("deduction", "forensics"), r.skillMatch().matched());
        assertEquals(List.of("pattern-recognition"), r.skillMatch().missing());
    }

    @Test
    void zeroSkillMatchKeepsOnlyExperienceAndAvailability() {
        MatchResult r = algorithm.evaluate(candidate(8, "Immediate", "cooking"), job);
        assertEquals(0.0, r.skillScore(), DELTA);
        assertEquals(40.0, r.score(), DELTA);
        assertEquals(3, r.skillMatch().missing().size());
    }

    @Test
    void extraCandidateSkillsDoNotIncreaseScore() {
        MatchResult r = algorithm.evaluate(
                candidate(8, "Immediate", "deduction", "cooking", "woodworking", "chemistry"), job);
        assertEquals(1.0 / 3.0, r.skillScore(), DELTA);
    }

    @Test
    void skillNamesAreNormalized() {
        MatchResult r = algorithm.evaluate(
                candidate(8, "Immediate", " DEDUCTION ", "Pattern-Recognition", "forensics"), job);
        assertEquals(100.0, r.score(), DELTA);
    }

    @Test
    void experienceBelowRequirementIsProportional() {
        MatchResult r = algorithm.evaluate(
                candidate(1, "Immediate", "deduction", "pattern-recognition", "forensics"), job);
        assertEquals(1.0 / 3.0, r.experienceScore(), DELTA);
        assertEquals(80.0, r.score(), DELTA); // 60 + 10 + 10
        assertTrue(r.reason().contains("below the minimum experience"));
    }

    @Test
    void experienceAboveRequirementIsCappedAtOne() {
        assertEquals(1.0, algorithm.experienceScore(8, 3), DELTA);
        assertEquals(1.0, algorithm.experienceScore(3, 3), DELTA);
    }

    @Test
    void availabilityScores() {
        assertEquals(1.0, algorithm.availabilityScore("Immediate"), DELTA);
        assertEquals(0.5, algorithm.availabilityScore("2 weeks"), DELTA);
        assertEquals(0.0, algorithm.availabilityScore("Not looking"), DELTA);
    }

    @Test
    void availabilityAffectsFinalScoreByAtMostTenPoints() {
        String[] all = {"deduction", "pattern-recognition", "forensics"};
        double immediate = algorithm.evaluate(candidate(8, "Immediate", all), job).score();
        double twoWeeks = algorithm.evaluate(candidate(8, "2 weeks", all), job).score();
        double notLooking = algorithm.evaluate(candidate(8, "Not looking", all), job).score();
        assertEquals(100.0, immediate, DELTA);
        assertEquals(95.0, twoWeeks, DELTA);
        assertEquals(90.0, notLooking, DELTA);
    }

    @Test
    void reasonIsGeneratedFromTheData() {
        MatchResult r = algorithm.evaluate(candidate(8, "Immediate", "deduction", "forensics"), job);
        assertEquals("Matches 2 of 3 required skills (missing: pattern-recognition), "
                + "exceeds the minimum experience (8 vs 3 years), and is immediately available.", r.reason());
    }
}
