import com.fasterxml.jackson.core.PrettyPrinter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.exceptions.CodeGenerationException;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import org.apache.http.Header;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.openstack4j.api.OSClient;
import org.openstack4j.model.common.Identifier;
import org.openstack4j.model.compute.Server;
import org.openstack4j.model.identity.v3.Service;
import org.openstack4j.model.image.Image;
import org.openstack4j.openstack.OSFactory;
import ro.cs.tao.utils.CloseableHttpResponse;
import ro.cs.tao.utils.HttpMethod;
import ro.cs.tao.utils.NetUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class OSTest {

    public static void main(String[] args) throws IOException, CodeGenerationException {
        String domain = "cloud_00407";
        String userName = "kraftek@c-s.ro";
        String userId = "f4cb2d2e875444c0bf3dbedddcdc1bf5";
        String password = "cei7pitici.";
        String authenticationURL = "https://keystone.cloudferro.com:5000/v3";
        String projectId = "7ac770fe523b436e91a00d6d64629969";
        Path folder = Paths.get("E:\\");

        final String url = "https://identity.cloudferro.com/auth/realms/Creodias-new/protocol/openid-connect/token";
        final String appClient = "openstack";//"taoclient";
        final String kSecret = "50ef4972-546f-46d9-8e72-f91a401a8b30";
        Header header = new BasicHeader("Content-Type", "application/x-www-form-urlencoded");
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("password", password));
        params.add(new BasicNameValuePair("grant_type", "password"));
        params.add(new BasicNameValuePair("username", userName));
        params.add(new BasicNameValuePair("client_id", appClient));
        params.add(new BasicNameValuePair("client_secret", kSecret));
        //params.add(new BasicNameValuePair("totp", generateOTP("N5IGCVBZOE4EC4ZVGVQXMOLQHFRHCULO")));
        String token;
        try (CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.POST, url, header, params)) {
             token = EntityUtils.toString(response.getEntity());
            Map<String, String> map = new LinkedHashMap<>();
            map.putAll(new ObjectMapper().readValue(token, map.getClass()));
            token = map.get("access_token");
            System.out.println(token);



        }
        OSFactory.enableHttpLoggingFilter(true);
        OSClient.OSClientV3 client = OSFactory.builderV3()
                .token("gAAAAABlEuAwxiY4Q9JLZkbPOvtFPOOzl0pqQtO2CS7ZCRcbdNzlNBOFXdTYditNH8Ew3bUrAesA-CVizHgjrOgtTJ0-yqIRLVsWbSOOFlroQnKlEwadi0AZINfmzCspw3JplLuUhJNGE1TLs-xjU36O4htizHAJ3RyvEx6zY02fLHkxoNRVzXY8keeLNDLczcA5DPVR2avt")
                .endpoint("https://keystone.cloudferro.com:5000/v3")
                //.credentials(userName, password, Identifier.byName(domain))
                .scopeToProject(Identifier.byId("7ac770fe523b436e91a00d6d64629969"), Identifier.byId("b78b4f25e74d40c888feeddb88232f91"))
                .authenticate();
        client = client.useRegion("WAW3-2");
        List<? extends Service> catalog = client.getToken().getCatalog();
        catalog.sort((Comparator<Service>) (o1, o2) -> o2.getType().compareTo(o1.getType()));
        final List<? extends Server> list = client.compute().servers().list();
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

    private static String generateOTP(String secret) throws CodeGenerationException {
        TimeProvider timeProvider = new SystemTimeProvider();
        CodeGenerator codeGenerator = new DefaultCodeGenerator();
        long counter = timeProvider.getTime() / 30;
        return codeGenerator.generate(secret, counter);
    }

}
