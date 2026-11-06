import ro.cs.tao.component.WebServiceAuthentication;
import ro.cs.tao.component.enums.AuthenticationType;
import ro.cs.tao.datasource.param.DataSourceParameter;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.opensearch.OpenSearchClient;
import ro.cs.tao.opensearch.model.OpenSearchDescription;
import ro.cs.tao.opensearch.model.Url;
import ro.cs.tao.utils.executors.monitoring.DownloadProgressListener;
import tools.jackson.dataformat.xml.XmlMapper;

import java.nio.file.Path;
import java.util.List;

public class ClientTest {

    /**
     * @param args  0 = login url, 1 = user, 2 = password, 3 = endpoint url, 4 = output folder
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        XmlMapper mapper = XmlMapper.builder()
                .defaultUseWrapper(false)
                .build();
        WebServiceAuthentication auth = new WebServiceAuthentication();
        auth.setType(AuthenticationType.TOKEN);
        auth.setUser(args[1]);
        auth.setPassword(args[2]);
        auth.setLoginUrl(args[0]);
        auth.setAuthHeader("Authorization");
        OpenSearchClient client = new OpenSearchClient(args[3], auth);
        OpenSearchDescription description = client.getDescription();
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(description));
        Url url = description.getUrl().get(0);
        DataSourceParameter pi = url.getParameters().stream().filter(p -> p.getName().equals("pi")).findFirst().get();
        String colName = pi.getValueSet()[0].toString();
        List<EOProduct> results = client.search(colName, null);
        for (EOProduct p : results) {
            System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(p.toAttributeMap()));
        }
        client.setProgressListener(new DownloadProgressListener() {
        });
        client.download(results.get(0).getLocation(), Path.of(args[4]));
        System.exit(0);
    }
}
