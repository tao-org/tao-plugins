package ro.cs.tao.stac.core.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import ro.cs.tao.stac.core.model.*;
import ro.cs.tao.stac.core.model.extensions.eo.Band;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

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
    }

    public Catalog parseCatalogResponse(String content) throws JsonProcessingException {
        return mapper.readValue(content, Catalog.class);
    }

    public Catalog parseCatalogResponse(InputStream stream) throws JsonProcessingException {
        return mapper.readValue(new BufferedReader(new InputStreamReader(stream))
                                        .lines().collect(Collectors.joining("\n")),
                                Catalog.class);
    }

    public CollectionList parseCollectionsResponse(String content) throws JsonProcessingException {
        return mapper.readValue(content, CollectionList.class);
    }

    public CollectionList parseCollectionsResponse(InputStream stream) throws JsonProcessingException {
        return mapper.readValue(new BufferedReader(new InputStreamReader(stream))
                                        .lines().collect(Collectors.joining("\n")),
                                CollectionList.class);
    }

    public Collection parseCollectionResponse(String content) throws JsonProcessingException {
        return mapper.readValue(content, Collection.class);
    }

    public Collection parseCollectionResponse(InputStream stream) throws JsonProcessingException {
        return mapper.readValue(new BufferedReader(new InputStreamReader(stream))
                                        .lines().collect(Collectors.joining("\n")),
                                Collection.class);
    }

    public ItemCollection parseItemCollectionResponse(String content) throws JsonProcessingException {
        return mapper.readValue(content, ItemCollection.class);
    }

    public ItemCollection parseItemCollectionResponse(InputStream stream) throws JsonProcessingException {
        return mapper.readValue(new BufferedReader(new InputStreamReader(stream))
                                        .lines().collect(Collectors.joining("\n")),
                                ItemCollection.class);
    }

    public Item parseItemResponse(String content) throws JsonProcessingException {
        return mapper.readValue(content, Item.class);
    }

    public Item parseItemResponse(InputStream stream) throws JsonProcessingException {
        return mapper.readValue(new BufferedReader(new InputStreamReader(stream))
                                        .lines().collect(Collectors.joining("\n")),
                                Item.class);
    }

    public Link parseLink(String content) throws JsonProcessingException {
        return mapper.readValue(content, Link.class);
    }

    public Band parseBand(String content) throws JsonProcessingException {
        return mapper.readValue(content, Band.class);
    }

    public Object parse(String content) throws JsonProcessingException {
        return mapper.reader().readValue(content);
    }

}
