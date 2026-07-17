package com.mil.trdss.ro.repository.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Converter
public class JsonNodeConverter implements AttributeConverter<JsonNode, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(JsonNode attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize rankedPayload to JSON", e);
        }
    }

    @Override
    public JsonNode convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            return MAPPER.readTree(dbData);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize rankedPayload from JSON", e);
        }
    }
}
