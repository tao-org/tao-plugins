package ro.cs.tao.stac.core.parser;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import ro.cs.tao.stac.core.model.Collection;

import java.io.IOException;

public class CollectionDeserializer extends BaseDeserializer<Collection> {

    public CollectionDeserializer() {
        super(Collection.class);
    }

    @Override
    public Collection deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JacksonException {
        return jsonParser.readValueAs(Collection.class);
    }
}
