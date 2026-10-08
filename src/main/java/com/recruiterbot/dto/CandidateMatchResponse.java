package com.recruiterbot.dto;

import java.util.List;

/** One ranked candidate in GET /api/jobs/{id}/matches. */
public record CandidateMatchResponse(
        long candidateId,
        String candidateName,
        double score,
        List<String> matchedSkills,
        List<String> missingSkills,
        int experienceYears,
        int requiredExperience,
        String availability,
        String reason) {
}
