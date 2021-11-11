package ro.cs.tao.datasource.usgs.json.handlers;

import ro.cs.tao.datasource.remote.result.filters.AttributeFilter;
import ro.cs.tao.datasource.remote.result.json.JSonResponseHandler;
import ro.cs.tao.datasource.usgs.json.responses.DownloadResponse;
import ro.cs.tao.serialization.JsonMapper;

import java.io.IOException;
import java.util.List;

public class DownloadResponseHandler  implements JSonResponseHandler<DownloadResponse> {
    @Override
    public List<DownloadResponse> readValues(String content, AttributeFilter...filters) throws IOException {
        return null;
    }

    @Override
    public DownloadResponse readValue(String content, AttributeFilter... filters) throws IOException {
        DownloadResponse response = JsonMapper.instance().readValue(content, DownloadResponse.class);
        final String error = response.getErrorMessage();
        if (response.getData() == null) {
            throw new IOException(error != null ? error : "No data [error: null]");
        }
        return response;
    }
}
