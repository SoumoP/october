package com.october.ats;

import com.october.model.AtsDetection;
import com.october.model.AtsType;
import com.october.model.Company;

import java.util.Optional;

public interface AtsDetector {

    AtsType supports();

    Optional<AtsDetection> detect(Company company);
}
