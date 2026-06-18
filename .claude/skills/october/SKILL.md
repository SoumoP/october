---
name: october
description: Run October to discover HSR Layout startups, detect their ATS, fetch open jobs, and summarize results. Triggers on phrases like "run October", "find jobs in HSR Layout", "refresh job listings", "find new openings", "scan startups for jobs", "what's hiring in HSR Layout".
---

# October

October is the discovery → ATS-detection → job-fetch → CSV-export pipeline. This skill is a **thin orchestration layer** — all real work happens inside the Java Spring Boot application at `october/`. The skill's job is to run that app and summarize its output.

## When to invoke

Use this skill when the user asks to:

- "Run October" / "refresh jobs" / "scan startups"
- "Find new openings at HSR Layout startups"
- "What's hiring in HSR Layout right now?"
- Re-fetch jobs after edits to the seed list
- Inspect ATS coverage across the seed list

## What this skill does NOT do

This skill does **not** contain ATS detection logic, HTTP clients, or parsing rules. Those live entirely in the Java app under `october/src/main/java/com/october/`. The skill must not duplicate or override that logic. If detection or fetching needs to change, edit the Java code, not this skill.

## How to run

1. From the project root:
   ```bash
   cd ~/my_repo/october
   ```

2. Run the pipeline via Maven:
   ```bash
   mvn -q spring-boot:run
   ```
   The pipeline runs four stages: discovery → ATS detection → job fetch → CSV export. Logs from each stage are printed to stdout.

   Optional CLI flags to skip stages:
   ```bash
   mvn -q spring-boot:run -Dspring-boot.run.arguments="--skip-discovery --skip-detection"
   ```

3. Once the process exits, read the output CSV:
   - Path: `october/exports/october.csv`
   - Columns: `company_name, website, ats_type, ats_identifier, job_count, jobs, important_emails`
   - `jobs` is a semicolon-separated list of job titles.

## How to summarize results back to the user

After reading the CSV, produce a tight summary:

1. **Headline numbers**:
   - Total companies in seed list
   - Companies with ATS detected (and breakdown by Lever / Greenhouse / Ashby / Unknown)
   - Total job openings across all detected companies

2. **Top companies by openings** (descending by `job_count`):
   - List the top 5–10 companies, each with their job count and ATS type.

3. **Notable opportunities**:
   - Skim job titles in the `jobs` column for roles matching the user's profile (software engineer, backend, platform, etc.). Surface 5–10 high-relevance titles with company names.

4. **Unresolved companies** (`ats_type = UNKNOWN`):
   - Briefly list these so the user knows which need manual investigation.

Keep the summary tight. Do not paste the whole CSV. Suggest a follow-up action only when relevant (e.g., "Want me to add a new company to the seed list?").

## Editing the seed list

The discovery source is a YAML file at `october/src/main/resources/seed/hsr-layout.yml`. To add or remove a company:

1. Edit the YAML directly (each entry needs `name`, `website`, optional `careers_url`).
2. Re-run the skill. `DiscoveryService` is idempotent — existing companies are not duplicated.

## Troubleshooting

- **No jobs returned for a known-good ATS company**: the ATS identifier may be wrong. Inspect `october/data/october.mv.db` (H2 file DB) using any H2 client, or temporarily flip `org.hibernate.SQL: DEBUG` in `application.yml`.
- **Detection always returns UNKNOWN**: check that the careers URL is reachable from your network and that the seed list URL is correct. Detectors fall back to scanning the homepage if the careers page yields nothing.
- **Build fails**: ensure Java 21 and Maven are installed (`java --version`, `mvn --version`).
