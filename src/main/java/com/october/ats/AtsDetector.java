package com.october.ats;

import com.october.model.AtsDetection;
import com.october.model.AtsType;
import com.october.model.Company;

import java.util.Optional;

public interface AtsDetector {

    AtsType supports();

    Optional<AtsDetection> detect(Company company);

    /**
     * Apply only this detector's URL-pattern recognition to the given URL.
     * Used by SearchBasedDetector to walk an existing detector's regex across
     * URLs returned by Brave Search, without doing a full fetch+scan.
     */
    Optional<String> extractIdentifierFromUrl(String url);
}
