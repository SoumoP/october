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
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class AshbyProvider implements JobProvider {

    private static final String BASE_URL = "https://api.ashbyhq.com/posting-api/job-board/";

    private final WebClient webClient;

    public AshbyProvider(WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public AtsType supports() {
        return AtsType.ASHBY;
    }

    @Override
    public List<Job> fetchJobs(String atsIdentifier) {
        try {
            AshbyResponse response = webClient.post()
                    .uri(BASE_URL + atsIdentifier + "?includeCompensation=false")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue("{}"))
                    .retrieve()
                    .bodyToMono(AshbyResponse.class)
                    .block();
            if (response == null || response.jobs == null) return Collections.emptyList();
            Instant fetchedAt = Instant.now();
            return response.jobs.stream()
                    .map(j -> toJob(j, fetchedAt))
                    .toList();
        } catch (WebClientResponseException e) {
            log.warn("Ashby fetch failed for '{}': HTTP {}", atsIdentifier, e.getStatusCode().value());
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("Ashby fetch failed for '{}': {}", atsIdentifier, e.getMessage());
            return Collections.emptyList();
        }
    }

    private Job toJob(AshbyJob j, Instant fetchedAt) {
        return Job.builder()
                .externalId(j.id)
                .title(j.title)
                .location(j.location)
                .department(j.department)
                .url(j.jobUrl)
                .description(j.descriptionPlain)
                .fetchedAt(fetchedAt)
                .build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class AshbyResponse {
        public List<AshbyJob> jobs;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class AshbyJob {
        public String id;
        public String title;
        public String department;
        public String team;
        public String location;
        public String jobUrl;
        public String descriptionPlain;
    }
}
