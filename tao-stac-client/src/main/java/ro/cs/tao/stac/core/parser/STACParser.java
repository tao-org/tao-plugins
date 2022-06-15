package ro.cs.tao.stac.core.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import ro.cs.tao.stac.core.model.*;
import ro.cs.tao.stac.core.model.extensions.eo.Band;

public class STACParser {
    private static final ObjectMapper mapper;

    static {
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        SimpleModule module = new SimpleModule();
        /*module.addDeserializer(Catalog.class, new CatalogDeserializer());
        module.addDeserializer(Collection.class, new CollectionDeserializer());*/
        module.addDeserializer(SpatialExtent.class, new SpatialDeserializer());
        module.addDeserializer(TemporalExtent.class, new TemporalDeserializer());
        /*module.addDeserializer(ItemCollection.class, new ItemCollectionDeserializer());*/
        module.addDeserializer(Item.class, new ItemDeserializer());
        mapper.registerModule(module);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public Catalog parseCatalogResponse(String content) throws JsonProcessingException {
        return mapper.readValue(content, Catalog.class);
    }

    public Collection parseCollectionResponse(String content) throws JsonProcessingException {
        return mapper.readValue(content, Collection.class);
    }

    public ItemCollection parseItemCollectionResponse(String content) throws JsonProcessingException {
        return mapper.readValue(content, ItemCollection.class);
    }

    public Item parseItemResponse(String content) throws JsonProcessingException {
        return mapper.readValue(content, Item.class);
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
