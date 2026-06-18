# October

Discovers startups in HSR Layout (Bangalore), detects which ATS they use, fetches open jobs, exports a CSV.

## Run

```bash
mvn -q spring-boot:run
```

Skip stages with `--skip-discovery`, `--skip-detection`, `--skip-fetch`, `--skip-export` (pass via `-Dspring-boot.run.arguments=...`).

## Output

`exports/october.csv` — one row per company, `jobs` column is semicolon-separated titles.
