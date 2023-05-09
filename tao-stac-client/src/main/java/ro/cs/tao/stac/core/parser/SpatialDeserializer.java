package ro.cs.tao.stac.core.parser;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import ro.cs.tao.stac.core.model.SpatialExtent;

import java.io.IOException;
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
    public SpatialExtent deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        TreeNode node = p.readValueAsTree();
        final SpatialExtent extent = new SpatialExtent();
        extent.setBbox(JsonValueHelper.getDoubleArray1(node, "bbox"));
        extent.setCrs(JsonValueHelper.getString(node, "crs"));
        return extent;
    }
}
