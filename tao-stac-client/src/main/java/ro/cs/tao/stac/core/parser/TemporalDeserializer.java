package ro.cs.tao.stac.core.parser;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import ro.cs.tao.stac.core.model.TemporalExtent;

import java.io.IOException;
/**
 * Deserialization from JSON class for a temporal extent.
 *
 * @author Cosmin Cara
 */
public class TemporalDeserializer extends StdDeserializer<TemporalExtent> {

    public TemporalDeserializer() {
        super(TemporalExtent.class);
    }

    @Override
    public TemporalExtent deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
        TreeNode node = jsonParser.readValueAsTree();
        final TemporalExtent extent = new TemporalExtent();
        extent.setInterval(JsonValueHelper.getDateTimeArray(node, "interval"));
        extent.setTrs(JsonValueHelper.getString(node, "trs"));
        return extent;
    }
}
