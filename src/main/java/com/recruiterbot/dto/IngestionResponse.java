package com.recruiterbot.dto;

public record IngestionResponse(
        int candidatesLoaded,
        int jobsLoaded,
        int candidateSkillLinks,
        int jobSkillLinks,
        String message) {
}
