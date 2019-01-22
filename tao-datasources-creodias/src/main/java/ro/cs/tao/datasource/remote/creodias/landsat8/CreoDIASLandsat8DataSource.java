package ro.cs.tao.datasource.remote.creodias.landsat8;

import ro.cs.tao.datasource.remote.creodias.CreoDIASDataSource;

import java.net.URISyntaxException;

public class CreoDIASLandsat8DataSource extends CreoDIASDataSource {

    public CreoDIASLandsat8DataSource() throws URISyntaxException {
        super();
        setParameterProvider(new CreoDIASLandsat8ParameterProvider());
    }

    @Override
    public String defaultId() { return "CreoDIAS Landsat-8"; }

    @Override
    public String urlPropertyName() { return "creodias.l8.search.url"; }

}
