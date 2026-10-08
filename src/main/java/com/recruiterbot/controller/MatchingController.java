package com.recruiterbot.controller;

import com.recruiterbot.dto.CandidateMatchResponse;
import com.recruiterbot.dto.JobMatchResponse;
import com.recruiterbot.service.MatchingService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MatchingController {

    private final MatchingService matchingService;

    public MatchingController(MatchingService matchingService) {
        this.matchingService = matchingService;
    }

    /** Ranked candidates for a job. */
    @GetMapping("/jobs/{id}/matches")
    public List<CandidateMatchResponse> matchesForJob(@PathVariable("id") long id) {
        return matchingService.findMatchesForJob(id);
    }

    /** Ranked jobs for a candidate (reverse direction). */
    @GetMapping("/candidates/{id}/matches")
    public List<JobMatchResponse> matchesForCandidate(@PathVariable("id") long id) {
        return matchingService.findMatchesForCandidate(id);
    }
}
