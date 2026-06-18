package com.october;

import com.october.ats.AtsDetectionService;
import com.october.discovery.DiscoveryService;
import com.october.export.CsvExportService;
import com.october.providers.JobFetchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OctoberPipeline implements ApplicationRunner {

    private final DiscoveryService discoveryService;
    private final AtsDetectionService detectionService;
    private final JobFetchService jobFetchService;
    private final CsvExportService csvExportService;

    @Value("${october.skip.discovery:false}") private boolean skipDiscovery;
    @Value("${october.skip.detection:false}") private boolean skipDetection;
    @Value("${october.skip.fetch:false}")     private boolean skipFetch;
    @Value("${october.skip.export:false}")    private boolean skipExport;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("==================== October pipeline starting ====================");

        if (skipDiscovery || args.containsOption("skip-discovery")) {
            log.info("[discovery] skipped");
        } else {
            log.info("[discovery] running...");
            var d = discoveryService.discoverAndPersist();
            log.info("[discovery] candidates={}, newlyAdded={}, removed={}, totalPersisted={}",
                    d.candidates(), d.newlyAdded(), d.removed(), d.totalPersisted());
        }

        if (skipDetection || args.containsOption("skip-detection")) {
            log.info("[detection] skipped");
        } else {
            log.info("[detection] running...");
            var det = detectionService.detectForAllUnresolved();
            log.info("[detection] total={}, newlyResolved={}, alreadyResolved={}, unresolved={}, breakdown={}",
                    det.total(), det.newlyResolved(), det.alreadyResolved(), det.unresolved(), det.breakdown());
        }

        if (skipFetch || args.containsOption("skip-fetch")) {
            log.info("[fetch] skipped");
        } else {
            log.info("[fetch] running...");
            var f = jobFetchService.fetchAllForKnownAts();
            log.info("[fetch] queried={}, skipped={}, failed={}, totalJobs={}",
                    f.companiesQueried(), f.companiesSkipped(), f.companiesFailed(), f.totalJobs());
        }

        if (skipExport || args.containsOption("skip-export")) {
            log.info("[export] skipped");
        } else {
            log.info("[export] running...");
            var e = csvExportService.export();
            log.info("[export] wrote {} rows to {}", e.rows(), e.path());
        }

        log.info("==================== October pipeline complete ====================");
    }
}
