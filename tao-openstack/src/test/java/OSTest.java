import com.fasterxml.jackson.core.PrettyPrinter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openstack4j.api.OSClient;
import org.openstack4j.model.common.Identifier;
import org.openstack4j.model.identity.v3.Service;
import org.openstack4j.model.image.Image;
import org.openstack4j.openstack.OSFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;

public class OSTest {

    public static void main(String[] args) throws IOException {
        String domain = "";
        String user = "";
        String password = "";
        String authenticationURL = "";
        String projectId = "";
        Path folder = Paths.get("W:\\");
        OSFactory.enableHttpLoggingFilter(true);
        final OSClient.OSClientV3 client = OSFactory.builderV3()
                .endpoint(authenticationURL)
                .credentials(user, password, Identifier.byName(domain))
                .scopeToProject(Identifier.byId(projectId))
                .authenticate();
        List<? extends Service> catalog = client.getToken().getCatalog();
        catalog.sort((Comparator<Service>) (o1, o2) -> o2.getType().compareTo(o1.getType()));

        ObjectMapper objectMapper = new ObjectMapper();
        PrettyPrinter prettyPrinter = new DefaultPrettyPrinter();

        /*final List<? extends Server> servers = client.compute().servers().list();
        Files.write(folder.resolve("servers.json"), objectMapper.writer(prettyPrinter).writeValueAsBytes(servers));

        final List<? extends FloatingIP> floatingIPS = client.compute().floatingIps().list();
        Files.write(folder.resolve("floatingIps.json"), objectMapper.writer(prettyPrinter).writeValueAsBytes(floatingIPS));*/

        final List<? extends Image> images = client.images().list();
        Files.write(folder.resolve("images.json"), objectMapper.writer(prettyPrinter).writeValueAsBytes(images));

        /*final List<? extends Network> networks = client.networking().network().list();
        Files.write(folder.resolve("networks.json"), objectMapper.writer(prettyPrinter).writeValueAsBytes(networks));

        final List<? extends Volume> volumes = client.blockStorage().volumes().list();

        final List<? extends VolumeType> volumeTypes = client.blockStorage().volumes().listVolumeTypes();
        Files.write(folder.resolve("volumes.json"), objectMapper.writer(prettyPrinter).writeValueAsBytes(volumes));*/
        System.exit(0);
    }
}
