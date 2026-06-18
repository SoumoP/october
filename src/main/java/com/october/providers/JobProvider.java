package com.october.providers;

import com.october.model.AtsType;
import com.october.model.Job;

import java.util.List;

public interface JobProvider {

    AtsType supports();

    /**
     * Fetch jobs for an ATS identifier. The returned Jobs will have
     * companyId unset — that is the caller's responsibility.
     */
    List<Job> fetchJobs(String atsIdentifier);
}
