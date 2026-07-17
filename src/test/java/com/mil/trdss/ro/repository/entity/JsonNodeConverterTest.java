package com.mil.trdss.ro.repository.entity;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class JsonNodeConverterTest {

    private final JsonNodeConverter converter = new JsonNodeConverter();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void roundTripsAJsonNodeThroughTheDatabaseColumn() throws Exception {
        JsonNode original = objectMapper.readTree("{\"modelName\":\"TB2\",\"totalAvailableCount\":2}");

        String columnValue = converter.convertToDatabaseColumn(original);
        JsonNode restored = converter.convertToEntityAttribute(columnValue);

        assertThat(restored).isEqualTo(original);
    }

    @Test
    void returnsNullForNullInput() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
