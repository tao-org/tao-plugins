package ro.cs.tao.datasource.remote.asf;

import ro.cs.tao.datasource.remote.URLDataSource;
import ro.cs.tao.datasource.remote.asf.parameters.ASFParameterProvider;
import ro.cs.tao.datasource.util.NetUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.Properties;

public class ASFSentinel1DataSource extends URLDataSource<ASFSentinel1Query> {
    private static final Properties props;
    private static String URL;

    static {
        props = new Properties();
        try {
            props.load(ASFSentinel1DataSource.class.getResourceAsStream("asf.properties"));
            URL = props.getProperty("asf.search.url");
        } catch (IOException ignored) {
        }
    }

    public ASFSentinel1DataSource() throws URISyntaxException {
        super(URL);
        setParameterProvider(new ASFParameterProvider());
        this.properties = ASFSentinel1DataSource.props;
    }

    @Override
    public String defaultId() { return "Alaska Satellite Facility"; }

    @Override
    public int getMaximumAllowedTransfers() { return 2; }

    @Override
    public boolean ping() {
        return NetUtils.isAvailable(URL);
    }

    @Override
    public boolean requiresAuthentication() { return true; }

    @Override
    public void setCredentials(String username, String password) {
        super.setCredentials(username, password);
        String authToken = "Basic " + new String(Base64.getEncoder().encode((username + ":" + password).getBytes()));
        NetUtils.setAuthToken(authToken);
    }

    @Override
    protected ASFSentinel1Query createQueryImpl(String code) {
        return new ASFSentinel1Query(this);
    }
}
