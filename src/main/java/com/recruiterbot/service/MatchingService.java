package com.recruiterbot.service;

import com.recruiterbot.dao.CandidateDao;
import com.recruiterbot.dao.JobDao;
import com.recruiterbot.dto.CandidateMatchResponse;
import com.recruiterbot.dto.JobMatchResponse;
import com.recruiterbot.exception.ResourceNotFoundException;
import com.recruiterbot.model.Candidate;
import com.recruiterbot.model.Job;
import com.recruiterbot.model.MatchResult;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/** Orchestrates: load data (DAO) -> score (MatchingAlgorithm) -> rank -> build response. */
@Service
public class MatchingService {

    /** Score desc, then more matched skills, then more experience, then name A-Z. */
    private static final Comparator<ScoredCandidate> CANDIDATE_RANKING =
            Comparator.comparingDouble((ScoredCandidate s) -> s.result().score()).reversed()
                    .thenComparing(Comparator.comparingInt(
                            (ScoredCandidate s) -> s.result().skillMatch().matched().size()).reversed())
                    .thenComparing(Comparator.comparingInt(
                            (ScoredCandidate s) -> s.candidate().experienceYears()).reversed())
                    .thenComparing((ScoredCandidate s) -> s.candidate().name());

    /** Score desc, then more matched skills, then job title A-Z. */
    private static final Comparator<ScoredJob> JOB_RANKING =
            Comparator.comparingDouble((ScoredJob s) -> s.result().score()).reversed()
                    .thenComparing(Comparator.comparingInt(
                            (ScoredJob s) -> s.result().skillMatch().matched().size()).reversed())
                    .thenComparing((ScoredJob s) -> s.job().title());

    private final CandidateDao candidateDao;
    private final JobDao jobDao;
    private final MatchingAlgorithm algorithm;

    public MatchingService(CandidateDao candidateDao, JobDao jobDao, MatchingAlgorithm algorithm) {
        this.candidateDao = candidateDao;
        this.jobDao = jobDao;
        this.algorithm = algorithm;
    }

    public List<CandidateMatchResponse> findMatchesForJob(long jobId) {
        Job job = jobDao.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job", jobId));

        return candidateDao.findAll().stream()
                .map(candidate -> new ScoredCandidate(candidate, algorithm.evaluate(candidate, job)))
                .sorted(CANDIDATE_RANKING)
                .map(scored -> toResponse(scored, job))
                .toList();
    }

    public List<JobMatchResponse> findMatchesForCandidate(long candidateId) {
        Candidate candidate = candidateDao.findById(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate", candidateId));

        return jobDao.findAll().stream()
                .map(job -> new ScoredJob(job, algorithm.evaluate(candidate, job)))
                .sorted(JOB_RANKING)
                .map(scored -> toResponse(scored, candidate))
                .toList();
    }

    private CandidateMatchResponse toResponse(ScoredCandidate scored, Job job) {
        Candidate candidate = scored.candidate();
        MatchResult result = scored.result();
        return new CandidateMatchResponse(candidate.id(), candidate.name(), result.score(),
                result.skillMatch().matched(), result.skillMatch().missing(),
                candidate.experienceYears(), job.minExperienceYears(), candidate.availability(),
                result.reason());
    }

    private JobMatchResponse toResponse(ScoredJob scored, Candidate candidate) {
        Job job = scored.job();
        MatchResult result = scored.result();
        return new JobMatchResponse(job.id(), job.title(), result.score(),
                result.skillMatch().matched(), result.skillMatch().missing(),
                candidate.experienceYears(), job.minExperienceYears(), candidate.availability(),
                result.reason());
    }

    private record ScoredCandidate(Candidate candidate, MatchResult result) {
    }

    private record ScoredJob(Job job, MatchResult result) {
    }
}
