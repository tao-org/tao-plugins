package ro.cs.tao.stac.core.parser;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import ro.cs.tao.stac.core.model.SpatialExtent;

import java.io.IOException;

public class SpatialDeserializer extends BaseDeserializer<SpatialExtent> {

    public SpatialDeserializer() {
        super(SpatialExtent.class);
    }

    @Override
    public SpatialExtent deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
        TreeNode node = p.readValueAsTree();
        final SpatialExtent extent = new SpatialExtent();
        extent.setBbox(getDoubleArray1(node, "bbox"));
        extent.setCrs(getString(node, "crs"));
        return extent;
    }
}
