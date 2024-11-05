package ro.cs.tao.stac.core.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import ro.cs.tao.stac.core.model.*;
import ro.cs.tao.stac.core.model.extensions.eo.Band;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

/**
 * Parser for JSON nodes representing STAC objects.
 *
 * @author Cosmin Cara
 */
public class STACParser {
    private static final ObjectMapper mapper;

    static {
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        SimpleModule module = new SimpleModule();
        module.addDeserializer(SpatialExtent.class, new SpatialDeserializer());
        module.addDeserializer(TemporalExtent.class, new TemporalDeserializer());
        module.addDeserializer(Item.class, new ItemDeserializer());
        mapper.registerModule(module);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES, false);
    }

    /**
     * Resolves the JSON content to a Catalog object
     * @param content   The JSON content
     */
    public static Catalog parseCatalogResponse(String content) throws JsonProcessingException {
        return mapper.readValue(content, Catalog.class);
    }
    /**
     * Resolves the JSON content to a Catalog object
     * @param stream   The input stream
     */
    public static Catalog parseCatalogResponse(InputStream stream) throws JsonProcessingException {
        return mapper.readValue(new BufferedReader(new InputStreamReader(stream))
                                        .lines().collect(Collectors.joining("\n")),
                                Catalog.class);
    }
    /**
     * Resolves the JSON content to a CollectionList object
     * @param content   The JSON content
     */
    public static CollectionList parseCollectionsResponse(String content) throws JsonProcessingException {
        return mapper.readValue(content, CollectionList.class);
    }
    /**
     * Resolves the JSON content to a CollectionList object
     * @param stream   The input stream
     */
    public static CollectionList parseCollectionsResponse(InputStream stream) throws JsonProcessingException {
        return mapper.readValue(new BufferedReader(new InputStreamReader(stream))
                                        .lines().collect(Collectors.joining("\n")),
                                CollectionList.class);
    }
    /**
     * Resolves the JSON content to a Collection object
     * @param content   The JSON content
     */
    public static Collection parseCollectionResponse(String content) throws JsonProcessingException {
        return mapper.readValue(content, Collection.class);
    }
    /**
     * Resolves the JSON content to a Collection object
     * @param stream   The input stream
     */
    public static Collection parseCollectionResponse(InputStream stream) throws JsonProcessingException {
        return mapper.readValue(new BufferedReader(new InputStreamReader(stream))
                                        .lines().collect(Collectors.joining("\n")),
                                Collection.class);
    }
    /**
     * Resolves the JSON content to an ItemCollection object
     * @param content   The JSON content
     */
    public static ItemCollection parseItemCollectionResponse(String content) throws JsonProcessingException {
        return mapper.readValue(content, ItemCollection.class);
    }
    /**
     * Resolves the JSON content to an ItemCollection object
     * @param stream   The input stream
     */
    public static ItemCollection parseItemCollectionResponse(InputStream stream) throws JsonProcessingException {
        return mapper.readValue(new BufferedReader(new InputStreamReader(stream))
                                        .lines().collect(Collectors.joining("\n")),
                                ItemCollection.class);
    }
    /**
     * Resolves the JSON content to an Item object
     * @param content   The JSON content
     */
    public static Item parseItemResponse(String content) throws JsonProcessingException {
        return mapper.readValue(content, Item.class);
    }
    /**
     * Resolves the JSON content to an Item object
     * @param stream   The input stream
     */
    public static Item parseItemResponse(InputStream stream) throws JsonProcessingException {
        return mapper.readValue(new BufferedReader(new InputStreamReader(stream))
                                        .lines().collect(Collectors.joining("\n")),
                                Item.class);
    }
    /**
     * Resolves the JSON content to a Link object
     * @param content   The JSON content
     */
    public static Link parseLink(String content) throws JsonProcessingException {
        return mapper.readValue(content, Link.class);
    }
    /**
     * Resolves the JSON content to an eo:Band object
     * @param content   The JSON content
     */
    public static Band parseBand(String content) throws JsonProcessingException {
        return mapper.readValue(content, Band.class);
    }
    /**
     * Resolves the JSON content to a generic object
     * @param content   The JSON content
     */
    public static Object parse(String content) throws JsonProcessingException {
        return mapper.reader().readValue(content);
    }
    /**
     * Resolves the JSON content to a typed object
     * @param content   The JSON content
     */
    public static <T> T parse(String content, Class<T> clazz) throws IOException {
        return (T) mapper.reader().readValue(content, clazz);
    }

}
