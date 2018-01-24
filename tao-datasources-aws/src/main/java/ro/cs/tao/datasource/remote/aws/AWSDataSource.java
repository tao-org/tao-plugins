package ro.cs.tao.datasource.remote.aws;

import ro.cs.tao.datasource.remote.URLDataSource;
import ro.cs.tao.datasource.remote.aws.parameters.AWSParameterProvider;
import ro.cs.tao.datasource.util.NetUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;

/**
 * @author Cosmin Cara
 */
public class AWSDataSource extends URLDataSource<AWSDataQuery> {
    private static String S2_URL;
    private static String L8_URL;
    private static String L8_PRE_URL;

    static {
        Properties props = new Properties();
        try {
            props.load(AWSDataSource.class.getResourceAsStream("aws.properties"));
            S2_URL = props.getProperty("s2.aws.search.url");
            L8_URL = props.getProperty("l8.aws.search.url");
            L8_PRE_URL = props.getProperty("l8.aws.pre.search.url");
        } catch (IOException ignored) {
        }
    }

    public AWSDataSource() throws URISyntaxException {
        super(S2_URL);
        setParameterProvider(new AWSParameterProvider());
    }

    @Override
    public void setCredentials(String username, String password) {
        // no-op
    }

    @Override
    public boolean ping() {
        return NetUtils.isAvailable(S2_URL);
    }

    @Override
    public String defaultName() { return "Amazon Web Services"; }

    @Override
    protected AWSDataQuery createQueryImpl(String sensorName) {
        try {
            switch (sensorName) {
                case "Sentinel2":
                    this.connectionString = S2_URL;
                    this.remoteUrl = new URI(this.connectionString);
                    break;
                case "Landsat8":
                    this.connectionString = L8_URL;
                    this.alternateConnectionString = L8_PRE_URL;
                    this.remoteUrl = new URI(this.connectionString);
                    break;
                default:
                    throw new IllegalArgumentException(String.format("'%s' is not supported", sensorName));
            }
            return new AWSDataQuery(this, sensorName);
        } catch (URISyntaxException ex) {
            throw new RuntimeException("Malformed url: " + ex.getMessage());
        }
    }
}
