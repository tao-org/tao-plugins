package ro.cs.tao.datasource.remote.creodias.sentinel2;

import ro.cs.tao.datasource.remote.creodias.BaseDataSource;

import java.net.URISyntaxException;

public class Sentinel2DataSource extends BaseDataSource {

    public Sentinel2DataSource() throws URISyntaxException {
        super();
        setParameterProvider(new Sentinel2ParameterProvider());
    }

    @Override
    public String defaultId() { return "CreoDIAS Sentinel-2"; }

    @Override
    public String urlPropertyName() { return "s2.search.url"; }

}
