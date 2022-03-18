package ro.cs.tao.datasource.remote.era;

import org.apache.http.Header;
import ro.cs.tao.datasource.remote.URLDataSource;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Properties;

public class ERADataSource extends URLDataSource<ERADataQuery, Header> {

    private static String URL;

    static {
        Properties props = new Properties();
        try {
            props.load(ERADataQuery.class.getResourceAsStream("era.properties"));
            URL = props.getProperty("era.search.url");
            if (!URL.endsWith("/")) {
                URL += "/";
            }
        } catch (IOException ignored) {
        }
    }

    public ERADataSource() throws URISyntaxException {
        super(URL);
    }

    @Override
    protected ERADataQuery createQueryImpl(String code) {
        return null;
    }

    @Override
    public Header authenticate() throws IOException {
        return null;
    }
}
