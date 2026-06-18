package com.october.providers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.october.model.AtsType;
import com.october.model.Job;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class WorkdayProvider implements JobProvider {

    private static final Pattern ID_PATTERN = Pattern.compile("([^/]+)/(wd\\d+)/([^/]+)");
    private static final int PAGE_SIZE = 20;
    private static final int MAX_PAGES = 5;   // cap at 100 jobs per company

    private final WebClient webClient;

    public WorkdayProvider(WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public AtsType supports() {
        return AtsType.WORKDAY;
    }

    @Override
    public List<Job> fetchJobs(String atsIdentifier) {
        Matcher m = ID_PATTERN.matcher(atsIdentifier);
        if (!m.matches()) {
            log.warn("Workday identifier malformed (expected tenant/wdN/site): {}", atsIdentifier);
            return Collections.emptyList();
        }
        String tenant = m.group(1);
        String wd = m.group(2);
        String site = m.group(3);
        String baseUrl = "https://" + tenant + "." + wd + ".myworkdayjobs.com";
        String apiUrl = baseUrl + "/wday/cxs/" + tenant + "/" + site + "/jobs";

        List<Job> all = new ArrayList<>();
        Instant fetchedAt = Instant.now();

        for (int page = 0; page < MAX_PAGES; page++) {
            int offset = page * PAGE_SIZE;
            try {
                WorkdayResponse response = webClient.post()
                        .uri(apiUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .body(BodyInserters.fromValue(Map.of(
                                "appliedFacets", Map.of(),
                                "limit", PAGE_SIZE,
                                "offset", offset,
                                "searchText", ""
                        )))
                        .retrieve()
                        .bodyToMono(WorkdayResponse.class)
                        .block();

                if (response == null || response.jobPostings == null || response.jobPostings.isEmpty()) break;

                for (WorkdayPosting p : response.jobPostings) {
                    all.add(toJob(p, baseUrl, site, fetchedAt));
                }

                if (response.jobPostings.size() < PAGE_SIZE) break;
                // Workday only reports `total` reliably on page 0 — later pages return 0,
                // so only trust the count when it's a positive number.
                if (response.total != null && response.total > 0 && all.size() >= response.total) break;
            } catch (WebClientResponseException e) {
                log.warn("Workday fetch failed for '{}' page {}: HTTP {}", atsIdentifier, page, e.getStatusCode().value());
                break;
            } catch (Exception e) {
                log.warn("Workday fetch failed for '{}' page {}: {}", atsIdentifier, page, e.getMessage());
                break;
            }
        }

        return all;
    }

    private Job toJob(WorkdayPosting p, String baseUrl, String site, Instant fetchedAt) {
        String url = (p.externalPath != null) ? baseUrl + "/" + site + p.externalPath : null;
        String externalId = (p.bulletFields != null && !p.bulletFields.isEmpty()) ? p.bulletFields.get(0) : null;
        return Job.builder()
                .externalId(externalId)
                .title(p.title)
                .location(p.locationsText)
                .department(null)
                .url(url)
                .description(null)
                .fetchedAt(fetchedAt)
                .build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class WorkdayResponse {
        public Integer total;
        public List<WorkdayPosting> jobPostings;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class WorkdayPosting {
        public String title;
        public String externalPath;
        public String locationsText;
        public String postedOn;
        public List<String> bulletFields;
    }
}
