package br.edu.tcc.librarycloud.functions;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Implementação com Jackson 3. Configuração mínima e deliberadamente parecida com a do Spring
 * Boot: propriedades desconhecidas no JSON de entrada são ignoradas; o restante usa os padrões
 * da biblioteca (inclusive {@code null} escrito como {@code null}, sem espaços no JSON gerado).
 */
public final class JacksonJsonCodec implements JsonCodec {

    private final JsonMapper mapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    @Override
    public String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new JsonCodecException("Falha ao serializar a resposta", e);
        }
    }

    @Override
    public <T> T fromJson(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (JacksonException e) {
            throw new JsonCodecException("JSON de entrada inválido", e);
        }
    }
}
