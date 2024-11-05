package ro.cs.tao.topology.openstack;

import org.openstack4j.api.Builders;
import org.openstack4j.api.OSClient;
import org.openstack4j.api.compute.ComputeFloatingIPService;
import org.openstack4j.api.compute.ComputeService;
import org.openstack4j.api.compute.QuotaSetService;
import org.openstack4j.api.compute.ServerService;
import org.openstack4j.api.storage.BlockVolumeService;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.compute.*;
import org.openstack4j.model.compute.builder.ServerCreateBuilder;
import org.openstack4j.model.image.v2.Image;
import org.openstack4j.model.network.Network;
import org.openstack4j.model.storage.block.Volume;
import org.openstack4j.model.storage.block.VolumeAttachment;
import org.openstack4j.model.storage.block.VolumeType;
import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.configuration.ConfigurationProvider;
import ro.cs.tao.topology.*;
import ro.cs.tao.topology.openstack.commons.Constants;
import ro.cs.tao.topology.openstack.commons.OpenStackSession;
import ro.cs.tao.topology.openstack.commons.PlatformUsage;
import ro.cs.tao.utils.StringUtilities;
import ro.cs.tao.utils.executors.MemoryUnit;
import ro.cs.tao.utils.executors.SSHExecutor;
import ro.cs.tao.utils.executors.SSHMode;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
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

    private OpenStackSession session;

    public NovaService() {
    }

    @Override
    public void authenticate() {
        session = OpenStackSession.getInstance();
    }

    private void checkAuthenticate() {
        if (session == null) {
            throw new IllegalStateException("The client is not authenticated.");
        }
    }

    @Override
    public List<NodeFlavor> listFlavors() throws TopologyException {
        checkAuthenticate();

        final List<? extends Flavor> remoteFlavors = session.computeService().flavors().list();
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

        List<? extends Server> servers = session.computeService().servers().list();
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

    public PlatformUsage getPlatformQuota() {
        checkAuthenticate();
        final QuotaSetService quotaSets = session.computeService().quotaSets();
        final ComputeQuotaDetail detail = quotaSets.getDetail(session.getTenantId());
        return new PlatformUsage(detail.getCores().getLimit(),
                                 detail.getCores().getInUse(),
                                 detail.getRam().getLimit(),
                                 detail.getRam().getInUse(),
                                 -1, -1);
    }

    @Override
    public NodeDescription getNode(String nodeName) throws TopologyException {
        authenticate();
        if ("localhost".equals(nodeName)) {
            return null;
        }
        final Server server = session.computeService().servers().get(nodeName);
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
            checkAuthenticate();
            final ConfigurationProvider cfgProvider = ConfigurationManager.getInstance();
            final String ssdType = cfgProvider.getValue(Constants.OPENSTACK_VOLUME_TYPE_SSD, "ssd");
            final String hddType = cfgProvider.getValue(Constants.OPENSTACK_VOLUME_TYPE_HDD, "hdd");
            final ComputeService service = session.computeService();
            checkVolumeType(ssdType);
            checkVolumeType(hddType);
            //final String defaultSecGroup = cfgProvider.getValue(Constants.OPENSTACK_DEFAULT_SECURITY_GROUP);
            final ServerService serverService = service.servers();
            server = serverService.list().stream().filter(s -> node.getId().equals(s.getName())).findFirst().orElse(null);
            if (server == null) {
                final ServerCreateBuilder builder = serverService.serverBuilder();
                final String defaultFlavourName = cfgProvider.getValue(Constants.OPENSTACK_DEFAULT_FLAVOUR);
                Flavor defaultFlavour = null;
                if (!StringUtilities.isNullOrEmpty(defaultFlavourName)) {
                    defaultFlavour = service.flavors().list().stream().filter(f -> f.getName().equals(defaultFlavourName)).findFirst().get();
                }
                String imageId = cfgProvider.getValue(Constants.OPENSTACK_OS_IMAGE);
                Image image;
                Flavor flavour = service.flavors().list().stream().filter(f -> f.getName().equals(node.getFlavor().getId())).findFirst().orElse(null);
                if (flavour == null) {
                    flavour = defaultFlavour;
                }
                // check to see if there are enough resources available
                final PlatformUsage platformQuota = getPlatformQuota();
                final int availableCPUs = platformQuota.getMaxCpus() - platformQuota.getUsedCpus();
                final int availableMemory = platformQuota.getMaxMemory() - platformQuota.getUsedMemory();
                final int requestedCPUs = flavour.getVcpus();
                final int requestedMemory = flavour.getRam();
                if (requestedCPUs > availableCPUs || requestedMemory > availableMemory) {
                    throw new TopologyException(String.format("Not enough platform resources to create the requested machine [requested: %d CPUs, %d MB memory; available: %d CPUs, %d MB memory]",
                                                              requestedCPUs, requestedMemory, availableCPUs, availableMemory));
                }
                if (imageId == null) {
                    image = session.imageService().list().stream().filter(i -> i.getName().equals("CentOS 7")).findFirst().orElse(null);
                    if (image != null) {
                        imageId = image.getId();
                    }
                } else {
                    final String iid = imageId;
                    image = session.imageService().get(iid);//list().stream().filter(i -> i.getId().equals(iid)).findFirst().orElse(null);
                    if (image == null) {
                        throw new TopologyException("Base image not found");
                    }
                    if (flavour != null && image.getMinRam() != null && requestedMemory < image.getMinRam()) {
                        throw new TopologyException("Flavor has less RAM than required by the OS image");
                    }
                }
                if (imageId == null) {
                    throw new TopologyException("No base image defined");
                }
                /*if (defaultFlavour != null &&
                        (flavour != null && (flavour.getVcpus() < defaultFlavour.getVcpus() || flavour.getRam() < defaultFlavour.getRam()))) {
                    flavour = defaultFlavour;
                }*/
                final List<String> networkIds = getNetworks();
                builder.name(node.getId())
                       .flavor(flavour)
                       .image(imageId);
                builder.networks(networkIds);//.addSecurityGroup(defaultSecGroup);
                final ServerCreate newServer = builder.addAdminPass(node.getUserPass())
                                                      .keypairName(cfgProvider.getValue("openstack.keypair.name", ""))
                                                      .availabilityZone("nova")
                                                      .build();
                server = serverService.bootAndWaitActive(newServer, WAIT_TIMEOUT_MILLISECONDS);
                if (server == null) {
                    throw new TopologyException("OpenStack node was not created");
                }
                // It may take some time until the new server services are started, so let's poll for login prompt
                String consoleLog = serverService.getConsoleOutput(server.getId(), 5);
                final int maxWaitTime = 3 * 60 * 1000;
                int waitTime = 0;
                while (!consoleLog.contains(node.getId() + " login:") && waitTime < maxWaitTime) {
                    try {
                        Thread.sleep(5000);
                        waitTime += 5000;
                    } catch (InterruptedException ignored) { }
                    consoleLog = serverService.getConsoleOutput(server.getId(), 5);
                }
                final String ssdVolumeName = node.getId() + "-ssd";
                final String hddVolumeName = node.getId() + "-hdd";
                final int ssdSize = Integer.parseInt(cfgProvider.getValue(Constants.OPENSTACK_VOLUME_SSD_SIZE, "100"));
                final int hddSize = Integer.parseInt(cfgProvider.getValue(Constants.OPENSTACK_VOLUME_HDD_SIZE, "0"));
                if (ssdSize > 0) {
                    ssdVolume = createVolume(ssdVolumeName, ssdType, ssdSize);
                }
                if (hddSize > 0) {
                    hddVolume = createVolume(hddVolumeName, hddType, hddSize);
                }
                final boolean useKey = node.getSshKey() != null;
                if (ssdVolume != null) {
                    final String ssdDevice = cfgProvider.getValue(Constants.OPENSTACK_VOLUME_SSD_DEVICE, "/dev/sds");
                    serverService.attachVolume(server.getId(), ssdVolume.getId(), ssdDevice);
                    if (mountDevice(server.getName(), node.getUserName(),
                                    useKey ? node.getSshKey() : node.getUserPass(), useKey, ssdDevice)) {
                        logger.fine("Volume " + ssdVolumeName + " was mounted on " + ssdDevice);
                    } else {
                        logger.warning("Volume " + ssdVolumeName + " was not mounted");
                    }
                }
                if (hddVolume != null) {
                    final String hddDevice = cfgProvider.getValue(Constants.OPENSTACK_VOLUME_HDD_DEVICE, "/dev/sdh");
                    serverService.attachVolume(server.getId(), hddVolume.getId(), hddDevice);
                    if (mountDevice(server.getName(), node.getUserName(),
                                    useKey ? node.getSshKey() : node.getUserPass(), useKey, hddDevice)) {
                        logger.fine("Volume " + hddVolumeName + " was mounted on " + hddDevice);
                    } else {
                        logger.warning("Volume " + hddVolumeName + " was not mounted");
                    }
                }
                if (disableSELinux(server.getName(), node.getUserName(),
                                   useKey ? node.getSshKey() : node.getUserPass(), useKey)) {
                    logger.fine("SELinux disabled");
                } else {
                    logger.warning("SELinux was not disabled");
                }
            }
            final NodeDescription newNode = convert(node, server, node.getVolatile());
            newNode.setUserPass(node.getUserPass());
            return newNode;
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
        checkAuthenticate();
        final ServerService serverService = session.computeService().servers();
        Server server = serverService.get(nodeName);
        if (server == null) {
            logger.warning(String.format("Node %s doesn't exist on %s", nodeName, getClass().getSimpleName()));
        } else {
            final BlockVolumeService volumeService = session.blockStorageService().volumes();
            Volume volume = volumeService.get(nodeName + "-ssd");
            if (volume != null) {
                deleteVolume(volume);
            }
            volume = volumeService.get(nodeName + "-hdd");
            if (volume != null) {
                deleteVolume(volume);
            }
            deleteServer(server);
        }
    }

    @Override
    public void suspend(String nodeName) throws TopologyException {
        checkAuthenticate();
        final ServerService serverService = session.computeService().servers();
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
        checkAuthenticate();
        final ServerService serverService = session.computeService().servers();
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
        final BlockVolumeService volumeService = session.blockStorageService().volumes();
        Volume volume = volumeService.get(volumeName);
        if (volume == null) { // the volume doesn't exist
            volume = volumeService.create(Builders.volume()
                                                  .name(volumeName)
                                                  .volumeType(volumeType)
                                                  .size(sizeGB)
                                                  .build());
        } else {
            final List<? extends VolumeAttachment> attachments = volume.getAttachments();
            if (attachments != null && !attachments.isEmpty()) {
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
            final BlockVolumeService volumeService = session.blockStorageService().volumes();
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
            final ServerService serverService = session.computeService().servers();
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
        final List<String> networkIds = new ArrayList<>();
        final ConfigurationProvider cfgProvider = ConfigurationManager.getInstance();
        final String privateNetworkName = cfgProvider.getValue(Constants.OPENSTACK_PRIVATE_NETWORK);
        final String eoDataNetworkName = cfgProvider.getValue(Constants.OPENSTACK_DATA_NETWORK);
        if (privateNetworkName != null || eoDataNetworkName != null) {
            final List<? extends Network> networks = session.networkService().network().list();
            for (Network network : networks) {
                if ((privateNetworkName != null && privateNetworkName.equals(network.getName())) ||
                        (eoDataNetworkName != null && eoDataNetworkName.equals(network.getName()))) {
                    networkIds.add(network.getId());
                }
            }
        }
        return networkIds;
    }

    private void checkVolumeType(String volumeType) {
        final List<? extends VolumeType> volumeTypes = session.blockStorageService().volumes().listVolumeTypes();
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
        final OSClient.OSClientV3 osClient = session.getService();
        String projectId = osClient.getToken().getProject().getId();
        List<? extends org.openstack4j.model.image.v2.Image> images = osClient.imagesV2().list();
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

    protected NodeDescription convert(NodeDescription initial, Server server, boolean isVolatile) {
        NodeDescription node = null;
        if (server != null) {
            node = new NodeDescription();
            node.setId(initial.getId());
            node.setFlavor(convert(server.getFlavor()));
            node.setUserName(initial.getUserName());
            node.setUserPass(initial.getUserPass());
            node.setSshKey(initial.getSshKey());
            node.setDescription(initial.getDescription());
            node.setActive(server.getStatus() == Server.Status.ACTIVE);
            node.setRole(NodeRole.WORKER);
            node.setVolatile(isVolatile);
            node.setServerId(server.getId());
            node.setAppId(initial.getAppId());
            node.setOwner(initial.getOwner());
        }
        return node;
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
        }
        return node;
    }

    private Server findServerByName(String serverName) {
        List<? extends Server> servers = session.computeService().servers().list();
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
            ComputeFloatingIPService floatingIPService = session.computeService().floatingIps();
            List<Address> floatingAddressesToDelete = new ArrayList<>();
            final Collection<List<? extends Address>> values = server.getAddresses().getAddresses().values();
            for (List<? extends Address> addresses : values) {
                for (Address address : addresses) {
                    if (address.getType().equalsIgnoreCase("floating")) {
                        floatingAddressesToDelete.add(address);
                    }
                }
            }

            // delete the server from the Openstack
            ActionResponse actionResponse = session.computeService().servers().delete(server.getId());
            if (actionResponse.getFault() != null) {
                throw new IllegalStateException("The server name '" + serverName+"' has not been deleted. Message: " + actionResponse.getFault()+".");
            }

            if (!floatingAddressesToDelete.isEmpty()) {
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

        ServerService serverService = session.computeService().servers();
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

        ComputeFloatingIPService computeFloatingIPService = session.computeService().floatingIps();
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
        List<? extends Flavor> flavors = session.computeService().flavors().list();
        for (Flavor flavor : flavors) {
            if (flavorName.equals(flavor.getName())) {
                return flavor;
            }
        }
        return null;
    }

    private Network findNetworkByName(String networkName) {
        List<? extends Network> networks = session.networkService().network().list();
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

    private boolean mountDevice(String hostName, String user, String credential, boolean useKey, String deviceName) {
        final List<String> args = new ArrayList<>();
        final String name = deviceName.substring(deviceName.lastIndexOf('/') + 1);
        args.add("DEVICE=\"" + deviceName + "\" && umount $DEVICE 2> /dev/null && echo -e \"o\\nn\\np\\n1\\n\\n\\nw\" | fdisk $DEFICE && " +
                         "partprobe $DEVICE && mkfs.ext4 ${DEVICE}1 && mkdir -p /mnt/wrk && mount ${DEVICE}1 /mnt/" + name + " && " +
                         "echo \"${DEVICE}1 /mnt/" + name + " ext4 defaults 0 0\" >> /etc/fstab");
        final SSHExecutor executor = new SSHExecutor(hostName, args, true, SSHMode.EXEC);
        executor.setUser(user);
        if (useKey) {
            executor.setCertificate(credential);
        } else {
            executor.setPassword(credential);
        }
        try {
            return executor.execute(true) == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean disableSELinux(String hostName, String user, String credential, boolean useKey) {
        final List<String> args = new ArrayList<>();
        args.add("setenforce");
        args.add("0");
        final SSHExecutor executor = new SSHExecutor(hostName, args, true, SSHMode.EXEC);
        executor.setUser(user);
        if (useKey) {
            executor.setCertificate(credential);
        } else {
            executor.setPassword(credential);
        }
        try {
            return executor.execute(true) == 0;
        } catch (Exception e) {
            return false;
        }
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
