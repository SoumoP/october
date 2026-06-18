package com.october.ats;

import com.october.model.AtsDetection;
import com.october.model.AtsType;
import com.october.model.Company;
import com.october.persistence.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AtsDetectionService {

    private final List<AtsDetector> detectors;
    private final SearchBasedDetector searchBasedDetector;
    private final CompanyRepository companyRepository;

    @Transactional
    public DetectionResult detectForAllUnresolved() {
        List<Company> all = companyRepository.findAllByOrderByNameAsc();
        int resolved = 0;
        int alreadyResolved = 0;
        int unresolved = 0;
        int viaSearch = 0;

        for (Company c : all) {
            if (c.getAtsType() != null && c.getAtsType() != AtsType.UNKNOWN && c.getAtsIdentifier() != null) {
                alreadyResolved++;
                continue;
            }
            Optional<AtsDetection> detection = detect(c);
            boolean fromSearch = false;
            if (detection.isEmpty() || !detection.get().isResolved()) {
                Optional<AtsDetection> searchDetection = searchBasedDetector.detect(c);
                if (searchDetection.isPresent() && searchDetection.get().isResolved()) {
                    detection = searchDetection;
                    fromSearch = true;
                }
            }
            if (detection.isPresent() && detection.get().isResolved()) {
                AtsDetection d = detection.get();
                c.setAtsType(d.atsType());
                c.setAtsIdentifier(d.identifier());
                if (c.getCareersUrl() == null && d.careersUrl() != null) {
                    c.setCareersUrl(d.careersUrl());
                }
                companyRepository.save(c);
                resolved++;
                if (fromSearch) viaSearch++;
                log.info("Detected {}={} for {}{}", d.atsType(), d.identifier(), c.getName(),
                        fromSearch ? " (via search)" : "");
            } else {
                c.setAtsType(AtsType.UNKNOWN);
                companyRepository.save(c);
                unresolved++;
                log.info("No ATS detected for {}", c.getName());
            }
        }

        log.info("[detection] search-based fallback resolved {} of {} newly-resolved companies", viaSearch, resolved);
        return new DetectionResult(all.size(), resolved, alreadyResolved, unresolved, breakdown(all));
    }

    private Optional<AtsDetection> detect(Company company) {
        for (AtsDetector detector : detectors) {
            Optional<AtsDetection> result = detector.detect(company);
            if (result.isPresent() && result.get().isResolved()) {
                return result;
            }
        }
        return Optional.empty();
    }

    private Map<AtsType, Long> breakdown(List<Company> companies) {
        return companies.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        c -> c.getAtsType() == null ? AtsType.UNKNOWN : c.getAtsType(),
                        java.util.stream.Collectors.counting()));
    }

    public record DetectionResult(int total, int newlyResolved, int alreadyResolved,
                                   int unresolved, Map<AtsType, Long> breakdown) { }
}
