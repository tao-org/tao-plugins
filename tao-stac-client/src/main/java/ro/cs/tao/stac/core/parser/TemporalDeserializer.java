package ro.cs.tao.stac.core.parser;

import ro.cs.tao.stac.core.model.TemporalExtent;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.deser.std.StdDeserializer;

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
    public TemporalExtent deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws JacksonException {
        JsonNode node = jsonParser.readValueAsTree();
        final TemporalExtent extent = new TemporalExtent();
        extent.setInterval(JsonValueHelper.getDateTimeArray(node, "interval"));
        extent.setTrs(JsonValueHelper.getString(node, "trs"));
        return extent;
    }
}


