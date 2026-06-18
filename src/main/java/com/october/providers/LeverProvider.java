package com.october.providers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.october.model.AtsType;
import com.october.model.Job;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class LeverProvider implements JobProvider {

    private static final String BASE_URL = "https://api.lever.co/v0/postings/";

    private final WebClient webClient;

    public LeverProvider(WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public AtsType supports() {
        return AtsType.LEVER;
    }

    @Override
    public List<Job> fetchJobs(String atsIdentifier) {
        try {
            LeverPosting[] postings = webClient.get()
                    .uri(BASE_URL + atsIdentifier + "?mode=json")
                    .retrieve()
                    .bodyToMono(LeverPosting[].class)
                    .block();
            if (postings == null) return Collections.emptyList();
            Instant fetchedAt = Instant.now();
            return java.util.Arrays.stream(postings)
                    .map(p -> toJob(p, fetchedAt))
                    .toList();
        } catch (WebClientResponseException e) {
            log.warn("Lever fetch failed for '{}': HTTP {}", atsIdentifier, e.getStatusCode().value());
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("Lever fetch failed for '{}': {}", atsIdentifier, e.getMessage());
            return Collections.emptyList();
        }
    }

    private Job toJob(LeverPosting p, Instant fetchedAt) {
        String location = p.categories != null ? p.categories.location : null;
        String department = p.categories != null ? p.categories.team : null;
        if (department == null && p.categories != null) department = p.categories.department;
        return Job.builder()
                .externalId(p.id)
                .title(p.text)
                .location(location)
                .department(department)
                .url(p.hostedUrl)
                .description(p.descriptionPlain)
                .fetchedAt(fetchedAt)
                .build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class LeverPosting {
        public String id;
        public String text;
        public String hostedUrl;
        public String descriptionPlain;
        public Categories categories;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Categories {
        public String location;
        public String team;
        public String department;
        public String commitment;
    }
}
