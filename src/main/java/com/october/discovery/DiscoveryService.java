package com.october.discovery;

import com.october.model.Company;
import com.october.persistence.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscoveryService {

    private final List<CompanyDiscoverySource> sources;
    private final CompanyRepository companyRepository;

    @Transactional
    public DiscoveryResult discoverAndPersist() {
        Map<String, Company> dedup = new LinkedHashMap<>();
        for (CompanyDiscoverySource source : sources) {
            List<Company> discovered = source.discover();
            log.info("Source '{}' returned {} companies", source.name(), discovered.size());
            for (Company c : discovered) {
                if (c.getWebsite() == null || c.getWebsite().isBlank()) {
                    log.warn("Skipping company without website: {}", c.getName());
                    continue;
                }
                dedup.putIfAbsent(c.getWebsite(), c);
            }
        }

        int newlyAdded = 0;
        for (Company candidate : dedup.values()) {
            var existing = companyRepository.findByWebsite(candidate.getWebsite());
            if (existing.isEmpty()) {
                companyRepository.save(candidate);
                newlyAdded++;
            } else {
                Company e = existing.get();
                if (e.getCareersUrl() == null && candidate.getCareersUrl() != null) {
                    e.setCareersUrl(candidate.getCareersUrl());
                    companyRepository.save(e);
                }
            }
        }

        long total = companyRepository.count();
        log.info("Discovery complete: {} new companies, {} total persisted.", newlyAdded, total);
        return new DiscoveryResult(dedup.size(), newlyAdded, total);
    }

    public record DiscoveryResult(int candidates, int newlyAdded, long totalPersisted) { }
}
