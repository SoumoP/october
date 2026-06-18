package com.october.ats;

import com.october.config.PageFetcher;
import com.october.model.AtsType;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LeverDetector extends AbstractAtsDetector {

    private static final Pattern URL_PATTERN =
            Pattern.compile("https?://jobs\\.lever\\.co/([A-Za-z0-9\\-_.]+)", Pattern.CASE_INSENSITIVE);

    public LeverDetector(PageFetcher pageFetcher) {
        super(pageFetcher);
    }

    @Override
    public AtsType supports() {
        return AtsType.LEVER;
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
