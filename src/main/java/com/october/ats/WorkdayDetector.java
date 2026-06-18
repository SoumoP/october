package com.october.ats;

import com.october.config.PageFetcher;
import com.october.model.AtsType;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Workday tenants live at {tenant}.wd{N}.myworkdayjobs.com/{site}.
 * The identifier we store is the slash-joined triple "tenant/wdN/site"
 * which the WorkdayProvider reverses to reconstruct the API base URL.
 */
@Component
public class WorkdayDetector extends AbstractAtsDetector {

    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://([A-Za-z0-9_-]+)\\.(wd\\d+)\\.myworkdayjobs\\.com/(?:en-US/)?([A-Za-z0-9_-]+)",
            Pattern.CASE_INSENSITIVE);

    public WorkdayDetector(PageFetcher pageFetcher) {
        super(pageFetcher);
    }

    @Override
    public AtsType supports() {
        return AtsType.WORKDAY;
    }

    @Override
    protected Optional<String> extractFromUrl(String url) {
        return extract(url);
    }

    @Override
    protected Optional<String> extractFromDocument(Document document) {
        return extract(document.html());
    }

    private static Optional<String> extract(String text) {
        if (text == null) return Optional.empty();
        Matcher m = URL_PATTERN.matcher(text);
        if (!m.find()) return Optional.empty();
        return Optional.of(m.group(1) + "/" + m.group(2) + "/" + m.group(3));
    }
}
