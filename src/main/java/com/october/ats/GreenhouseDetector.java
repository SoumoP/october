package com.october.ats;

import com.october.config.PageFetcher;
import com.october.model.AtsType;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class GreenhouseDetector extends AbstractAtsDetector {

    // Matches:
    //   boards.greenhouse.io/acme
    //   job-boards.greenhouse.io/acme
    //   boards.greenhouse.io/embed/job_board?for=acme
    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://(?:boards|job-boards)\\.greenhouse\\.io/(?:embed/job_board\\?for=)?([A-Za-z0-9\\-_.]+)",
            Pattern.CASE_INSENSITIVE);

    public GreenhouseDetector(PageFetcher pageFetcher) {
        super(pageFetcher);
    }

    @Override
    public AtsType supports() {
        return AtsType.GREENHOUSE;
    }

    @Override
    protected Optional<String> extractFromUrl(String url) {
        if (url == null) return Optional.empty();
        Matcher m = URL_PATTERN.matcher(url);
        if (m.find()) return Optional.of(cleanIdentifier(m.group(1)));
        return Optional.empty();
    }

    @Override
    protected Optional<String> extractFromDocument(Document document) {
        Matcher m = URL_PATTERN.matcher(document.html());
        if (m.find()) return Optional.of(cleanIdentifier(m.group(1)));
        return Optional.empty();
    }

    private static String cleanIdentifier(String raw) {
        // Strip a trailing 'embed' if path got captured oddly
        return raw.replaceAll("/(jobs|embed).*$", "");
    }
}
