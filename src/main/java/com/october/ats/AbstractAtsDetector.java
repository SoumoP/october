package com.october.ats;

import com.october.config.PageFetcher;
import com.october.model.AtsDetection;
import com.october.model.Company;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;

import java.util.Optional;

/**
 * Common detection pipeline shared by all ATS detectors:
 *   1. regex the careersUrl directly (handles 'jobs.lever.co/foo' style careers links)
 *   2. fetch the careers page and scan its HTML for ATS-specific signatures
 *   3. fall back to scanning the homepage
 *
 * Subclasses supply the per-ATS regex and HTML scanning logic.
 */
@Slf4j
public abstract class AbstractAtsDetector implements AtsDetector {

    protected final PageFetcher pageFetcher;

    protected AbstractAtsDetector(PageFetcher pageFetcher) {
        this.pageFetcher = pageFetcher;
    }

    @Override
    public final Optional<AtsDetection> detect(Company company) {
        String careersUrl = company.getCareersUrl();
        String website = company.getWebsite();

        Optional<String> id = extractFromUrl(careersUrl);
        if (id.isPresent()) {
            return Optional.of(new AtsDetection(supports(), id.get(), careersUrl));
        }

        Optional<AtsDetection> fromCareers = scanPage(careersUrl, careersUrl);
        if (fromCareers.isPresent()) return fromCareers;

        return scanPage(website, careersUrl != null ? careersUrl : website);
    }

    private Optional<AtsDetection> scanPage(String url, String careersUrl) {
        if (url == null || url.isBlank()) return Optional.empty();
        Optional<Document> doc = pageFetcher.fetch(url);
        if (doc.isEmpty()) return Optional.empty();
        Optional<String> id = extractFromDocument(doc.get());
        if (id.isPresent()) {
            log.debug("{} detector matched {} on page {}", supports(), id.get(), url);
            return Optional.of(new AtsDetection(supports(), id.get(), careersUrl));
        }
        return Optional.empty();
    }

    protected abstract Optional<String> extractFromUrl(String url);

    protected abstract Optional<String> extractFromDocument(Document document);
}
