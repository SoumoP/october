package com.october.providers;

import com.october.model.AtsType;
import com.october.model.Company;
import com.october.model.Job;
import com.october.persistence.CompanyRepository;
import com.october.persistence.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobFetchService {

    private final CompanyRepository companyRepository;
    private final JobRepository jobRepository;
    private final JobProviderRegistry providerRegistry;

    @Transactional
    public FetchResult fetchAllForKnownAts() {
        List<Company> companies = companyRepository.findAllByOrderByNameAsc();
        int companiesQueried = 0;
        int companiesSkipped = 0;
        int companiesFailed = 0;
        int totalJobs = 0;

        for (Company c : companies) {
            if (c.getAtsType() == null || c.getAtsType() == AtsType.UNKNOWN || c.getAtsIdentifier() == null) {
                companiesSkipped++;
                continue;
            }
            Optional<JobProvider> provider = providerRegistry.get(c.getAtsType());
            if (provider.isEmpty()) {
                log.warn("No provider registered for ATS type {} (company: {})", c.getAtsType(), c.getName());
                companiesSkipped++;
                continue;
            }
            try {
                List<Job> jobs = provider.get().fetchJobs(c.getAtsIdentifier());
                jobRepository.deleteByCompanyId(c.getId());
                jobs.forEach(j -> j.setCompanyId(c.getId()));
                jobRepository.saveAll(jobs);
                log.info("Fetched {} jobs for {} ({} via {})", jobs.size(), c.getName(),
                        c.getAtsIdentifier(), c.getAtsType());
                companiesQueried++;
                totalJobs += jobs.size();
            } catch (Exception e) {
                log.warn("Failed to fetch jobs for {} ({} via {}): {}", c.getName(),
                        c.getAtsIdentifier(), c.getAtsType(), e.getMessage());
                companiesFailed++;
            }
        }

        return new FetchResult(companiesQueried, companiesSkipped, companiesFailed, totalJobs);
    }

    public record FetchResult(int companiesQueried, int companiesSkipped,
                               int companiesFailed, int totalJobs) { }
}
