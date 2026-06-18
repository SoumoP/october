package com.october.model;

public record AtsDetection(AtsType atsType, String identifier, String careersUrl) {

    public static AtsDetection unknown(String careersUrl) {
        return new AtsDetection(AtsType.UNKNOWN, null, careersUrl);
    }

    public boolean isResolved() {
        return atsType != AtsType.UNKNOWN && identifier != null && !identifier.isBlank();
    }
}
