package ro.cs.tao.stac.client;

import ro.cs.tao.datasource.remote.result.filters.AttributeFilter;
import ro.cs.tao.datasource.remote.result.json.JSonResponseHandler;
import ro.cs.tao.serialization.JsonMapper;
import ro.cs.tao.stac.model.Catalogs;

import java.io.IOException;

public class CatalogsResponseHandler implements JSonResponseHandler<Catalogs> {

    @Override
    public Catalogs readValue(String content, AttributeFilter... filters) throws IOException {
        return JsonMapper.instance().readValue(content, Catalogs.class);
    }
}
