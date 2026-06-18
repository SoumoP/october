package com.october.ats;

import com.october.config.PageFetcher;
import com.october.model.AtsType;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AshbyDetector extends AbstractAtsDetector {

    // Matches:
    //   jobs.ashbyhq.com/acme
    //   embed.ashbyhq.com/v1/embed/acme
    //   ashbyhq.com/api/posting-board/acme
    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://(?:jobs|embed|api)?\\.?ashbyhq\\.com/(?:v\\d+/embed/|posting-api/job-board/|api/posting-board/)?([A-Za-z0-9\\-_.]+)",
            Pattern.CASE_INSENSITIVE);

    public AshbyDetector(PageFetcher pageFetcher) {
        super(pageFetcher);
    }

    @Override
    public AtsType supports() {
        return AtsType.ASHBY;
    }

    @Override
    protected Optional<String> extractFromUrl(String url) {
        if (url == null) return Optional.empty();
        Matcher m = URL_PATTERN.matcher(url);
        if (m.find()) return Optional.of(m.group(1));
        return Optional.empty();
    }

    @Override
    protected Optional<String> extractFromDocument(Document document) {
        Matcher m = URL_PATTERN.matcher(document.html());
        if (m.find()) return Optional.of(m.group(1));
        return Optional.empty();
    }
}
