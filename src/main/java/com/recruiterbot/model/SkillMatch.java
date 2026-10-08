package com.recruiterbot.model;

import java.util.List;

/** Which of a job's required skills a candidate has / lacks (normalized names). */
public record SkillMatch(List<String> matched, List<String> missing) {

    public int totalRequired() {
        return matched.size() + missing.size();
    }
}
