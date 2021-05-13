package ro.cs.tao.topology.openstack;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.FileUtils;
import org.openstack4j.api.OSClient;
import org.openstack4j.api.compute.ComputeFloatingIPService;
import org.openstack4j.api.compute.ComputeService;
import org.openstack4j.api.compute.ServerService;
import org.openstack4j.api.exceptions.AuthenticationException;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.common.Identifier;
import org.openstack4j.model.compute.*;
import org.openstack4j.model.compute.builder.ServerCreateBuilder;
import org.openstack4j.model.identity.v3.Service;
import org.openstack4j.model.image.v2.Image;
import org.openstack4j.model.network.Network;
import org.openstack4j.model.network.SecurityGroup;
import org.openstack4j.openstack.OSFactory;
import ro.cs.tao.utils.FileUtilities;

import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;

public class OpenstackClient {

    private static final int WAIT_TIMEOUT_MILLISECONDS = 60 * 1000;

    private OSClient.OSClientV3 client;

    public OpenstackClient() {
    }

    public void authenticate() throws AuthenticationException {
        if (this.client != null) {
            throw new IllegalStateException("The client is already authenticated.");
        }

        OSClient.OSClientV3 client = OSFactory.builderV3()
                .endpoint(getAuthenticationURL())
                .credentials(getUserName(), getPassword(), Identifier.byName(getDomain()))
                .scopeToProject(Identifier.byId(getProjectId()))
                .authenticate();

        // hack to force the retrieval of newest version of a service
        List<? extends Service> catalog = client.getToken().getCatalog();
        catalog.sort((Comparator<Service>) (o1, o2) -> o2.getType().compareTo(o1.getType()));

        this.client = client;
    }

    public Set<String> getServerFloatingIPs() {
        checkAuthenticate();

        List<? extends Server> servers = this.client.compute().servers().list();
        Set<String> serverFloatingIPs = new HashSet<>();
        for (Server server : servers) {
            for (List<? extends Address> addresses : server.getAddresses().getAddresses().values()) {
                for (Address address : addresses) {
                    if (address.getType().equalsIgnoreCase("floating")) {
                        serverFloatingIPs.add(address.getAddr());
                    }
                }
            }
        }
        return serverFloatingIPs;
    }

    public void removeServer(String serverName) {
        checkAuthenticate();

        Server server = findServerByName(serverName);
        if (server != null) {
            ComputeFloatingIPService floatingIPService = this.client.compute().floatingIps();
            List<Address> floatingAddressesToDelete = new ArrayList<>();
            for (List<? extends Address> addresses : server.getAddresses().getAddresses().values()) {
                for (Address address : addresses) {
                    if (address.getType().equalsIgnoreCase("floating")) {
                        floatingAddressesToDelete.add(address);
                    }
                }
            }
            ActionResponse actionResponse = this.client.compute().servers().delete(server.getId());
            if (actionResponse.getFault() != null) {
                throw new IllegalStateException("The server name '" + serverName+"' has not been deleted. Message: " + actionResponse.getFault()+".");
            }

            if (floatingAddressesToDelete.size() > 0) {
                List<? extends FloatingIP> floatingIPs = floatingIPService.list();
                for (Address address : floatingAddressesToDelete) {
                    FloatingIP foundFloatingIP = null;
                    for (FloatingIP floatingIP : floatingIPs) {
                        if (floatingIP.getFloatingIpAddress() != null && floatingIP.getFloatingIpAddress().equals(address.getAddr())) {
                            foundFloatingIP = floatingIP;
                            break;
                        }
                    }
                    if (foundFloatingIP != null) {
                        actionResponse = floatingIPService.deallocateIP(foundFloatingIP.getId());
                        if (actionResponse.getFault() != null) {
                            throw new IllegalStateException("The floating IP '" + foundFloatingIP.getFloatingIpAddress() + "' has not been deallocated. Message: " + actionResponse.getFault()+".");
                        }
                    }
                }
            }
        }
    }

    public Server createServer(String serverName, String flavorName, String imageName, String securityGroupName, String userDataAsYaml) {
        checkAuthenticate();

        Server server = findServerByName(serverName);
        if (server != null) {
            throw new IllegalArgumentException("Duplicate server name '" + serverName + "'.");
        }

        SecurityGroup securityGroup = findSecurityGroupByName(securityGroupName);
        if (securityGroup == null) {
            throw new NullPointerException("The security group name '" +securityGroupName+"' does not exist.");
        }

        Image image = findImageByName(imageName);
        if (image == null) {
            throw new NullPointerException("The images name '" +imageName+"' does not exist.");
        }

        String internalNetworkName = getInternalNetworkName();
        Network network = findNetworkByName(internalNetworkName);
        if (network == null) {
            throw new NullPointerException("The internal network name '" +internalNetworkName+"' does not exist.");
        }
        List<String> networkIds = new ArrayList<>(1);
        networkIds.add(network.getId());

        Flavor flavor = findFlavorByName(flavorName);
        if (flavor == null) {
            throw new NullPointerException("The flavor name '" +flavorName+"' does not exist.");
        }

        String encodedString = Base64.getEncoder().encodeToString(userDataAsYaml.getBytes());

        ComputeService computeService = this.client.compute();
        ServerService serverService = computeService.servers();
        ServerCreateBuilder builder = serverService.serverBuilder();
        ServerCreate newServer = builder.name(serverName)
                .flavor(flavor)
                .image(image.getId())
                //.keypairName(getKeyPairName())
                .networks(networkIds)
                .addSecurityGroup(securityGroup.getName())
                .userData(encodedString)
                //TODO Jean remove adddmin password
                .addAdminPass(getPassword())
                .build();
        server = serverService.bootAndWaitActive(newServer, WAIT_TIMEOUT_MILLISECONDS);
        if (server.getStatus() != Server.Status.ACTIVE) {
            throw new IllegalStateException("The server '" + serverName +"' is not active. Its status is " + server.getStatus() + ".");
        }

        ComputeFloatingIPService computeFloatingIPService = computeService.floatingIps();
        boolean succcess = false;
        FloatingIP floatingIP = computeFloatingIPService.allocateIP(getExternalNetworkName());
        try {
            ActionResponse actionResponse = computeFloatingIPService.addFloatingIP(server, floatingIP.getFloatingIpAddress());
            if (actionResponse.getFault() != null) {
                throw new IllegalStateException("The floating IP has not been allocated. Message: " + actionResponse.getFault() +".");
            }
            succcess = true;
        } finally {
            if (!succcess) {
                // deallocate the IP address
                computeFloatingIPService.deallocateIP(floatingIP.getId());
            }
        }
        return server;
    }

    private void checkAuthenticate() {
        if (this.client == null) {
            throw new IllegalStateException("The client is not authenticated.");
        }
    }

    private void checkSupportsNetwork() {
        if (!this.client.supportsNetwork()) {
            throw new IllegalStateException("The client does not support the network service.");
        }
    }

    private void checkSupportsCompute() {
        if (!this.client.supportsCompute()) {
            throw new IllegalStateException("The client does not support the compute service.");
        }
    }

    private Flavor findFlavorByName(String flavorName) {
        checkSupportsCompute();

        List<? extends Flavor> flavors = this.client.compute().flavors().list();
        for (Flavor flavor : flavors) {
            if (flavorName.equals(flavor.getName())) {
                return flavor;
            }
        }
        return null;
    }

    private Server findServerByName(String serverName) {
        List<? extends Server> servers = this.client.compute().servers().list();
        for (Server server : servers) {
            if (serverName.equals(server.getName())) {
                return server;
            }
        }
        return null;
    }

    private SecurityGroup findSecurityGroupByName(String securityGroupName) {
        List<? extends SecurityGroup> groups = this.client.networking().securitygroup().list();
        for (SecurityGroup securityGroup : groups) {
            if (securityGroupName.equals(securityGroup.getName())) {
                if (securityGroup.getTenantId().equals(getProjectId())) {
                    return securityGroup;
                }
            }
        }
        return null;
    }

    private Image findImageByName(String imageName) {
        List<? extends Image> images = this.client.imagesV2().list();
        for (Image image : images) {
            if (imageName.equalsIgnoreCase(image.getName())) {
                if (Image.ImageVisibility.PUBLIC == image.getVisibility()) {
                    return image;
                } else if (Image.ImageVisibility.PRIVATE == image.getVisibility() && image.getOwner().equals(getProjectId())) {
                    return image;
                }
            }
        }
        return null;
    }

    private Network findNetworkByName(String networkName) {
        checkSupportsNetwork();

        List<? extends Network> networks = this.client.networking().network().list();
        for (Network network : networks) {
            if (networkName.equals(network.getName())) {
                return network;
            }
        }
        return null;
    }

    protected String getInternalNetworkName() {
        return "demo-network";
    }

    protected String getExternalNetworkName() {
        return "public";
    }

    protected String getKeyPairName() {
        return "demo-ssh-key";
    }

    protected String getAuthenticationURL() {
        return "http://192.168.61.158/identity/v3";
    }

    protected String getDomain() {
        return "Default";
    }

    protected String getUserName() {
        return "jean";
    }

    protected String getPassword() {
        return "jean";
    }

    protected String getProjectId() {
        return "8a910fbad4524870aa93aaaf2b903975";
    }

    public static void main(String[] args) throws IOException {
        OpenstackClient openstackClient = new OpenstackClient();
        openstackClient.authenticate();

        //Set<String> serverFloatingIPs = openstackClient.getServerFloatingIPs();

        //openstack server create --flavor ds1G --image CentOS-7-Azure --key-name demo-ssh-key --network demo-network --security-group ssh --wait demo-instance8
        //ssh -i /home/jean/Downloads/demo-ssh-key.pem cirros@172.24.4.7

        //String flavorName = "m1.medium";
        String flavorName = "ds1G";

        //String imageName = "cirros-0.4.0-x86_64-disk";
        //String imageName = "CentOS-7";
        //String imageName = "centos-7-x86-64-1810";
        //String imageName = "CentOS-7-Azure";
        String imageName = "CentOS7-GenericCloud-1811";

//        File userDataFile = new File("D:\\add-cloud-user.yaml");
//        String userDataAsYaml = FileUtils.readFileToString(userDataFile, (Charset)null);

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        String cloudConfigFilePath = "cloud-config.yaml";
        URL cloudConfigUrl = classLoader.getResource(cloudConfigFilePath);
        if (cloudConfigUrl == null) {
            throw new NullPointerException("The " + cloudConfigFilePath + " is missing from the resources.");
        }
        StringWriter userDataAsYaml = new StringWriter();
        try (InputStream inputStream = cloudConfigUrl.openStream()) {
            try (InputStreamReader inputStreamReaderReader = new InputStreamReader(inputStream)) {
                char[] buffer = new char[4 * 1024];
                int bytesRead;
                while ((bytesRead = inputStreamReaderReader.read(buffer)) >= 0) {
                    userDataAsYaml.write(buffer, 0, bytesRead);
                }
            }
        }

        String serverName = "java-centos7-v1";
        Server server = openstackClient.createServer(serverName, flavorName, imageName, "ssh", userDataAsYaml.toString());

//        openstackClient.removeServer("CentOS-7-x86_64-GenericCloud-1602");
//        openstackClient.removeServer("cirros-0.4.0-x86_64-disk");
//        openstackClient.removeServer("CentOS-7-Azure");
//        openstackClient.removeServer("CentOS-7-x86_64-GenericCloud-1602");

        System.out.println("OpenstackClient.main ");
    }
}
