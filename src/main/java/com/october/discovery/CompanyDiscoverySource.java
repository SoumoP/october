package com.october.discovery;

import com.october.model.Company;

import java.util.List;

/**
 * A source that produces candidate companies for the pipeline.
 * Implementations should be idempotent: returning the same logical set
 * of companies on each call given the same inputs.
 */
public interface CompanyDiscoverySource {

    String name();

    List<Company> discover();
}
