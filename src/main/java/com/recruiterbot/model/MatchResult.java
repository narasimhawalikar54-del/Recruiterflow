package com.recruiterbot.model;

/** Output of the matching algorithm for one candidate/job pair. Scores: final 0-100, parts 0-1. */
public record MatchResult(
        double score,
        double skillScore,
        double experienceScore,
        double availabilityScore,
        SkillMatch skillMatch,
        String reason) {
}
