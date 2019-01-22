package ro.cs.tao.datasource.remote.creodias.sentinel2;

import ro.cs.tao.datasource.remote.creodias.CreoDIASDataSource;

import java.net.URISyntaxException;

public class CreoDIASSentinel2DataSource extends CreoDIASDataSource {

    public CreoDIASSentinel2DataSource() throws URISyntaxException {
        super();
        setParameterProvider(new CreoDIASSentinel2ParameterProvider());
    }

    @Override
    public String defaultId() { return "CreoDIAS Sentinel-2"; }

    @Override
    public String urlPropertyName() { return "creodias.s2.search.url"; }

}
