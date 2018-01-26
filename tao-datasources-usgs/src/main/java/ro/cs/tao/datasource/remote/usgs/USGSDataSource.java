package ro.cs.tao.datasource.remote.usgs;

import ro.cs.tao.datasource.remote.URLDataSource;
import ro.cs.tao.datasource.remote.usgs.parameters.LandsatParameterProvider;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Properties;

/**
 * @author Cosmin Cara
 */
public class USGSDataSource extends URLDataSource<Landsat8Query> {
    private static final Properties props;
    private static String URL;
    private static String BASE_URL;

    static {
        props = new Properties();
        try {
            props.load(USGSDataSource.class.getResourceAsStream("usgs.properties"));
            URL = props.getProperty("usgs.search.url");
            BASE_URL = props.getProperty("usgs.base.url");
        } catch (IOException ignored) {
        }
    }

    public USGSDataSource() throws URISyntaxException {
        super(URL);
        setParameterProvider(new LandsatParameterProvider());
        this.alternateConnectionString = BASE_URL;
        this.properties = USGSDataSource.props;
    }

    @Override
    public String defaultName() { return "USGS"; }

    @Override
    protected Landsat8Query createQueryImpl(String code) {
        return new Landsat8Query(this);
    }
}
