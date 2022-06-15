package ro.cs.tao.stac.core.parser;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import ro.cs.tao.stac.core.model.Catalog;

import java.io.IOException;

public class CatalogDeserializer extends BaseDeserializer<Catalog> {

    public CatalogDeserializer() {
        super(Catalog.class);
    }

    @Override
    public Catalog deserialize(JsonParser p, DeserializationContext ctx) throws IOException, JacksonException {
        return p.readValueAs(Catalog.class);
    }
}
