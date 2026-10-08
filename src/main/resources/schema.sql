-- Recruiter Bot schema (hand-written DDL, MySQL 8.x). Safe to run repeatedly.

CREATE TABLE IF NOT EXISTS candidates (
    id               BIGINT       NOT NULL,
    name             VARCHAR(100) NOT NULL,
    experience_years INT          NOT NULL,
    availability     VARCHAR(20)  NOT NULL,   -- 'Immediate', '2 weeks', 'Not looking'
    traits           VARCHAR(255),
    quirk            VARCHAR(500),
    PRIMARY KEY (id),
    CONSTRAINT uq_candidates_name UNIQUE (name),
    CONSTRAINT chk_candidates_experience CHECK (experience_years >= 0)
);

CREATE TABLE IF NOT EXISTS candidate_skills (
    candidate_id BIGINT       NOT NULL,
    skill        VARCHAR(100) NOT NULL,       -- stored normalized (trimmed, lowercase)
    PRIMARY KEY (candidate_id, skill),
    CONSTRAINT fk_candidate_skills_candidate FOREIGN KEY (candidate_id)
        REFERENCES candidates (id) ON DELETE CASCADE,
    INDEX idx_candidate_skills_skill (skill)
);

CREATE TABLE IF NOT EXISTS jobs (
    id                    BIGINT       NOT NULL,
    title                 VARCHAR(150) NOT NULL,
    min_experience_years  INT          NOT NULL,
    culture_keywords      VARCHAR(255),
    tagline               VARCHAR(500),
    PRIMARY KEY (id),
    CONSTRAINT uq_jobs_title UNIQUE (title),
    CONSTRAINT chk_jobs_min_experience CHECK (min_experience_years >= 0)
);

CREATE TABLE IF NOT EXISTS job_required_skills (
    job_id BIGINT       NOT NULL,
    skill  VARCHAR(100) NOT NULL,             -- stored normalized (trimmed, lowercase)
    PRIMARY KEY (job_id, skill),
    CONSTRAINT fk_job_required_skills_job FOREIGN KEY (job_id)
        REFERENCES jobs (id) ON DELETE CASCADE,
    INDEX idx_job_required_skills_skill (skill)
);
