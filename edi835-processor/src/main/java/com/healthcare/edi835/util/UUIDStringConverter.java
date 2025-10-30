package com.healthcare.edi835.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.UUID;

/**
 * JPA Converter to handle UUID to String conversion for SQLite compatibility.
 *
 * SQLite doesn't have a native UUID type, so we store UUIDs as TEXT.
 * This converter handles the conversion between Java UUID objects
 * and String database columns.
 *
 * Note: autoApply=false to avoid interfering with PostgreSQL profile.
 * This converter is registered in SQLiteDataSourceConfig when SQLite profile is active.
 */
@Converter
public class UUIDStringConverter implements AttributeConverter<UUID, String> {

    @Override
    public String convertToDatabaseColumn(UUID attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.toString();
    }

    @Override
    public UUID convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(dbData);
        } catch (IllegalArgumentException e) {
            // Handle cases where the string is not a valid UUID format
            throw new IllegalArgumentException("Invalid UUID string: " + dbData, e);
        }
    }
}
