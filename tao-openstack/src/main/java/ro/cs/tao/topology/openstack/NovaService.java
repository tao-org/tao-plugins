package ro.cs.tao.topology.openstack;

import org.openstack4j.api.Builders;
import org.openstack4j.api.OSClient;
import org.openstack4j.api.compute.ComputeFloatingIPService;
import org.openstack4j.api.compute.ServerService;
import org.openstack4j.api.storage.BlockVolumeService;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.common.Identifier;
import org.openstack4j.model.compute.*;
import org.openstack4j.model.compute.builder.ServerCreateBuilder;
import org.openstack4j.model.identity.v3.Service;
import org.openstack4j.model.network.Network;
import org.openstack4j.model.storage.block.Volume;
import org.openstack4j.model.storage.block.VolumeAttachment;
import org.openstack4j.model.storage.block.VolumeType;
import org.openstack4j.openstack.OSFactory;
import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.configuration.ConfigurationProvider;
import ro.cs.tao.topology.NodeDescription;
import ro.cs.tao.topology.NodeFlavor;
import ro.cs.tao.topology.NodeProvider;
import ro.cs.tao.topology.TopologyException;
import ro.cs.tao.topology.openstack.commons.Constants;
import ro.cs.tao.utils.executors.MemoryUnit;

import java.util.*;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * OpenStack topology node provider implementation.
 *
 * @author Cosmin Cara
 */
public class NovaService implements NodeProvider {

    private static final Logger logger = Logger.getLogger(NovaService.class.getName());

    private static final int WAIT_TIMEOUT_MILLISECONDS = 60 * 1000;

    private Server master;

    protected OSClient.OSClientV3 client;

    public NovaService() {
    }

    @Override
    public void authenticate() {
        if (this.client != null) {
            throw new IllegalStateException("The client is already authenticated.");
        }
        final ConfigurationProvider cfgProvider = ConfigurationManager.getInstance();
        final String domain = cfgProvider.getValue(Constants.OPENSTACK_DOMAIN, null);
        if (domain == null || domain.isEmpty()) {
            throw new TopologyException(String.format("Missing configuration key [%s]", Constants.OPENSTACK_DOMAIN));
        }
        final String user = cfgProvider.getValue(Constants.OPENSTACK_USER, null);
        if (user == null || user.isEmpty()) {
            throw new TopologyException(String.format("Missing configuration key [%s]", Constants.OPENSTACK_USER));
        }
        final String password = cfgProvider.getValue(Constants.OPENSTACK_PASSWORD, null);
        if (password == null || password.isEmpty()) {
            throw new TopologyException(String.format("Missing configuration key [%s]", Constants.OPENSTACK_PASSWORD));
        }
        final String authUrl = cfgProvider.getValue(Constants.OPENSTACK_AUTH_URL, null);
        if (authUrl == null || authUrl.isEmpty()) {
            throw new TopologyException(String.format("Missing configuration key [%s]", Constants.OPENSTACK_AUTH_URL));
        }
        final String tenantId = cfgProvider.getValue(Constants.OPENSTACK_TENANT_ID, null);
        if (tenantId == null || tenantId.isEmpty()) {
            throw new TopologyException(String.format("Missing configuration key [%s]", Constants.OPENSTACK_TENANT_ID));
        }

        OSClient.OSClientV3 client = OSFactory.builderV3()
                .endpoint(authUrl)
                .credentials(user, password, Identifier.byName(domain))
                .scopeToProject(Identifier.byId(tenantId))
                .authenticate();

        // hack to force the retrieval of newest version of a service
        List<? extends Service> catalog = client.getToken().getCatalog();
        catalog.sort((Comparator<Service>) (o1, o2) -> o2.getType().compareTo(o1.getType()));

        this.client = client;
    }

    private void checkAuthenticate() {
        if (this.client == null) {
            throw new IllegalStateException("The client is not authenticated.");
        }
    }

    @Override
    public List<NodeFlavor> listFlavors() throws TopologyException {
        checkAuthenticate();

        final List<? extends Flavor> remoteFlavors = this.client.compute().flavors().list();
        List<NodeFlavor> flavors = new ArrayList<>();
        if (remoteFlavors != null) {
            for (Flavor flavor : remoteFlavors) {
                flavors.add(convert(flavor));
            }
        }
        return flavors;
    }

    @Override
    public List<NodeDescription> listNodes() throws TopologyException {
        return listNodes(null); // 'null' => throw exception if the floating ip address is missing
    }

    public List<NodeDescription> listNodes(Boolean addNodeWithoutFloatingIpAddress) {
        checkAuthenticate();

        List<? extends Server> servers = this.client.compute().servers().list();
        List<NodeDescription> remoteNodes = new ArrayList<>();
        if (servers != null) {
            for (Server server : servers) {
                NodeDescription node = convert(server);
                boolean canAddNode = true;
                if (node.getId() == null) {
                    // the node has not assigned a floating ip address
                    if (addNodeWithoutFloatingIpAddress == null) {
                        throw new IllegalArgumentException("The Openstack server '" + server.getName()+"' does not contain the floating IP address.");
                    } else {
                        canAddNode = addNodeWithoutFloatingIpAddress.booleanValue();
                    }
                }
                if (canAddNode) {
                    remoteNodes.add(node);
                }
            }
        }
        return remoteNodes;
    }

    @Override
    public NodeDescription getNode(String nodeName) throws TopologyException {
        authenticate();
        if ("localhost".equals(nodeName)) {
            return null;
        }
        final Server server = this.client.compute().servers().get(nodeName);
        NodeDescription node = null;
        if (server == null) {
            logger.warning(String.format("Node '%s' not found in OpenStack topology", nodeName));
        } else {
            node = convert(server);
        }
        return node;
    }

    @Override
    public NodeDescription create(NodeDescription node) throws TopologyException {
        Volume ssdVolume = null, hddVolume = null;
        Server server = null;
        try {
            authenticate();
            final ConfigurationProvider cfgProvider = ConfigurationManager.getInstance();
            final String ssdType = cfgProvider.getValue(Constants.OPENSTACK_VOLUME_TYPE_SSD, "SSD");
            checkVolumeType(ssdType);
            final String hddType = cfgProvider.getValue(Constants.OPENSTACK_VOLUME_TYPE_HDD, "HDD");
            checkVolumeType(hddType);
            final String defaultSecGroup = cfgProvider.getValue(Constants.OPENSTACK_DEFAULT_SECURITY_GROUP);
            final ServerService serverService = this.client.compute().servers();
            final ServerCreateBuilder builder = serverService.serverBuilder();
            final Flavor flavor = this.client.compute().flavors().list().stream().filter(f -> f.getName().equals(node.getFlavor().getId())).findFirst().orElse(null);
            final List<String> networkIds = getNetworks();
            final String ssdVolumeName = node.getId() + "-ssd";
            final String hddVolumeName = node.getId() + "-hdd";
            final int ssdSize = Integer.parseInt(cfgProvider.getValue(Constants.OPENSTACK_VOLUME_SSD_SIZE, "480"));
            final int hddSize = Integer.parseInt(cfgProvider.getValue(Constants.OPENSTACK_VOLUME_HDD_SIZE, "4096"));
            ssdVolume = createVolume(ssdVolumeName, ssdType, ssdSize);
            hddVolume = createVolume(hddVolumeName, hddType, hddSize > 0 ? hddSize : node.getFlavor().getDisk());
            final BlockDeviceMappingCreate sddCreate = Builders.blockDeviceMapping().uuid(ssdVolume.getId()).deviceName("/dev/sdb").bootIndex(0).build();
            final BlockDeviceMappingCreate hddCreate = Builders.blockDeviceMapping().uuid(hddVolume.getId()).deviceName("/dev/sdc").bootIndex(1).build();
            builder.name(node.getId())
                    .flavor(flavor)
                    .image(this.master != null ?
                                   this.master.getImageId() :
                                   getImageId(cfgProvider.getValue(Constants.OPENSTACK_OS_IMAGE, "CentOS 7")));
            builder.networks(networkIds);
            final ServerCreate newServer = builder.addSecurityGroup(defaultSecGroup)
                    .blockDevice(sddCreate)
                    .blockDevice(hddCreate)
                    .addAdminPass(node.getUserPass())
                    .build();
            server = serverService.bootAndWaitActive(newServer, WAIT_TIMEOUT_MILLISECONDS);
            if (server == null) {
                throw new TopologyException("OpenStack node was not created");
            }
            return convert(server);
        } catch (Exception ex) {
            deleteVolume(ssdVolume);
            deleteVolume(hddVolume);
            deleteServer(server);
            throw new TopologyException(ex.getMessage());
        }
    }

    @Override
    public NodeDescription create(NodeFlavor flavor, String name, String description, String user, String pwd) throws TopologyException {
        NodeDescription node = new NodeDescription();
        node.setFlavor(flavor);
        node.setId(name);
        node.setDescription(description);
        node.setUserName(user);
        node.setUserPass(pwd);
        return create(node);
    }

    @Override
    public void remove(String nodeName) throws TopologyException {
        authenticate();
        final ServerService serverService = this.client.compute().servers();
        Server server = serverService.get(nodeName);
        if (server == null) {
            logger.warning(String.format("Node %s doesn't exist on %s", nodeName, getClass().getSimpleName()));
        } else {
            final List<Address> IPtoDelete = new ArrayList<>();
            final ComputeFloatingIPService floatingIPService = this.client.compute().floatingIps();
            for (List<? extends Address> addresses : server.getAddresses().getAddresses().values()) {
                addresses.stream().filter(a -> a.getType().equalsIgnoreCase("floating")).findFirst().ifPresent(IPtoDelete::add);
            }
            serverService.delete(nodeName);
            final Map<String, ? extends FloatingIP> ipMap = floatingIPService.list().stream().collect(Collectors.toMap(FloatingIP::getFixedIpAddress, Function.identity()));
            for (Address address : IPtoDelete) {
                floatingIPService.deallocateIP(ipMap.get(address.getAddr()).getId());
            }
        }
    }

    @Override
    public void suspend(String nodeName) throws TopologyException {
        authenticate();
        final ServerService serverService = this.client.compute().servers();
        final Server server = serverService.get(nodeName);
        if (server == null) {
            throw new TopologyException(String.format("Node '%s' is not created", nodeName));
        }
        final ActionResponse response = serverService.action(server.getId(), Action.SUSPEND);
        if (!response.isSuccess()) {
            throw new TopologyException(String.format("Suspending node '%s' failed. Reason: %s",
                                                      nodeName, response.getFault()));
        }
    }

    @Override
    public void resume(String nodeName) throws TopologyException {
        authenticate();
        final ServerService serverService = this.client.compute().servers();
        final Server server = serverService.get(nodeName);
        if (server == null) {
            throw new TopologyException(String.format("Node '%s' is not created", nodeName));
        }
        final ActionResponse response = serverService.action(server.getId(), Action.RESUME);
        if (!response.isSuccess()) {
            throw new TopologyException(String.format("Resuming node '%s' failed. Reason: %s",
                                                      nodeName, response.getFault()));
        }
    }

    @Override
    public NodeDescription update(NodeDescription node) throws TopologyException {
        // Update not implemented for now
        return node;
    }

    private Volume createVolume(String volumeName, String volumeType, int sizeGB) {
        final BlockVolumeService volumeService = this.client.blockStorage().volumes();
        Volume volume = volumeService.get(volumeName);
        if (volume == null) { // the volume doesn't exist
            volume = volumeService.create(Builders.volume()
                                                  .name(volumeName)
                                                  .volumeType(volumeType)
                                                  .size(sizeGB)
                                                  .build());
        } else {
            final List<? extends VolumeAttachment> attachments = volume.getAttachments();
            if (attachments != null && attachments.size() > 0) {
                // the volume exists and already has attachments
                throw new TopologyException("The volume '%s' already exists and is attached [%s]",
                                            volumeName,
                                            attachments.stream().map(VolumeAttachment::getHostname).collect(Collectors.joining(",")));
            }
        }
        return volume;
    }

    private void deleteVolume(Volume volume) {
        if (volume != null) {
            final BlockVolumeService volumeService = this.client.blockStorage().volumes();
            if (volumeService.get(volume.getId()) != null) { // make sure it exists
                final ActionResponse response = volumeService.delete(volume.getId());
                if (!response.isSuccess()) {
                    logger.severe(String.format("Volume '%s' could not be deleted. Reason: %s",
                                                volume.getId(), response.getFault()));
                }
            }
        }
    }

    private void deleteServer(Server server) {
        if (server != null) {
            final ServerService serverService = this.client.compute().servers();
            if (serverService.get(server.getId()) != null) { // make sure it exists
                final ActionResponse response = serverService.delete(server.getId());
                if (!response.isSuccess()) {
                    logger.severe(String.format("Server '%s' could not be deleted. Reason: %s",
                                                server.getName(), response.getFault()));
                }
            }
        }
    }

    private List<String> getNetworks() {
        final List<String> networkIds = this.master != null ?
                new ArrayList<>(this.master.getAddresses().getAddresses().keySet()) : new ArrayList<>();
        if (networkIds.size() == 0) {
            final ConfigurationProvider cfgProvider = ConfigurationManager.getInstance();
            final String privateNetworkName = cfgProvider.getValue(Constants.OPENSTACK_PRIVATE_NETWORK);
            final String eoDataNetworkName = cfgProvider.getValue(Constants.OPENSTACK_DATA_NETWORK);
            if (privateNetworkName != null || eoDataNetworkName != null) {
                for (Network network : this.client.networking().network().list()) {
                    if ((privateNetworkName != null && privateNetworkName.equals(network.getName())) ||
                            (eoDataNetworkName != null && eoDataNetworkName.equals(network.getName()))) {
                        networkIds.add(network.getId());
                    }
                }
            }
        }
        return networkIds;
    }

    private void checkVolumeType(String volumeType) {
        final List<? extends VolumeType> volumeTypes = this.client.blockStorage().volumes().listVolumeTypes();
        if (volumeType == null) {
            throw new TopologyException("The remote provider API returned an empty VolumeType list");
        }
        if (volumeTypes.stream().noneMatch(vt -> vt.getName().equals(volumeType))) {
            throw new TopologyException("The VolumeType '%s' doesn't exist", volumeType);
        }
    }

    private String getImageId(String imageName) {
        org.openstack4j.model.image.v2.Image image = findImageByName(imageName);
        if (image == null) {
            throw new TopologyException("Image '%s' doesn't exist", imageName);
        }
        return image.getId();
    }

    private org.openstack4j.model.image.v2.Image findImageByName(String imageName) {
        String projectId = this.client.getToken().getProject().getId();
        List<? extends org.openstack4j.model.image.v2.Image> images = this.client.imagesV2().list();
        for (org.openstack4j.model.image.v2.Image image : images) {
            if (imageName.equalsIgnoreCase(image.getName())) {
                if (org.openstack4j.model.image.v2.Image.ImageVisibility.PUBLIC == image.getVisibility()) {
                    return image;
                } else if (org.openstack4j.model.image.v2.Image.ImageVisibility.PRIVATE == image.getVisibility() && image.getOwner().equals(projectId)) {
                    return image;
                }
            }
        }
        return null;
    }

    protected static NodeFlavor convert(Flavor flavor) {
        return flavor != null ?
                new NodeFlavor(flavor.getName(), flavor.getVcpus(), flavor.getRam(),
                               flavor.getDisk(), flavor.getSwap() == 0
                                                 ? flavor.getRam() / MemoryUnit.KB.value().intValue() * 2
                                                 : flavor.getSwap(),
                               flavor.getRxtxFactor()) : null;
    }

    protected NodeDescription convert(Server server) {
        NodeDescription node = null;
        if (server != null) {
            node = new NodeDescription();
            node.setId(server.getHostId());
            node.setFlavor(convert(server.getFlavor()));
            node.setUserName(server.getUserId());
            node.setUserPass(server.getAdminPass());
            node.setDescription(server.getAccessIPv4());
            node.setActive(server.getStatus() == Server.Status.ACTIVE);
            node.setVolatile(true);
        }
        return node;
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

    public void removeServer(String serverName) {
        checkAuthenticate();

        Server server = findServerByName(serverName);
        if (server != null) {
            // the server exists in the Openstack
            ComputeFloatingIPService floatingIPService = this.client.compute().floatingIps();
            List<Address> floatingAddressesToDelete = new ArrayList<>();
            for (List<? extends Address> addresses : server.getAddresses().getAddresses().values()) {
                for (Address address : addresses) {
                    if (address.getType().equalsIgnoreCase("floating")) {
                        floatingAddressesToDelete.add(address);
                    }
                }
            }

            // delete the server from the Openstack
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

    public String createServer(String serverName, String flavorName, String imageName, String securityGroupName, String privateNetworkName,
                               String publicNetworkName, String keypairName, String userDataAsYaml) {

        checkAuthenticate();

        Server server = findServerByName(serverName);
        if (server != null) {
            throw new IllegalArgumentException("Duplicate server name '" + serverName + "'.");
        }

        Network network = findNetworkByName(privateNetworkName);
        if (network == null) {
            throw new NullPointerException("The private network name '" + privateNetworkName + "' does not exist.");
        }
        List<String> networkIds = new ArrayList<>(1);
        networkIds.add(network.getId());

        Flavor flavor = findFlavorByName(flavorName);
        if (flavor == null) {
            throw new NullPointerException("The flavor name '" + flavorName + "' does not exist.");
        }

        ServerService serverService = this.client.compute().servers();
        ServerCreateBuilder builder = serverService.serverBuilder();
        builder.name(serverName)
                .flavor(flavor)
                .image(getImageId(imageName))
                .networks(networkIds)
                .addSecurityGroup(securityGroupName);
        if (keypairName != null) {
            builder.keypairName(keypairName);
        }
        if (userDataAsYaml != null) {
            String encodedString = Base64.getEncoder().encodeToString(userDataAsYaml.getBytes());
            builder.userData(encodedString);
        }

        ServerCreate newServerToCreate = builder.build();
        server = serverService.bootAndWaitActive(newServerToCreate, WAIT_TIMEOUT_MILLISECONDS);
        if (server.getStatus() != Server.Status.ACTIVE) {
            throw new IllegalStateException("The server '" + serverName +"' is not active. Its status is " + server.getStatus() + ".");
        }

        ComputeFloatingIPService computeFloatingIPService = this.client.compute().floatingIps();
        boolean success = false;
        String floatingIpAddress;
        FloatingIP floatingIP = computeFloatingIPService.allocateIP(publicNetworkName);
        try {
            // the floating IP address has been successfully create in Openstack
            ActionResponse actionResponse = computeFloatingIPService.addFloatingIP(server, floatingIP.getFloatingIpAddress());
            if (actionResponse.getFault() != null) {
                throw new IllegalStateException("The floating IP has not been allocated. Message: " + actionResponse.getFault() +".");
            }
            floatingIpAddress = floatingIP.getFloatingIpAddress();
            success = true;
        } finally {
            if (!success) {
                // deallocate the IP address
                computeFloatingIPService.deallocateIP(floatingIP.getId());
            }
        }
        return floatingIpAddress;
    }

    private Flavor findFlavorByName(String flavorName) {
        List<? extends Flavor> flavors = this.client.compute().flavors().list();
        for (Flavor flavor : flavors) {
            if (flavorName.equals(flavor.getName())) {
                return flavor;
            }
        }
        return null;
    }

    private Network findNetworkByName(String networkName) {
        List<? extends Network> networks = this.client.networking().network().list();
        for (Network network : networks) {
            if (networkName.equals(network.getName())) {
                return network;
            }
        }
        return null;
    }

    protected static String getServerFloatingIP(Server server) {
        for (List<? extends Address> addresses : server.getAddresses().getAddresses().values()) {
            for (Address address : addresses) {
                if (address.getType().equalsIgnoreCase("floating")) {
                    return address.getAddr();
                }
            }
        }
        return null;
    }


    private static String buildAddGroupCommand(String groupName, Integer groupId) {
        String command = "groupadd";
        if (groupId != null) {
            command += " -g " + groupId.intValue();
        }
        command += " " + groupName;
        return command;
    }

    private static String buildAddUserCommand(String userName, String password, Integer userId, Integer groupId) {
//        sudo useradd -p $(openssl passwd -1 milan) -u 4321 -g 2345 -s /bin/bash -m milan
        StringBuilder command = new StringBuilder();
        command.append("useradd -p $(openssl passwd -1 ")
                .append(password)
                .append(")")
                .append(" -s /bin/bash")
                .append(" -m")
                .append(" -d /home/")
                .append(userName);
        if (userId != null) {
            command.append(" -u ")
                    .append(userId.intValue());
        }
        if (groupId != null) {
            command.append(" -g ")
                    .append(groupId.intValue());
        }
        command.append(" ")
                .append(userName);
        return command.toString();
    }

    public static String buildCloudConfig(String userName, String password, Integer userId, String groupName, Integer groupId,
                                          String sshPublicKeyContent, boolean permitRootLogin, boolean passwordAuthentication) {

        List<String> commands = new ArrayList<>();

        String sshdConfigFilePath = "/etc/ssh/sshd_config";
        String permitRootLoginValue = permitRootLogin ? "yes" : "no";
        String passwordAuthenticationValue = passwordAuthentication ? "yes" : "no";

        commands.add("sed -i -e '/^[# ]*PermitRootLogin/s/^.*$//' " + sshdConfigFilePath);
        commands.add("sed -i -e '/^[# ]*PasswordAuthentication/s/^.*$//' " + sshdConfigFilePath);
        commands.add("sed -i -e '/^[# ]*PermitEmptyPasswords/s/^.*$//' " + sshdConfigFilePath);
        commands.add("sed -i -e '$aPermitRootLogin " + permitRootLoginValue + "' " + sshdConfigFilePath);
        commands.add("sed -i -e '$aPasswordAuthentication " + passwordAuthenticationValue + "' " + sshdConfigFilePath);
        commands.add("sed -i -e '$aPermitEmptyPasswords no' " + sshdConfigFilePath);
        commands.add("systemctl restart sshd");
        commands.add("groupmod -g " + groupId.intValue() + " " + groupName);
        commands.add("usermod -u " + userId.intValue() + " " + userName);

//        commands.add(buildAddGroupCommand(groupName, groupId));
//        commands.add(buildAddUserCommand(userName, password, userId, groupId));
//        commands.add("usermod -a -G wheel,adm,systemd-journal " + userName);

        CloudConfigHelper cloudConfigHelper = new CloudConfigHelper();
        cloudConfigHelper.addGroupUser(groupName);
        cloudConfigHelper.addDefaultUser(userName, groupName, sshPublicKeyContent);
        cloudConfigHelper.addDefaultUserToUsers();
        cloudConfigHelper.addChangeUserPasswords("root", userName, password);

//        cloudConfigHelper.addNoUsers();
//        cloudConfigHelper.addChangeUserPasswords("root", "root");
        cloudConfigHelper.addRunCommands(commands);
        return cloudConfigHelper.getContent();
    }
}
