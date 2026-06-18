# October Match — Phase 2 (slice 1) design

**Date:** 2026-06-18
**Status:** Approved (brainstorming); awaiting spec review
**Author:** Soumyajit Podder
**Predecessor:** Phase 1 (Signal — discover → detect ATS → fetch → CSV) shipped 2026-06-18

---

## Context

The pipeline currently fetches ~145 jobs from the seed list (Lever / Greenhouse / Ashby / Workday). Most are irrelevant to the user's role search — sales managers, finance analysts, senior staff roles outside the user's experience band. The CSV is dumped raw; the UI shows everything.

Match is the first reduction step: apply a small regex profile to drop irrelevant jobs **before they persist**, and tag the survivors with a YOE-fit flag based on what their description states. Future Phase 2 slices (scoring, custom resumes, contacts) build on top of the same `match.yml`.

---

## Goals

- Title-level include/exclude regex applied at the **fetch boundary**; non-matching jobs never enter H2.
- YOE range parsed from each surviving job's description; classified as `matched | out_of_range | unknown` against the user's configured band.
- `yoe_status` surfaced in the CSV and on the deployed UI as a chip-row filter (dynamic, same pattern as the ATS chips).
- All rule + profile config in a single `match.yml`, committed to the repo (repo is public; YOE band and stack interests are not sensitive).
- **No DB schema migration. No new entity tables.**

---

## Non-goals (explicit)

- Scored / weighted relevance — boolean filter only. Scoring is a later Phase 2 slice.
- Description fetch for Workday postings (CXS list API doesn't return them; would need a per-job detail call). Workday jobs land as `yoe_status = unknown`.
- Editing rules from the UI. Edit `match.yml` → commit → push; the 4-hour workflow handles refresh.
- Using `your_yoe` for filtering — accepted in schema for future scoring/personalization, ignored by the filter today.
- Excluding by description text (the schema has `include_in_description` but no symmetric `exclude_in_description`; if needed later, add it without breaking format).

---

## Architecture

### New modules

```
src/main/resources/match.yml          # profile + rules (committed)
src/main/java/com/october/match/
  ├── MatchProfile.java               # immutable record holding the loaded config
  ├── MatchProperties.java            # @ConfigurationProperties("october.match") binding
  └── MatchService.java               # @Service: compiles regex once at construction
                                      #           exposes passesRegex(Job) and classifyYoe(Job)
```

### Modified modules

```
src/main/java/com/october/providers/JobFetchService.java
  → filter the provider's results with matchService.passesRegex before saveAll

src/main/java/com/october/export/CsvExportService.java
  → switch the CSV from per-company to per-job (see "CSV restructure" below)
  → emit yoe_status via matchService.classifyYoe

docs/index.html
  → new chip row for yoe_status, derived from CSV via the same dynamic pattern as ATS chips
  → adapt rendering to per-job rows (group by company visually, but the CSV grain is per-job)
```

### Pipeline data flow

```
provider.fetchJobs(atsIdentifier)            # raw N jobs from ATS API
        ↓
matchService.passesRegex(job)                # drops title-fails / excluded — never persisted
        ↓
jobRepository.saveAll(kept)                  # only matches in H2
        ↓
csvExport:
  for each Job in DB:
    yoe = matchService.extractYoe(description)
    yoe_status = matchService.classifyYoe(yoe, profile)
    write per-job row with yoe_status
        ↓
docs/index.html
  parses CSV, builds dynamic yoe_status chips via discoveredYoeStatuses()
```

---

## Config schema (`src/main/resources/match.yml`)

```yaml
# Loaded into MatchProfile at startup. Regex compile errors → app fails fast.

your_yoe: 4                        # informational; reserved for Phase 2 scoring
job_yoe_min: 2                     # filter accepts jobs whose required YOE band overlaps [min, max]
job_yoe_max: 6

# At least ONE must match the job title. Empty list = no job passes (fail closed).
include_regex:
  - "\\b(software\\s+)?engineer\\b"
  - "\\b(backend|platform|infra(structure)?)\\b"
  - "\\b(SDE|developer|programmer)\\b"

# Optional. If non-empty, at least ONE must match the job description body.
# Use to require specific stacks (e.g. java/spring) regardless of title wording.
include_in_description: []

# NONE may match the job title.
exclude_regex:
  - "\\b(sales|marketing|recruit(er|ing)|intern(ship)?)\\b"
  - "\\b(staff|principal|director|VP|vice\\s*president)\\b"
  - "\\b(designer|design\\s+lead|UX|UI)\\b"
```

The seed values above are starter content; tune freely.

---

## Regex semantics

A job passes the filter iff all three are true (case-insensitive, applied with Java `Pattern.CASE_INSENSITIVE`):

1. `include_regex` is non-empty AND at least one matches `job.title`.
2. `include_in_description` is empty OR at least one matches `job.description`. **If description is null/blank** (notably every Workday job — we don't call the CXS detail API), this check is **skipped, not failed** — so adding to `include_in_description` never silently drops the entire Workday ATS. Consistent with how YOE handles missing descriptions (→ `unknown`, not exclusion).
3. No `exclude_regex` matches `job.title`.

If `include_regex` is empty, **no job passes** — deliberate fail-closed. `match.yml` comment makes this explicit.

Patterns are compiled to `Pattern` once in `MatchService`'s constructor. A `PatternSyntaxException` aborts startup with a clear error message and the offending pattern.

---

## YOE extraction

`MatchService.extractYoe(String description)` runs these against the description in order; first hit wins; returns `Optional<int[]>` of `[min, max]` or empty:

```
P1   \b(\d+)\s*[-–to]+\s*(\d+)\s*\+?\s*(?:year|yr|yoe)s?\b           → [g1, g2]
P2   \b(\d+)\s*\+\s*(?:year|yr|yoe)s?\b                              → [g1, Integer.MAX_VALUE]
P3   \b(?:minimum|at\s+least|over)\s+(\d+)\s*(?:year|yr|yoe)s?\b     → [g1, Integer.MAX_VALUE]
P4   \b(\d+)\s*(?:year|yr|yoe)s?\s*(?:of\s+experience)?\b            → [g1, g1]
```

All `CASE_INSENSITIVE`. Null/blank description → empty.

---

## YOE classification

Given user range `U = [job_yoe_min, job_yoe_max]` and extracted `J = [a, b]`:

- `J` empty → `unknown`
- `J ∩ U` non-empty (i.e. `max(a, U.min) <= min(b, U.max)`) → `matched`
- Otherwise → `out_of_range`

---

## CSV restructure (design decision needing your eyes)

The current CSV is **per-company** with `jobs` semicolon-joined. `yoe_status` is **per-job**, so it doesn't fit a per-company row cleanly. Options considered:

| Path | Shape | Verdict |
|---|---|---|
| **A. Per-job CSV (recommended)** | one row per job: `company_name, website, ats_type, ats_identifier, job_title, job_url, job_location, yoe_status`. UI computes per-company aggregates client-side. | clean, fits the filter, UI gets straightforward sort/filter |
| B. Keep per-company; structured cell | `jobs` becomes `"Title A\|matched\|url;Title B\|unknown\|url"` | ugly, hard to read in spreadsheet apps, fragile parser |
| C. Two CSVs (`october.csv` summary + `october-jobs.csv` detail) | UI loads both | extra file to deploy + parse; aggregate stays a derived view anyway |

**Picking A.** The aggregate view in the UI can be recomputed from per-job rows trivially (`group by company_name`). This is also the grain Match's future slices (scoring, deduping per role) want.

If you'd rather hold the per-company shape for spreadsheet compatibility, say so during spec review — the implementation cost of B is the same as A.

### CSV columns under Path A

```
company_name, website, ats_type, ats_identifier, job_title, job_url, job_location, yoe_status
```

`important_emails` drops from the CSV — it was empty for Phase 1 anyway. It can return as a per-company column in a separate `october-companies.csv` when Contacts (Phase 4) needs it.

---

## UI changes

`docs/index.html` updates:

1. Parse per-job CSV. Build an in-memory `byCompany` map: `Map<companyName, { row[], jobs[] }>` for the table render.
2. New chip row above the ATS chip row: `ALL | MATCHED | OUT_OF_RANGE | UNKNOWN` with counts. Same `discovered…` pattern (`discoveredYoeStatuses`) so new statuses style themselves.
3. Filter logic: search + ATS chip + YOE chip ALL apply; a job appears iff it passes all three.
4. Counters update across both chip rows when either filter changes.
5. Default YOE chip selection: `ALL` (don't hide anything by default; one click to focus on `MATCHED`).

Color palette for YOE chips (within the existing dynamic scheme):

- `MATCHED` → hue 130 (green — same family as the GREENHOUSE chip; differentiated by chip-row position)
- `OUT_OF_RANGE` → hue 0 (muted red)
- `UNKNOWN` → grey (same as the UNKNOWN ATS chip)

If green-on-green feels off, switch `MATCHED` to a distinct hue at implementation time; not load-bearing.

---

## Verification plan

End-to-end, exercised against the live HSR Layout seed list:

1. **Unit-equivalent runtime checks (no test framework — Phase 1 rule still applies):**
   - Boot fails fast if `match.yml` has an invalid regex.
   - `MatchService.passesRegex` returns true for `Job(title="Senior Backend Engineer")` against the seed `include_regex`.
   - `MatchService.passesRegex` returns false for `Job(title="VP, Sales")`.
   - `MatchService.extractYoe("3-5 years of experience required")` returns `[3, 5]`.
   - `MatchService.extractYoe("Minimum 5 years")` returns `[5, MAX]`.
   - `MatchService.extractYoe(null)` returns empty.

   Run as a one-off `main`-style scratch or assert via temporary log lines during the first `mvn spring-boot:run`. Don't add JUnit until Phase 2 is older than a sprint.

2. **Pipeline run (local):**
   - Delete `data/october.mv.db` to start clean.
   - `mvn -q spring-boot:run`.
   - Confirm log shows `[match] filtered X of Y jobs`, where X is materially smaller than Y.
   - Confirm CSV under `exports/october.csv` has the new schema and a mix of `matched / out_of_range / unknown`.

3. **Deployed UI:**
   - Push to main, trigger `October pipeline` workflow.
   - Visit https://soumop.github.io/october/ and confirm:
     - New chip row appears with `MATCHED / OUT_OF_RANGE / UNKNOWN` and live counts.
     - Clicking a chip narrows the table.
     - ATS chip + YOE chip + search combine correctly.

4. **Regression on existing functionality:**
   - ATS chip row still works.
   - Sorting by job count / company name still works (note: per-job grain means "job count" is now derived).
   - Last-Modified footer still updates.

---

## Open questions for spec review

1. **CSV grain change** (per-company → per-job) — OK?
2. **Drop `important_emails` column** for now (will return via a separate file when Phase 4 / Contacts arrives)?
3. **Starter `include_regex` / `exclude_regex` lists** in the schema example — keep as-is, or do you want different defaults baked in?
4. **Default YOE chip** = `ALL` (show everything, one-click focus) vs `MATCHED` (open with the focused subset, one click to expand)?
5. **`include_in_description` on null descriptions** = skip the check (current spec; Workday jobs survive) vs treat as no match (strict; Workday jobs all drop if you put anything in this list)?

Reply with answers (or just "ship it as written") and we'll move to the implementation plan via `writing-plans`.
