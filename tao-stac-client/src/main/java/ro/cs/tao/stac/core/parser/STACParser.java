package ro.cs.tao.stac.core.parser;

import ro.cs.tao.stac.core.model.*;
import ro.cs.tao.stac.core.model.extensions.eo.Band;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.module.SimpleModule;

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
        SimpleModule module = new SimpleModule();
        module.addDeserializer(SpatialExtent.class, new SpatialDeserializer());
        module.addDeserializer(TemporalExtent.class, new TemporalDeserializer());
        module.addDeserializer(Item.class, new ItemDeserializer());
        mapper = new ObjectMapper().rebuild()
                .addModule(module)
                .enable(EnumFeature.WRITE_ENUMS_USING_TO_STRING)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES, false)
                .configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false)
                .build();
    }

    /**
     * Resolves the JSON content to a Catalog object
     * @param content   The JSON content
     */
    public static Catalog parseCatalogResponse(String content) throws JacksonException {
        return mapper.readValue(content, Catalog.class);
    }
    /**
     * Resolves the JSON content to a Catalog object
     * @param stream   The input stream
     */
    public static Catalog parseCatalogResponse(InputStream stream) throws JacksonException {
        return mapper.readValue(new BufferedReader(new InputStreamReader(stream))
                                        .lines().collect(Collectors.joining("\n")),
                                Catalog.class);
    }
    /**
     * Resolves the JSON content to a CollectionList object
     * @param content   The JSON content
     */
    public static CollectionList parseCollectionsResponse(String content) throws JacksonException {
        return mapper.readValue(content, CollectionList.class);
    }
    /**
     * Resolves the JSON content to a CollectionList object
     * @param stream   The input stream
     */
    public static CollectionList parseCollectionsResponse(InputStream stream) throws JacksonException {
        return mapper.readValue(new BufferedReader(new InputStreamReader(stream))
                                        .lines().collect(Collectors.joining("\n")),
                                CollectionList.class);
    }
    /**
     * Resolves the JSON content to a Collection object
     * @param content   The JSON content
     */
    public static Collection parseCollectionResponse(String content) throws JacksonException {
        return mapper.readValue(content, Collection.class);
    }
    /**
     * Resolves the JSON content to a Collection object
     * @param stream   The input stream
     */
    public static Collection parseCollectionResponse(InputStream stream) throws JacksonException {
        return mapper.readValue(new BufferedReader(new InputStreamReader(stream))
                                        .lines().collect(Collectors.joining("\n")),
                                Collection.class);
    }

    public static QueryableList parseQueryablesResponse(InputStream stream) throws JacksonException {
        return mapper.readValue(stream, QueryableList.class);
    }

    /**
     * Resolves the JSON content to an ItemCollection object
     * @param content   The JSON content
     */
    public static ItemCollection parseItemCollectionResponse(String content) throws JacksonException {
        return mapper.readValue(content, ItemCollection.class);
    }
    /**
     * Resolves the JSON content to an ItemCollection object
     * @param stream   The input stream
     */
    public static ItemCollection parseItemCollectionResponse(InputStream stream) throws JacksonException {
        return mapper.readValue(new BufferedReader(new InputStreamReader(stream))
                                        .lines().collect(Collectors.joining("\n")),
                                ItemCollection.class);
    }
    /**
     * Resolves the JSON content to an Item object
     * @param content   The JSON content
     */
    public static Item parseItemResponse(String content) throws JacksonException {
        return mapper.readValue(content, Item.class);
    }
    /**
     * Resolves the JSON content to an Item object
     * @param stream   The input stream
     */
    public static Item parseItemResponse(InputStream stream) throws JacksonException {
        return mapper.readValue(new BufferedReader(new InputStreamReader(stream))
                                        .lines().collect(Collectors.joining("\n")),
                                Item.class);
    }
    /**
     * Resolves the JSON content to a Link object
     * @param content   The JSON content
     */
    public static Link parseLink(String content) throws JacksonException {
        return mapper.readValue(content, Link.class);
    }
    /**
     * Resolves the JSON content to an eo:Band object
     * @param content   The JSON content
     */
    public static Band parseBand(String content) throws JacksonException {
        return mapper.readValue(content, Band.class);
    }
    /**
     * Resolves the JSON content to a generic object
     * @param content   The JSON content
     */
    public static Object parse(String content) throws JacksonException {
        return mapper.reader().readValue(content);
    }
    /**
     * Resolves the JSON content to a typed object
     * @param content   The JSON content
     */
    public static <T> T parse(String content, Class<T> clazz) throws IOException {
        return mapper.readerFor(clazz).readValue(content);
    }

}

