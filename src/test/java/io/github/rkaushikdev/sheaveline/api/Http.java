package io.github.rkaushikdev.sheaveline.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UncheckedIOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.StreamSupport;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * A small client over MockMvc for the http tests: send a request, get the status, the headers and
 * the parsed body back.
 *
 * <p>A request the service fails on without answering comes back as a bare 500, the way a server
 * answers it, rather than as an exception in the test. A query string in the target is read the way
 * a browser writes one, so {@code ?speed=500+fpm} sends {@code 500 fpm}.
 */
public final class Http {

    /** A mapper for the bodies the tests write and read, which are maps and trees. */
    static final ObjectMapper JSON = Json.mapper();

    /** JSON {@code true}, to compare a field with when it must be the boolean and not merely truthy. */
    public static final JsonNode TRUE = tree("true");

    /** JSON {@code false}. */
    public static final JsonNode FALSE = tree("false");

    /** JSON {@code null}, which a field the service has no value for is written as. */
    public static final JsonNode NULL = tree("null");

    private final MockMvc mvc;

    /**
     * A client.
     *
     * @param mvc the application to talk to
     */
    public Http(MockMvc mvc) {
        this.mvc = Objects.requireNonNull(mvc, "mvc");
    }

    public Answer get(String target) {
        return get(target, Map.of());
    }

    public Answer get(String target, Map<String, String> headers) {
        return send(request(HttpMethod.GET, target, headers));
    }

    public Answer postJson(String target, Object body) {
        return postJson(target, body, Map.of());
    }

    public Answer postJson(String target, Object body, Map<String, String> headers) {
        return send(request(HttpMethod.POST, target, headers)
                .contentType(MediaType.APPLICATION_JSON)
                .content(write(body)));
    }

    public Answer delete(String target) {
        return delete(target, Map.of());
    }

    public Answer delete(String target, Map<String, String> headers) {
        return send(request(HttpMethod.DELETE, target, headers));
    }

    /**
     * Send a request built by hand.
     *
     * @param request the request
     * @return what came back
     */
    public Answer send(MockHttpServletRequestBuilder request) {
        MockHttpServletResponse response;
        try {
            response = mvc.perform(request).andReturn().getResponse();
        } catch (Exception unanswered) {
            return Answer.serverError();
        }
        Map<String, String> headers = new LinkedHashMap<>();
        response.getHeaderNames().forEach(name -> headers.put(name.toLowerCase(Locale.ROOT), response.getHeader(name)));
        return new Answer(
                response.getStatus(), new String(response.getContentAsByteArray(), StandardCharsets.UTF_8), headers);
    }

    /**
     * A JSON document written out in a test, as a tree to compare a body with.
     *
     * @param text the JSON
     * @return the tree
     */
    public static JsonNode tree(String text) {
        try {
            return JSON.readTree(text);
        } catch (JsonProcessingException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    /**
     * One text field off every object in a JSON list, in order.
     *
     * @param list the list
     * @param field the field
     * @return the values
     */
    public static List<String> texts(JsonNode list, String field) {
        return StreamSupport.stream(list.spliterator(), false)
                .map(one -> one.get(field).asText())
                .toList();
    }

    /**
     * One whole number field off every object in a JSON list, in order.
     *
     * @param list the list
     * @param field the field
     * @return the values
     */
    public static List<Integer> numbers(JsonNode list, String field) {
        return StreamSupport.stream(list.spliterator(), false)
                .map(one -> one.get(field).asInt())
                .toList();
    }

    private static MockHttpServletRequestBuilder request(
            HttpMethod method, String target, Map<String, String> headers) {
        int mark = target.indexOf('?');
        String path = mark < 0 ? target : target.substring(0, mark);
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.request(method, path);
        if (mark >= 0) {
            for (String pair : target.substring(mark + 1).split("&")) {
                int equals = pair.indexOf('=');
                String name = equals < 0 ? pair : pair.substring(0, equals);
                String value = equals < 0 ? "" : pair.substring(equals + 1);
                request.queryParam(decoded(name), decoded(value));
            }
        }
        headers.forEach(request::header);
        return request;
    }

    private static String decoded(String text) {
        return URLDecoder.decode(text, StandardCharsets.UTF_8);
    }

    private static String write(Object body) {
        try {
            return JSON.writeValueAsString(body);
        } catch (JsonProcessingException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }

    /**
     * What came back.
     *
     * @param status the status code
     * @param text the body
     * @param headers the headers, names in lower case
     */
    public record Answer(int status, String text, Map<String, String> headers) {

        static Answer serverError() {
            return new Answer(500, "Internal Server Error", Map.of());
        }

        public JsonNode json() {
            return tree(text);
        }

        public String header(String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }
    }
}
