package com.recruiterbot.dto;

import java.util.List;

/** One ranked job in GET /api/candidates/{id}/matches. */
public record JobMatchResponse(
        long jobId,
        String jobTitle,
        double score,
        List<String> matchedSkills,
        List<String> missingSkills,
        int experienceYears,
        int requiredExperience,
        String availability,
        String reason) {
}
