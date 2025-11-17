package com.healthcare.edi835.exception;

import lombok.Getter;

/**
 * Exception thrown when required configuration (payer or payee) is missing
 * during EDI file generation.
 */
@Getter
public class MissingConfigurationException extends RuntimeException {

    private final ConfigurationType configurationType;
    private final String missingId;

    public enum ConfigurationType {
        PAYER,
        PAYEE
    }

    public MissingConfigurationException(ConfigurationType type, String missingId) {
        super(String.format("%s not found: %s", type.name(), missingId));
        this.configurationType = type;
        this.missingId = missingId;
    }

    public MissingConfigurationException(ConfigurationType type, String missingId, Throwable cause) {
        super(String.format("%s not found: %s", type.name(), missingId), cause);
        this.configurationType = type;
        this.missingId = missingId;
    }
}
