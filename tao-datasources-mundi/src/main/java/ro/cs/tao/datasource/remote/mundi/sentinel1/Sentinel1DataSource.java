package ro.cs.tao.datasource.remote.mundi.sentinel1;

import ro.cs.tao.datasource.remote.mundi.BaseDataSource;

import java.net.URISyntaxException;

public class Sentinel1DataSource extends BaseDataSource {

    public Sentinel1DataSource() throws URISyntaxException {
        super();
        setParameterProvider(new Sentinel1ParameterProvider());
    }

    @Override
    public String defaultId() { return "Mundi DIAS Sentinel-1"; }

    @Override
    public String urlPropertyName() { return "s1.search.url"; }

}
