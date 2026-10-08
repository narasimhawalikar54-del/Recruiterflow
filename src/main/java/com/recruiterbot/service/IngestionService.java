package com.recruiterbot.service;

import com.recruiterbot.dao.CandidateDao;
import com.recruiterbot.dao.JobDao;
import com.recruiterbot.dto.IngestionResponse;
import com.recruiterbot.model.Candidate;
import com.recruiterbot.model.Job;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads the assignment's candidates and jobs. Idempotent: rows carry fixed ids and are upserted
 * (INSERT ... ON DUPLICATE KEY UPDATE); skill links are deleted and re-inserted. All in one transaction.
 */
@Service
public class IngestionService {

    private static final List<Candidate> CANDIDATES = List.of(
            candidate(1, "Sherlock H.", 8, "Immediate", "analytical, blunt",
                    "Solves problems by eliminating the impossible; occasionally insufferable in standups.",
                    "deduction", "pattern-recognition", "forensics"),
            candidate(2, "Hermione G.", 4, "2 weeks", "detail-oriented, overachiever",
                    "Reads the entire documentation before writing a single line of code.",
                    "research", "time-management", "public-speaking"),
            candidate(3, "Tony S.", 12, "Not looking", "confident, innovative",
                    "Ships an MVP overnight, refuses to write tests.",
                    "systems-design", "rapid-prototyping", "leadership"),
            candidate(4, "Leslie K.", 6, "Immediate", "tenacious, organized",
                    "Has a binder for everything, including the binder.",
                    "project-management", "stakeholder-management", "public-speaking"),
            candidate(5, "Ron S.", 15, "Not looking", "stubborn, principled",
                    "Refuses to use more than one monitor.",
                    "woodworking", "minimalism", "negotiation"),
            candidate(6, "Rick S.", 20, "Immediate", "genius, reckless",
                    "Solution works, mechanism deeply concerning.",
                    "systems-design", "rapid-prototyping", "chemistry"),
            candidate(7, "Elle W.", 3, "Immediate", "optimistic, sharp",
                    "Underestimated in every standup, correct in every retro.",
                    "persuasion", "research", "public-speaking"),
            candidate(8, "MacGyver", 10, "2 weeks", "calm, improviser",
                    "Fixes production outages with duct tape and a paperclip metaphor.",
                    "rapid-prototyping", "resourcefulness", "chemistry", "systems-design"),
            candidate(9, "Sheldon C.", 9, "Immediate", "rigid, brilliant",
                    "Correct 95% of the time, insufferable 100% of the time.",
                    "theoretical-analysis", "pattern-recognition", "research"),
            candidate(10, "Katniss E.", 5, "Immediate", "resilient, decisive",
                    "Excellent under pressure, terrible with public speaking.",
                    "precision", "strategy", "crisis-management"),
            candidate(11, "Michael S.", 11, "Not looking", "enthusiastic, chaotic",
                    "World's best boss, according to a mug he bought himself.",
                    "sales", "public-speaking", "team-building"),
            candidate(12, "Olivia P.", 13, "2 weeks", "decisive, intense",
                    "Handles it. Whatever it is.",
                    "crisis-management", "negotiation", "strategy", "leadership"),
            candidate(13, "Ted L.", 7, "Immediate", "empathetic, persistent",
                    "Turns every technical setback into a folksy metaphor.",
                    "team-building", "optimism", "mentorship", "public-speaking"),
            candidate(14, "Miranda P.", 18, "Not looking", "demanding, decisive",
                    "Reviews every PR personally. Says nothing. Everyone panics.",
                    "leadership", "negotiation", "stakeholder-management", "precision"),
            candidate(15, "Dwight S.", 9, "Immediate", "intense, loyal",
                    "Assistant to the regional backend engineer.",
                    "sales", "negotiation", "security", "loyalty"));

    private static final List<Job> JOBS = List.of(
            job(1, "Backend Detective", 3, "analytical, autonomous",
                    "We have a bug. We have no leads. We have you.",
                    "deduction", "pattern-recognition", "forensics"),
            job(2, "Rapid Prototyping Engineer", 2, "innovative, fast-paced",
                    "Ship first, document never (kidding \u2014 please document).",
                    "rapid-prototyping", "systems-design"),
            job(3, "Developer Relations Lead", 2, "energetic, curious",
                    "Explain complex things to confused humans, cheerfully.",
                    "public-speaking", "research"),
            job(4, "Engineering Manager, Chaos Team", 5, "empathetic, organized",
                    "Herd cats. The cats are senior engineers.",
                    "team-building", "stakeholder-management", "leadership"),
            job(5, "Incident Commander", 4, "decisive, calm-under-pressure",
                    "3am page. You're the one who picks up.",
                    "crisis-management", "strategy", "negotiation"),
            job(6, "Sales Engineer", 3, "enthusiastic, persistent",
                    "Sell the vision, then go build it.",
                    "sales", "public-speaking", "negotiation"));

    private final CandidateDao candidateDao;
    private final JobDao jobDao;

    public IngestionService(CandidateDao candidateDao, JobDao jobDao) {
        this.candidateDao = candidateDao;
        this.jobDao = jobDao;
    }

    @Transactional
    public IngestionResponse ingest() {
        int candidateSkillLinks = 0;
        for (Candidate candidate : CANDIDATES) {
            candidateDao.upsert(candidate);
            candidateDao.deleteSkills(candidate.id());
            candidateSkillLinks += candidateDao.insertSkills(candidate.id(), candidate.skills());
        }

        int jobSkillLinks = 0;
        for (Job job : JOBS) {
            jobDao.upsert(job);
            jobDao.deleteRequiredSkills(job.id());
            jobSkillLinks += jobDao.insertRequiredSkills(job.id(), job.requiredSkills());
        }

        return new IngestionResponse(CANDIDATES.size(), JOBS.size(), candidateSkillLinks, jobSkillLinks,
                "Ingestion complete. Safe to run again: records are upserted by id, not duplicated.");
    }

    private static Candidate candidate(long id, String name, int years, String availability,
                                       String traits, String quirk, String... skills) {
        return new Candidate(id, name, years, availability, traits, quirk, normalized(skills));
    }

    private static Job job(long id, String title, int minYears, String cultureKeywords,
                           String tagline, String... skills) {
        return new Job(id, title, minYears, cultureKeywords, tagline, normalized(skills));
    }

    private static Set<String> normalized(String... skills) {
        return Stream.of(skills)
                .map(MatchingAlgorithm::normalize)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
