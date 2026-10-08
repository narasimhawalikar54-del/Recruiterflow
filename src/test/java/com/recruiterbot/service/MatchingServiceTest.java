package com.recruiterbot.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.recruiterbot.dao.CandidateDao;
import com.recruiterbot.dao.JobDao;
import com.recruiterbot.dto.CandidateMatchResponse;
import com.recruiterbot.exception.ResourceNotFoundException;
import com.recruiterbot.model.Candidate;
import com.recruiterbot.model.Job;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MatchingServiceTest {

    private final CandidateDao candidateDao = mock(CandidateDao.class);
    private final JobDao jobDao = mock(JobDao.class);
    private final MatchingService service = new MatchingService(candidateDao, jobDao, new MatchingAlgorithm());

    private static Set<String> skills(String... values) {
        return new LinkedHashSet<>(List.of(values));
    }

    @Test
    void tiesAreBrokenByExperienceThenName() {
        Job job = new Job(1, "Job", 2, "", "", skills("a", "b"));
        // All four score 100 and match 2 skills, so only experience, then name, decide the order.
        Candidate zed = new Candidate(1, "Zed", 5, "Immediate", "", "", skills("a", "b"));
        Candidate amy = new Candidate(2, "Amy", 9, "Immediate", "", "", skills("a", "b"));
        Candidate bob = new Candidate(3, "Bob", 5, "Immediate", "", "", skills("a", "b"));
        Candidate abe = new Candidate(4, "Abe", 5, "Immediate", "", "", skills("a", "b"));

        when(jobDao.findById(1)).thenReturn(Optional.of(job));
        when(candidateDao.findAll()).thenReturn(List.of(zed, bob, amy, abe));

        List<String> order = service.findMatchesForJob(1).stream()
                .map(CandidateMatchResponse::candidateName).toList();

        assertEquals(List.of("Amy", "Abe", "Bob", "Zed"), order);
    }

    @Test
    void higherScoreRanksFirst() {
        Job job = new Job(1, "Job", 2, "", "", skills("a", "b"));
        Candidate partial = new Candidate(1, "Partial", 5, "Immediate", "", "", skills("a"));
        Candidate full = new Candidate(2, "Full", 5, "Immediate", "", "", skills("a", "b"));

        when(jobDao.findById(1)).thenReturn(Optional.of(job));
        when(candidateDao.findAll()).thenReturn(List.of(partial, full));

        List<CandidateMatchResponse> result = service.findMatchesForJob(1);

        assertEquals("Full", result.get(0).candidateName());
        assertEquals(100.0, result.get(0).score(), 0.001);
        assertEquals(70.0, result.get(1).score(), 0.001); // 30 + 30 + 10
    }

    @Test
    void unknownJobAndCandidateThrowNotFound() {
        when(jobDao.findById(99)).thenReturn(Optional.empty());
        when(candidateDao.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.findMatchesForJob(99));
        assertThrows(ResourceNotFoundException.class, () -> service.findMatchesForCandidate(99));
    }
}
