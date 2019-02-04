package ro.cs.tao.datasource.remote.creodias.landsat8;

import ro.cs.tao.datasource.remote.creodias.BaseDataSource;

import java.net.URISyntaxException;

public class Landsat8DataSource extends BaseDataSource {

    public Landsat8DataSource() throws URISyntaxException {
        super();
        setParameterProvider(new Landsat8ParameterProvider());
    }

    @Override
    public String defaultId() { return "CreoDIAS Landsat-8"; }

    @Override
    public String urlPropertyName() { return "l8.search.url"; }

}
