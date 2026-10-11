package com.bioinfo.cytoscope.server;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.IOException;

/**
 * The JSON mapper of the API. Values that are not numbers (NaN, infinite), such as a percentage
 * of an empty population, are written as {@code null}, which JavaScript reads safely.
 */
public final class Json {

    private static final ObjectMapper MAPPER = create();

    private Json() {}

    /**
     * Returns the shared mapper, which is thread-safe once configured.
     *
     * @return the mapper
     */
    public static ObjectMapper mapper() {
        return MAPPER;
    }

    private static ObjectMapper create() {
        SimpleModule numbers = new SimpleModule("finite-numbers");
        JsonSerializer<Double> finite = new FiniteSerializer();
        numbers.addSerializer(Double.class, finite);
        numbers.addSerializer(Double.TYPE, finite);
        return new ObjectMapper()
                .registerModule(numbers)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true);
    }

    /** Writes NaN and infinite values as {@code null}. */
    private static final class FiniteSerializer extends JsonSerializer<Double> {

        @Override
        public void serialize(Double value, JsonGenerator generator, SerializerProvider provider)
                throws IOException {
            if (Double.isFinite(value)) {
                generator.writeNumber(value);
            } else {
                generator.writeNull();
            }
        }
    }
}
