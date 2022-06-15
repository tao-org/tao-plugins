package ro.cs.tao.stac.core.parser;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import ro.cs.tao.stac.core.model.TemporalExtent;

import java.io.IOException;

public class TemporalDeserializer extends BaseDeserializer<TemporalExtent> {

    public TemporalDeserializer() {
        super(TemporalExtent.class);
    }

    @Override
    public TemporalExtent deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JacksonException {
        TreeNode node = jsonParser.readValueAsTree();
        final TemporalExtent extent = new TemporalExtent();
        extent.setInterval(getDateTimeArray(node, "interval"));
        extent.setTrs(getString(node, "trs"));
        return extent;
    }
}
