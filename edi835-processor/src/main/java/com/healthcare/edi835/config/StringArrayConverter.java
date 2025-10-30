package com.healthcare.edi835.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

/**
 * JPA Attribute Converter for String[] to JSON string and vice versa.
 * Handles array fields that are stored as JSON strings in SQLite (TEXT columns).
 *
 * In PostgreSQL, TEXT[] arrays are natively supported.
 * In SQLite, arrays are stored as JSON strings and need conversion.
 */
@Slf4j
@Converter
public class StringArrayConverter implements AttributeConverter<String[], String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(String[] attribute) {
        if (attribute == null || attribute.length == 0) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            log.error("Error converting String[] to JSON string", e);
            return null;
        }
    }

    @Override
    public String[] convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return null;
        }

        try {
            return objectMapper.readValue(dbData, String[].class);
        } catch (JsonProcessingException e) {
            log.error("Error converting JSON string to String[]: {}", dbData, e);
            return null;
        }
    }
}
