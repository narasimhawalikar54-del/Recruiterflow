package com.recruiterbot.model;

import java.util.Set;

/** Plain Java representation of a jobs row plus its required skills (not an ORM entity). */
public record Job(
        long id,
        String title,
        int minExperienceYears,
        String cultureKeywords,
        String tagline,
        Set<String> requiredSkills) {
}
