package com.october.export;

import com.october.model.AtsType;
import com.october.model.Company;
import com.october.model.Job;
import com.october.persistence.CompanyRepository;
import com.october.persistence.JobRepository;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CsvExportService {

    @Value("${october.export-path:./exports/october-signal.csv}")
    private String exportPath;

    private final CompanyRepository companyRepository;
    private final JobRepository jobRepository;

    @Transactional(readOnly = true)
    public ExportResult export() throws IOException {
        Path output = Paths.get(exportPath);
        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }

        List<Company> companies = companyRepository.findAllByOrderByNameAsc();
        int rowsWritten = 0;

        try (CSVWriter writer = new CSVWriter(new FileWriter(output.toFile()))) {
            writer.writeNext(new String[] {
                    "company_name", "website", "ats_type", "ats_identifier",
                    "job_count", "jobs", "important_emails"
            });
            for (Company c : companies) {
                List<Job> jobs = jobRepository.findByCompanyIdOrderByTitleAsc(c.getId());
                String jobTitles = jobs.stream()
                        .map(Job::getTitle)
                        .filter(t -> t != null && !t.isBlank())
                        .collect(Collectors.joining(";"));
                writer.writeNext(new String[] {
                        nullToEmpty(c.getName()),
                        nullToEmpty(c.getWebsite()),
                        c.getAtsType() == null ? AtsType.UNKNOWN.name() : c.getAtsType().name(),
                        nullToEmpty(c.getAtsIdentifier()),
                        Integer.toString(jobs.size()),
                        jobTitles,
                        nullToEmpty(c.getImportantEmails())
                });
                rowsWritten++;
            }
        }

        log.info("CSV export wrote {} rows to {}", rowsWritten, output.toAbsolutePath());
        return new ExportResult(output.toAbsolutePath().toString(), rowsWritten);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    public record ExportResult(String path, int rows) { }
}
