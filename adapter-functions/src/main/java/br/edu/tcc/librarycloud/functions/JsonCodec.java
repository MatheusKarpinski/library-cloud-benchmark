package br.edu.tcc.librarycloud.functions;

/**
 * Conversão entre objetos e JSON. Existe para que {@link BookApiHandler} não dependa de uma
 * biblioteca JSON específica nem do runtime do Functions (ADR-020, ADR-021).
 */
public interface JsonCodec {

    /** @throws JsonCodecException se o objeto não puder ser serializado */
    String toJson(Object value);

    /**
     * @return o objeto lido, ou {@code null} quando o texto for o literal JSON {@code null}
     * @throws JsonCodecException se o JSON for malformado ou tiver tipos incompatíveis com {@code type}
     */
    <T> T fromJson(String json, Class<T> type);
}
