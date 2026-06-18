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
import java.util.stream.Collectors;

@Slf4j
@Component
public class GreenhouseProvider implements JobProvider {

    private static final String BASE_URL = "https://boards-api.greenhouse.io/v1/boards/";

    private final WebClient webClient;

    public GreenhouseProvider(WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public AtsType supports() {
        return AtsType.GREENHOUSE;
    }

    @Override
    public List<Job> fetchJobs(String atsIdentifier) {
        try {
            GreenhouseResponse response = webClient.get()
                    .uri(BASE_URL + atsIdentifier + "/jobs?content=true")
                    .retrieve()
                    .bodyToMono(GreenhouseResponse.class)
                    .block();
            if (response == null || response.jobs == null) return Collections.emptyList();
            Instant fetchedAt = Instant.now();
            return response.jobs.stream()
                    .map(j -> toJob(j, fetchedAt))
                    .toList();
        } catch (WebClientResponseException e) {
            log.warn("Greenhouse fetch failed for '{}': HTTP {}", atsIdentifier, e.getStatusCode().value());
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("Greenhouse fetch failed for '{}': {}", atsIdentifier, e.getMessage());
            return Collections.emptyList();
        }
    }

    private Job toJob(GhJob j, Instant fetchedAt) {
        String location = j.location != null ? j.location.name : null;
        String department = (j.departments != null && !j.departments.isEmpty())
                ? j.departments.stream().map(d -> d.name).filter(java.util.Objects::nonNull).collect(Collectors.joining(", "))
                : null;
        return Job.builder()
                .externalId(j.id != null ? j.id.toString() : null)
                .title(j.title)
                .location(location)
                .department(department)
                .url(j.absoluteUrl)
                .description(j.content)
                .fetchedAt(fetchedAt)
                .build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class GreenhouseResponse {
        public List<GhJob> jobs;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class GhJob {
        public Long id;
        public String title;
        @com.fasterxml.jackson.annotation.JsonProperty("absolute_url")
        public String absoluteUrl;
        public String content;
        public Location location;
        public List<Department> departments;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Location {
        public String name;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Department {
        public String name;
    }
}
