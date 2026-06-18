package com.october.search;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.List;

/**
 * Thin client over the Brave Search API. Returns an empty list when the API
 * key is missing or any error occurs — never throws into the caller, so
 * detection always degrades gracefully when search is unavailable.
 */
@Slf4j
@Component
public class BraveSearchClient {

    private static final String API_URL = "https://api.search.brave.com/res/v1/web/search";

    private final WebClient webClient;
    private final String apiKey;
    private final boolean enabled;

    public BraveSearchClient(WebClient webClient,
                             @Value("${october.search.brave.api-key:}") String apiKey) {
        this.webClient = webClient;
        this.apiKey = apiKey;
        this.enabled = apiKey != null && !apiKey.isBlank();
        if (!enabled) {
            log.warn("BRAVE_SEARCH_API_KEY not set; search-based ATS detection disabled (URL+HTML detectors still run).");
        } else {
            log.info("Brave Search API client initialized.");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<SearchResult> search(String query, int count) {
        if (!enabled) return Collections.emptyList();
        try {
            String uri = UriComponentsBuilder.fromHttpUrl(API_URL)
                    .queryParam("q", query)
                    .queryParam("count", count)
                    .toUriString();
            BraveResponse response = webClient.get()
                    .uri(uri)
                    .header("X-Subscription-Token", apiKey)
                    .header("Accept", "application/json")
                    .retrieve()
                    .bodyToMono(BraveResponse.class)
                    .block();
            if (response == null || response.web == null || response.web.results == null) {
                return Collections.emptyList();
            }
            return response.web.results;
        } catch (Exception e) {
            log.warn("Brave search failed for '{}': {}", query, e.getMessage());
            return Collections.emptyList();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BraveResponse {
        public Web web;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Web {
        public List<SearchResult> results;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SearchResult {
        public String title;
        public String url;
        public String description;
    }
}
