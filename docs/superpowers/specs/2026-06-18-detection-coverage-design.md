# Detection coverage improvements — design

**Date:** 2026-06-18
**Status:** Approved; implementing
**Predecessor:** Phase 1 (Signal) shipped 2026-06-18 with 3/21 companies detected (~14%). Match brainstorm parked at `docs/superpowers/specs/2026-06-18-october-match-design.md` until this lands.

**Update 2026-06-18 (later same day):** Original spec used **Brave Search API**. Brave's free tier was removed mid-day; switched provider to **Tavily Search API** (1,000 queries/month free, single API key, same architectural shape). All references to Brave in the sections below should be read as Tavily — the design is unchanged otherwise. Env var: `TAVILY_API_KEY`. Config key: `october.search.tavily.api-key`. POST endpoint: `https://api.tavily.com/search` with `{api_key, query, search_depth: "basic", max_results}`.

---

## Problem

18 of 21 seed companies have `ats_type = UNKNOWN`. Investigation showed three root causes:

| Bucket | Examples | Cause |
|---|---|---|
| JS-rendered careers pages | Atlan, Postman, Hasura | Real ATS URL is injected by JS at runtime; static HTML fetch misses it |
| Bot-blocked | Meesho (HTTP 403) | Server rejects requests with non-browser User-Agent |
| Genuinely custom | Zerodha | No third-party ATS — nothing to detect |

Match (Phase 2) is parked because its filter has too little signal to act on with 14% detection. Coverage first; Match second.

## Solution — three small additions

1. **`SearchBasedDetector`** — Brave Search API fallback in the detection chain. For each company still UNKNOWN after URL + HTML scan, query `"<company> careers"` and inspect the top results' URLs against the existing detectors' regex patterns. Sidesteps both JS-rendering and bot-blocking entirely, because Google/Brave already crawled what the rendered page looks like.

2. **PageFetcher UA + header upgrade.** Drop the obvious `OctoberSignal/0.1` UA in favor of a realistic Chrome on macOS UA plus `Accept-Language: en-US,en;q=0.9`. Not stealth-aggressive — just unflagged. Unblocks bucket 2 (some 403s).

3. **Persist DB across CI runs** via `actions/cache@v4` on `data/october.mv.db`. Means once a company's ATS is detected, it stays detected — search isn't re-run every 4h for the same company. Keeps Brave usage well under the 2,000/mo free tier.

## Out of scope (deferred)

- New ATS detectors. After the search-based detector runs against the real seed list, the logs will show what URLs Brave returns ("apply.workable.com/...", "recruit.zoho.com/...", etc.). The next ATS to add is whichever shows up most. Adding speculatively (Freshteam was the original plan; it's effectively dead in 2026) wastes effort.
- Google Maps-based discovery — different problem (find new HSR-Layout companies), separate slice.
- Headless browser for sites Brave also can't find — extreme tail.

## Components

### New

```
src/main/java/com/october/search/
  ├── BraveSearchClient.java       # WebClient → api.search.brave.com/res/v1/web/search
  └── BraveSearchClient.SearchResult  # nested record: title, url, description

src/main/java/com/october/ats/SearchBasedDetector.java
  # NOT an AtsDetector; called by AtsDetectionService directly as a fallback.
  # Calls Brave for the company name, walks each existing AtsDetector's
  # extractIdentifierFromUrl(url) against each result. First hit wins.
```

### Modified

```
AtsDetector.java                   # add extractIdentifierFromUrl(String) to the interface
AbstractAtsDetector.java           # default impl delegates to existing protected extractFromUrl()
AtsDetectionService.java           # after the URL/HTML chain fails for a company,
                                   #   try searchBasedDetector.detect(company)
PageFetcher.java                   # browser-like UA + Accept-Language
application.yml                    # october.search.brave.api-key: ${BRAVE_SEARCH_API_KEY:}
.github/workflows/october.yml      # BRAVE_SEARCH_API_KEY from repo secret;
                                   # actions/cache for data/october.mv.db (key includes 'detection')
```

## Brave client

- Endpoint: `GET https://api.search.brave.com/res/v1/web/search?q={q}&count=10`
- Header: `X-Subscription-Token: ${BRAVE_SEARCH_API_KEY}`
- If `BRAVE_SEARCH_API_KEY` is empty/missing, log a warning at startup and have `search()` return an empty list — never crash, never block detection. SearchBasedDetector becomes a no-op.
- On HTTP error: log and return empty list (don't propagate; degrade gracefully).

## Free-tier math

- Brave free tier: 2,000 queries/month
- With actions/cache restoring the DB between CI runs, only **still-UNKNOWN** companies get searched
- First run (cold DB): 18 queries. Subsequent runs (warm DB): 0 unless new companies are added or cache evicts
- Worst case (cache evicts daily): 18 × 6 = 108/day = ~3,200/month — still under cap if we cache-warm well, but worth keeping an eye on once live
- Cache evicts after 7 days of inactivity in Actions, which won't apply since cron runs every 4h

## Verification

1. `mvn -B -q -DskipTests spring-boot:run` locally **without** Brave key
   - Pipeline still completes; `BraveSearchClient` logs the missing-key warning; `SearchBasedDetector` returns empty for every company
   - Sanity: no crashes, no exceptions, same 3 detected companies as before
2. User obtains Brave API key (sign up at https://api.search.brave.com) and sets `gh secret set BRAVE_SEARCH_API_KEY` for `SoumoP/october`
3. Manual workflow trigger; expected log output:
   - For each UNKNOWN company: `Brave returned N results for "<company>" careers`
   - For some: `Detected <ATS>=<id> for <company> via search`
4. Live site at https://soumop.github.io/october/ should show 8–12 detected companies (up from 3) with their ATS chips populated
5. Brave dashboard at api.search.brave.com confirms ~18 queries the first run, near-zero after

## Open questions

None — the design is locked. Implementing.
