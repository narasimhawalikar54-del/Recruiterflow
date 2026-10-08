# Recruiter Bot

## Overview

Recruiter Bot is a small backend service that stores candidates and jobs in MySQL and ranks the best matches in both directions: the best candidates for a job, and the best jobs for a candidate. Each result carries a score, the matched and missing skills, and a generated plain-English reason. The assignment also includes a separate SQL exercise (Part 2), delivered as one `.sql` file.

All database access is raw, handwritten SQL executed through `JdbcTemplate`. There is no ORM, query builder or generated SQL.

## Tech Stack

- Java 21, Spring Boot 3.3.x, Maven
- Spring Web, Spring JDBC (`JdbcTemplate`), HikariCP
- MySQL 8.x
- JUnit 5 and Mockito for tests
- Postman for demonstration (no frontend)

## Architecture

```
Postman -> Controller -> Service -> DAO -> JdbcTemplate -> MySQL
                           |
                           +-> MatchingAlgorithm (pure Java, no DB access)
```

- **Controller**: HTTP endpoints only.
- **MatchingService**: loads data through the DAOs, calls the algorithm, ranks, builds responses.
- **MatchingAlgorithm**: skill, experience and availability scoring, matched/missing skills, reason text.
- **DAO**: every SQL statement lives here as visible, parameterized text (`?` placeholders, never string concatenation).
- **IngestionService**: transactional, repeatable load of the assignment data.

## Project Structure

```
recruiter-bot/
├── pom.xml
├── README.md
├── .env.example
├── sql/sql_detective.sql                 Part 2
├── postman/RecruiterBot.postman_collection.json
└── src/
    ├── main/java/com/recruiterbot/
    │   ├── RecruiterBotApplication.java
    │   ├── controller/   MatchingController, IngestionController
    │   ├── service/      MatchingService, MatchingAlgorithm, IngestionService
    │   ├── dao/          CandidateDao, JobDao
    │   ├── model/        Candidate, Job, MatchResult, SkillMatch (plain Java records)
    │   ├── dto/          CandidateMatchResponse, JobMatchResponse, IngestionResponse
    │   └── exception/    ResourceNotFoundException, GlobalExceptionHandler
    ├── main/resources/   application.properties, schema.sql
    └── test/java/        MatchingAlgorithmTest, MatchingServiceTest
```

There is no `MatchingDao`: the write SQL sits in `CandidateDao` and `JobDao`, and a third DAO would have had no distinct responsibility.

## Prerequisites

- JDK 21
- Maven 3.9+
- MySQL 8.x running locally

## MySQL Setup

The app creates the `recruiter_bot_app` database automatically (`createDatabaseIfNotExist=true`). To create it yourself:

```
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS recruiter_bot_app CHARACTER SET utf8mb4;"
```

Tables are created at startup from `src/main/resources/schema.sql` (`CREATE TABLE IF NOT EXISTS`, so repeated starts are safe).

## Configuration

Credentials come from environment variables. Nothing secret is committed.

| Variable | Default |
|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/recruiter_bot_app?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true` |
| `DB_USERNAME` | `root` |
| `DB_PASSWORD` | empty |

See `.env.example`. Set them in your shell, for example (Windows `cmd`):

```
set DB_PASSWORD=your_mysql_password
```

## Running the Application

```
mvn test
mvn spring-boot:run
```

The server starts on `http://localhost:8080`. Then load the data once:

```
curl -X POST http://localhost:8080/api/ingest
```

## Database Schema

```
candidates(id PK, name UNIQUE, experience_years, availability, traits, quirk)
candidate_skills(candidate_id FK, skill, PK(candidate_id, skill))
jobs(id PK, title UNIQUE, min_experience_years, culture_keywords, tagline)
job_required_skills(job_id FK, skill, PK(job_id, skill))
```

Skills are separate rows, not comma-separated values. They are stored normalized (trimmed, lowercase). Foreign keys cascade on delete, and each skill column is indexed.

## API Endpoints

| Method | Path | Description |
|---|---|---|
| POST | `/api/ingest` | Loads the assignment's candidates and jobs (idempotent). |
| GET | `/api/jobs/{id}/matches` | Ranked candidates for a job. |
| GET | `/api/candidates/{id}/matches` | Ranked jobs for a candidate. |

An unknown id returns HTTP 404 with a JSON body:

```json
{
  "timestamp": "2026-10-08T10:00:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Job with id 999 was not found",
  "path": "/api/jobs/999/matches"
}
```

A non-numeric id returns 400.

Example for `GET /api/jobs/1/matches` (first entry; the second is Sheldon C., score 60):

```json
{
  "candidateId": 1,
  "candidateName": "Sherlock H.",
  "score": 100.0,
  "matchedSkills": ["deduction", "forensics", "pattern-recognition"],
  "missingSkills": [],
  "experienceYears": 8,
  "requiredExperience": 3,
  "availability": "Immediate",
  "reason": "Matches all 3 required skills, exceeds the minimum experience (8 vs 3 years), and is immediately available."
}
```

### Ingestion strategy (idempotent)

Every seeded row has a fixed id. Candidates and jobs are written with `INSERT ... ON DUPLICATE KEY UPDATE`, and each row's skill links are deleted and re-inserted. All of it runs in one `@Transactional` method, so a failure rolls back everything, and repeated calls leave exactly the same rows.

## Postman

Import `postman/RecruiterBot.postman_collection.json`. The `baseUrl` variable defaults to `http://localhost:8080`. Run "Ingest seed data" first. The collection also contains four job-match requests, three candidate-match requests, and an invalid job id and an invalid candidate id (both expect 404).

## Matching Algorithm

For a candidate/job pair:

1. **Skill score** = matched required skills / total required skills. Only the job's required skills count, so extra candidate skills add nothing. Names are normalized (trim, lowercase) before comparison.
2. **Experience score** = `min(candidateYears / minimumYears, 1.0)`.
3. **Availability score**: Immediate = 1.0, 2 weeks = 0.5, Not looking = 0.0.

## Scoring Formula

```
finalScore = skillScore * 60 + experienceScore * 30 + availabilityScore * 10
```

The result is between 0 and 100, rounded to 2 decimals. The weights are named constants in `MatchingAlgorithm`.

**Ranking and tie-breaking** (candidates for a job): score descending, then more matched skills, then more experience, then name ascending. For jobs for a candidate: score descending, then more matched skills, then title ascending. Both orders are fully deterministic.

**Edge cases**: a job with no required skills gets a skill score of 1.0, and a minimum experience of 0 gives an experience score of 1.0 (no division by zero). An unrecognised availability value scores 0.

## Design Decisions

1. **Skills have the highest weight (60%).** The assignment names skill overlap as the core signal; someone without the skills cannot do the job whatever else is true.
2. **Experience is capped at the minimum.** The requirement is a minimum, so meeting it gives full credit. Without the cap, a 20-year candidate would outscore a perfectly skilled 8-year one for a role asking for 3.
3. **Availability has a small weight (10%).** It is a timing detail, not suitability. It can reorder near-equals but cannot lift an unqualified candidate above a qualified one.
4. **Scoring is in Java, not SQL.** It is unit-testable without a database, and the reason text and tie-breaks are easy to read and change. SQL does the loading and nothing else.
5. **SQL is isolated in the DAO layer.** Services never see SQL, and all queries can be reviewed in two files.
6. **Skills are normalized into tables.** Comma-separated columns cannot be indexed, joined or constrained; relational rows can.
7. **JdbcTemplate instead of an ORM.** The assignment requires raw handwritten SQL. JdbcTemplate removes only connection and mapping boilerplate while the SQL stays visible.
8. **Deterministic tie-breaking.** Many candidates share scores, so without explicit rules the order could change between calls.
9. **N+1 queries are avoided.** Each DAO method loads an entity with its skills in a single LEFT JOIN and groups the rows in Java, so ranking all candidates costs two queries (one for the job, one for all candidates).

## Trade-offs

- Matching loads all candidates into memory and scores them in Java. That is fine for a small dataset; at large scale, SQL pre-filtering or pagination would be needed.
- Exact skill matching only. Synonyms (for example "strategy" and "strategic planning") and similarity are not handled.
- Traits, culture keywords and quirks are stored but not used in scoring. The assignment allows this, and including them would need a defensible similarity model.
- Results include every candidate or job, even with a low score. There is no minimum threshold or pagination.
- Ingestion data is embedded in Java rather than read from a file, to keep the service simple.
- Candidate/job ids come from the seed data; there are no create-record endpoints.

## Part 2 — SQL Detective Challenge

`sql/sql_detective.sql` creates its own database (`sql_detective`), loads the supplied schema and seed data, and answers each question with one query preceded by a comment with the question number. It can be re-run safely.

```
mysql -u root -p < sql/sql_detective.sql
```

Notes on the trickier questions:

- **Q2** uses a `LEFT JOIN` with the Final filter in the `ON` clause, so postings with no Final interviews show 0.
- **Q3** treats a person as `LOWER(TRIM(email))`, because Ananya Rao has two applicant records whose emails differ only by case. An application to a posting is evidenced by an interview for it.
- **Q4** counts only `Final` interviews. The total includes failed, no-show and pending (NULL) outcomes, and `HAVING COUNT(*) >= 3` applies the minimum.
- **Q5** uses a CTE and `RANK() OVER (PARTITION BY department ORDER BY placements DESC)`. Ties for first place are all returned.

## AI Usage

I used an AI coding assistant (Claude) to generate the project scaffolding, DAO code, tests and this README from the assignment text. I reviewed the design and chose the scoring model and trade-offs above, and I can explain and defend each decision.
