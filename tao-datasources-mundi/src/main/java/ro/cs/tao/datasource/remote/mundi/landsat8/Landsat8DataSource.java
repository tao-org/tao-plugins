package ro.cs.tao.datasource.remote.mundi.landsat8;

import ro.cs.tao.datasource.remote.mundi.BaseDataSource;

import java.net.URISyntaxException;

public class Landsat8DataSource extends BaseDataSource {

    public Landsat8DataSource() throws URISyntaxException {
        super();
        setParameterProvider(new Landsat8ParameterProvider());
    }

    @Override
    public String defaultId() { return "Mundi DIAS Landsat-8"; }

    @Override
    public String urlPropertyName() { return "l8.search.url"; }

}
