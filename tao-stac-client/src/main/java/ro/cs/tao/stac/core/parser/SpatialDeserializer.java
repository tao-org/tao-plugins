package ro.cs.tao.stac.core.parser;

import ro.cs.tao.stac.core.model.SpatialExtent;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.deser.std.StdDeserializer;

/**
 * Deserialization from JSON class for a spatial extent.
 *
 * @author Cosmin Cara
 */
public class SpatialDeserializer extends StdDeserializer<SpatialExtent> {

    public SpatialDeserializer() {
        super(SpatialExtent.class);
    }

    @Override
    public SpatialExtent deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
        JsonNode node = p.readValueAsTree();
        final SpatialExtent extent = new SpatialExtent();
        extent.setBbox(JsonValueHelper.getDoubleArray1(node, "bbox"));
        extent.setCrs(JsonValueHelper.getString(node, "crs"));
        return extent;
    }
}


