package com.october.providers;

import com.october.model.AtsType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class JobProviderRegistry {

    private final Map<AtsType, JobProvider> byType;

    public JobProviderRegistry(List<JobProvider> providers) {
        this.byType = providers.stream()
                .collect(Collectors.toMap(JobProvider::supports, Function.identity()));
    }

    public Optional<JobProvider> get(AtsType type) {
        return Optional.ofNullable(byType.get(type));
    }
}
