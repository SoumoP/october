package com.october.ats;

import com.october.model.AtsDetection;
import com.october.model.Company;
import com.october.search.TavilySearchClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Fallback detector consumed by AtsDetectionService when the regular URL+HTML
 * detector chain returns UNKNOWN. Queries Brave for "&lt;company&gt; careers"
 * and walks each existing detector's URL regex across the returned URLs.
 *
 * Intentionally NOT registered as an AtsDetector (so it doesn't show up in
 * the standard chain) — AtsDetectionService calls this directly as a
 * second-chance step. That keeps Brave queries out of the hot path for
 * companies that resolve via URL/HTML alone.
 */
@Slf4j
@Component
public class SearchBasedDetector {

    private static final int RESULT_LIMIT = 10;

    private final TavilySearchClient tavily;
    private final List<AtsDetector> detectors;

    public SearchBasedDetector(TavilySearchClient tavily, List<AtsDetector> detectors) {
        this.tavily = tavily;
        this.detectors = detectors;
    }

    public boolean isEnabled() {
        return tavily.isEnabled();
    }

    public Optional<AtsDetection> detect(Company company) {
        if (!tavily.isEnabled()) return Optional.empty();
        String query = "\"" + company.getName() + "\" careers";
        List<TavilySearchClient.SearchResult> results = tavily.search(query, RESULT_LIMIT);
        log.info("Tavily returned {} results for {}", results.size(), query);
        for (TavilySearchClient.SearchResult r : results) {
            if (r.url == null || r.url.isBlank()) continue;
            for (AtsDetector d : detectors) {
                Optional<String> id = d.extractIdentifierFromUrl(r.url);
                if (id.isPresent()) {
                    log.info("Search hit: {} → {}={} (from {})",
                            company.getName(), d.supports(), id.get(), r.url);
                    return Optional.of(new AtsDetection(d.supports(), id.get(), r.url));
                }
            }
        }
        return Optional.empty();
    }
}
