package ro.cs.tao.opensearch.parser;

import ro.cs.tao.eodata.EOProduct;

import java.io.InputStream;

public interface PostDownloadAction {
    void execute(InputStream stream, EOProduct product) throws Exception;
}
