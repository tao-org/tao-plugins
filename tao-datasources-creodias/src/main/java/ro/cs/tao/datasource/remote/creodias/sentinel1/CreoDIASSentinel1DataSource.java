package ro.cs.tao.datasource.remote.creodias.sentinel1;

import ro.cs.tao.datasource.remote.creodias.CreoDIASDataSource;

import java.net.URISyntaxException;

public class CreoDIASSentinel1DataSource extends CreoDIASDataSource {

    public CreoDIASSentinel1DataSource() throws URISyntaxException {
        super();
        setParameterProvider(new CreoDIASSentinel1ParameterProvider());
    }

    @Override
    public String defaultId() { return "CreoDIAS Sentinel-1"; }

    @Override
    public String urlPropertyName() { return "creodias.s1.search.url"; }

}
