package com.october.discovery;

import com.october.model.Company;
import com.october.persistence.CompanyRepository;
import com.october.persistence.JobRepository;
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
    private final JobRepository jobRepository;

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

        int removed = 0;
        if (!dedup.isEmpty()) {
            // Companies persisted in the DB but no longer present in any discovery
            // source are removed along with their jobs. Guarded by the non-empty
            // check above so a transient empty-source result (e.g. seed-file parse
            // error) can never wipe the database.
            for (Company existing : companyRepository.findAllByOrderByNameAsc()) {
                if (!dedup.containsKey(existing.getWebsite())) {
                    jobRepository.deleteByCompanyId(existing.getId());
                    companyRepository.delete(existing);
                    removed++;
                    log.info("Removed {} — no longer in any discovery source", existing.getName());
                }
            }
        } else {
            log.warn("Discovery returned 0 companies across all sources — skipping deletion pass to avoid wiping the database.");
        }

        long total = companyRepository.count();
        log.info("Discovery complete: {} candidates, {} new, {} removed, {} total persisted.",
                dedup.size(), newlyAdded, removed, total);
        return new DiscoveryResult(dedup.size(), newlyAdded, removed, total);
    }

    public record DiscoveryResult(int candidates, int newlyAdded, int removed, long totalPersisted) { }
}
