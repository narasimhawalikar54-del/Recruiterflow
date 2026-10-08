package com.recruiterbot.model;

import java.util.Set;

/** Plain Java representation of a candidates row plus its skills (not an ORM entity). */
public record Candidate(
        long id,
        String name,
        int experienceYears,
        String availability,
        String traits,
        String quirk,
        Set<String> skills) {
}
