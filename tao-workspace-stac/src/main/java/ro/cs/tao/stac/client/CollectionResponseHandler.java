package ro.cs.tao.stac.client;

import ro.cs.tao.datasource.remote.result.filters.AttributeFilter;
import ro.cs.tao.datasource.remote.result.json.JSonResponseHandler;
import ro.cs.tao.serialization.JsonMapper;
import ro.cs.tao.stac.model.Collection;

import java.io.IOException;

public class CollectionResponseHandler implements JSonResponseHandler<Collection> {

    @Override
    public Collection readValue(String content, AttributeFilter... filters) throws IOException {
        return JsonMapper.instance().readValue(content, Collection.class);
    }
}
