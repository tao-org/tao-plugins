package ro.cs.tao.stac.core.parser;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import ro.cs.tao.stac.core.model.ItemCollection;

import java.io.IOException;

public class ItemCollectionDeserializer extends BaseDeserializer<ItemCollection> {

    public ItemCollectionDeserializer() {
        super(ItemCollection.class);
    }

    @Override
    public ItemCollection deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JacksonException {
        return jsonParser.readValueAs(ItemCollection.class);
    }
}
