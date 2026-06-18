package com.october.search;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Thin client over the Tavily Search API. Returns an empty list when the API
 * key is missing or any error occurs — never throws into the caller, so
 * detection always degrades gracefully when search is unavailable.
 *
 * API contract: https://docs.tavily.com/docs/rest-api/api-reference
 *   POST https://api.tavily.com/search
 *   Body: { "api_key": "...", "query": "...", "search_depth": "basic", "max_results": N }
 *   Response: { "results": [ { "title", "url", "content", "score" }, ... ] }
 */
@Slf4j
@Component
public class TavilySearchClient {

    private static final String API_URL = "https://api.tavily.com/search";

    private final WebClient webClient;
    private final String apiKey;
    private final boolean enabled;

    public TavilySearchClient(WebClient webClient,
                              @Value("${october.search.tavily.api-key:}") String apiKey) {
        this.webClient = webClient;
        this.apiKey = apiKey;
        this.enabled = apiKey != null && !apiKey.isBlank();
        if (!enabled) {
            log.warn("TAVILY_API_KEY not set; search-based ATS detection disabled (URL+HTML detectors still run).");
        } else {
            log.info("Tavily Search API client initialized.");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<SearchResult> search(String query, int maxResults) {
        if (!enabled) return Collections.emptyList();
        try {
            Map<String, Object> body = Map.of(
                    "api_key", apiKey,
                    "query", query,
                    "search_depth", "basic",
                    "max_results", maxResults
            );
            TavilyResponse response = webClient.post()
                    .uri(API_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(body))
                    .retrieve()
                    .bodyToMono(TavilyResponse.class)
                    .block();
            if (response == null || response.results == null) return Collections.emptyList();
            return response.results;
        } catch (Exception e) {
            log.warn("Tavily search failed for '{}': {}", query, e.getMessage());
            return Collections.emptyList();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TavilyResponse {
        public List<SearchResult> results;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SearchResult {
        public String title;
        public String url;
        public String content;
    }
}
