package com.october.discovery;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.october.model.Company;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class SeedListDiscoverySource implements CompanyDiscoverySource {

    private final ResourceLoader resourceLoader;
    private final String seedResource;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public SeedListDiscoverySource(ResourceLoader resourceLoader,
                                   @Value("${october.seed-resource}") String seedResource) {
        this.resourceLoader = resourceLoader;
        this.seedResource = seedResource;
    }

    @Override
    public String name() {
        return "seed-list:" + seedResource;
    }

    @Override
    public List<Company> discover() {
        Resource resource = resourceLoader.getResource(seedResource);
        if (!resource.exists()) {
            log.warn("Seed resource {} not found — discovery returns empty list.", seedResource);
            return Collections.emptyList();
        }
        try (InputStream in = resource.getInputStream()) {
            SeedFile parsed = yamlMapper.readValue(in, SeedFile.class);
            if (parsed.companies() == null) return Collections.emptyList();
            return parsed.companies().stream()
                    .map(this::toCompany)
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read seed file " + seedResource, e);
        }
    }

    private Company toCompany(SeedCompany sc) {
        return Company.builder()
                .name(sc.name())
                .website(normalizeWebsite(sc.website()))
                .careersUrl(sc.careersUrl())
                .build();
    }

    private static String normalizeWebsite(String website) {
        if (website == null) return null;
        String w = website.trim().toLowerCase();
        if (w.endsWith("/")) w = w.substring(0, w.length() - 1);
        return w;
    }

    private record SeedFile(List<SeedCompany> companies) { }

    private record SeedCompany(String name,
                               String website,
                               @JsonProperty("careers_url") String careersUrl) { }
}
