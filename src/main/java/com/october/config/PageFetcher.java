package com.october.config;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Thin wrapper around Jsoup for HTML page retrieval used by ATS detectors.
 * Centralizes timeout, user-agent, and error handling so detectors stay focused
 * on pattern matching.
 */
@Slf4j
@Component
public class PageFetcher {

    private final int timeoutMs;
    private final String userAgent;

    public PageFetcher(@Value("${october.http.timeout-ms:15000}") int timeoutMs,
                       @Value("${october.http.user-agent:OctoberSignal/0.1}") String userAgent) {
        this.timeoutMs = timeoutMs;
        this.userAgent = userAgent;
    }

    public Optional<Document> fetch(String url) {
        if (url == null || url.isBlank()) return Optional.empty();
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(userAgent)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    // Note: deliberately NOT advertising "br" (brotli) — Jsoup decodes
                    // only gzip/deflate, and servers (e.g. Razorpay behind Cloudflare)
                    // will gladly hand us brotli-encoded gibberish if we ask for it.
                    .header("Accept-Encoding", "gzip, deflate")
                    .header("Upgrade-Insecure-Requests", "1")
                    .timeout(timeoutMs)
                    .followRedirects(true)
                    .ignoreContentType(false)
                    .ignoreHttpErrors(true)
                    .get();
            return Optional.of(doc);
        } catch (Exception e) {
            log.debug("PageFetcher could not fetch {}: {}", url, e.getMessage());
            return Optional.empty();
        }
    }
}
