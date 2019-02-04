package ro.cs.tao.datasource.remote.mundi.sentinel2;

import ro.cs.tao.datasource.remote.mundi.BaseDataSource;

import java.net.URISyntaxException;

public class Sentinel2DataSource extends BaseDataSource {

    public Sentinel2DataSource() throws URISyntaxException {
        super();
        setParameterProvider(new Sentinel2ParameterProvider());
    }

    @Override
    public String defaultId() { return "Mundi DIAS Sentinel-2"; }

    @Override
    public String urlPropertyName() { return "s2.search.url"; }

}
