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

    /**
     * Retrieves a list of node descriptions from the OpenStack server. Nodes without a floating
     * IP address can optionally be included or excluded based on the parameter provided.
     *
     * @param addNodeWithoutFloatingIpAddress indicates whether nodes that do not have a floating
     *        IP address should be included in the result. If null, an exception will be thrown
     *        for nodes without floating IPs.
     * @return a list of {@code NodeDescription} objects representing the nodes present in the
     *         OpenStack environment.
     * @throws IllegalArgumentException if addNodeWithoutFloatingIpAddress is null and a node
     *         without a floating IP is encountered.
     * @throws IllegalStateException if the client is not authenticated.
     */
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

    /**
     * Retrieves the platform quota details for the current tenant, including CPU and memory usage.
     * The method ensures authentication and fetches detailed quota information from the OpenStack
     * compute service. The returned object includes maximum limits and current usage for CPUs and memory.
     *
     * @return an instance of {@code PlatformUsage} representing the platform's quota details,
     * including maximum and used CPUs and memory. Storage-related details are defaulted to -1.
     * @throws IllegalStateException if the client is not authenticated.
     */
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

    /**
     * Creates or retrieves an existing volume with the specified parameters.
     * If the volume does not exist, a new one is created. If the volume already
     * exists and has attachments, an exception is thrown.
     *
     * @param volumeName the name of the volume to create or retrieve
     * @param volumeType the type of the volume to be created
     * @param sizeGB the size of the volume in gigabytes
     * @return the created or retrieved {@code Volume} instance
     * @throws TopologyException if the volume already exists and has active attachments
     */
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

    /**
     * Deletes a specified volume if it exists. The method checks for the presence of
     * the volume in the storage system before performing the deletion. If the deletion
     * fails, an error message is logged with the reason for the failure.
     *
     * @param volume the {@code Volume} instance to be deleted. If the volume is null,
     *               the method does nothing.
     */
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

    /**
     * Deletes a specified server if it exists. This method checks for the existence of the server
     * in the compute service before attempting the deletion. If the deletion fails, an error message
     * with the reason for the failure is logged.
     *
     * @param server the {@code Server} instance to be deleted. If the server is null,
     *               the method does nothing.
     */
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

    /**
     * Retrieves a list of network IDs filtered by names specified in the configuration.
     * The method checks for the presence of specific network names in the configuration
     * and retrieves the corresponding network IDs from the OpenStack network service.
     *
     * @return a list of network IDs that match the configured network names. If no matching
     *         networks are found, an empty list is returned.
     */
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

    /**
     * Verifies that the specified volume type exists in the list of available volume types
     * provided by the remote block storage service.
     *
     * @param volumeType the name of the volume type to verify. If null, an exception is thrown.
     * @throws TopologyException if the volume type list is empty, or if the specified volume type
     *                           does not exist in the list.
     */
    private void checkVolumeType(String volumeType) {
        final List<? extends VolumeType> volumeTypes = session.blockStorageService().volumes().listVolumeTypes();
        if (volumeType == null) {
            throw new TopologyException("The remote provider API returned an empty VolumeType list");
        }
        if (volumeTypes.stream().noneMatch(vt -> vt.getName().equals(volumeType))) {
            throw new TopologyException("The VolumeType '%s' doesn't exist", volumeType);
        }
    }

    /**
     * Retrieves the unique identifier (ID) of an image based on its name. If the
     * image does not exist, an exception is thrown.
     *
     * @param imageName the name of the image for which to retrieve the ID
     * @return the ID of the image corresponding to the given name
     * @throws TopologyException if the image with the specified name does not exist
     */
    private String getImageId(String imageName) {
        org.openstack4j.model.image.v2.Image image = findImageByName(imageName);
        if (image == null) {
            throw new TopologyException("Image '%s' doesn't exist", imageName);
        }
        return image.getId();
    }

    /**
     * Searches for an image by its name in the OpenStack image service.
     * The method returns the image if it matches the given name and its visibility
     * is either public, or private and owned by the current project.
     *
     * @param imageName the name of the image to look for; case-insensitive
     * @return the {@code Image} object that matches the specified name and visibility criteria,
     *         or {@code null} if no such image is found
     */
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

    /**
     * Converts a {@code Flavor} object into a {@code NodeFlavor} object.
     *
     * @param flavor the {@code Flavor} object to be converted. If null, the method will return null.
     * @return a {@code NodeFlavor} object containing the equivalent properties of the input
     *         {@code Flavor}, or null if the input {@code Flavor} is null.
     */
    protected static NodeFlavor convert(Flavor flavor) {
        return flavor != null ?
                new NodeFlavor(flavor.getName(), flavor.getVcpus(), flavor.getRam(),
                               flavor.getDisk(), flavor.getSwap(), flavor.getRxtxFactor(),
                               MemoryUnit.MB) : null;
    }

    /**
     * Converts an initial {@code NodeDescription} based on provided {@code Server} and volatility state.
     * This method generates a new {@code NodeDescription} object, populating its properties
     * using the provided {@code initial NodeDescription}, {@code Server} data, and the {@code isVolatile} flag.
     *
     * @param initial the initial {@code NodeDescription} to be converted; must not be null.
     * @param server the {@code Server} object that provides additional data for populating the {@code NodeDescription}.
     *               If null, the method will return null.
     * @param isVolatile a boolean flag indicating whether the resulting {@code NodeDescription} should be marked as volatile.
     * @return a new {@code NodeDescription} populated with the data from the {@code initial NodeDescription} and {@code Server},
     *         or null if the provided {@code server} is null.
     */
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

    /**
     * Converts a {@code Server} object into a {@code NodeDescription} object.
     * This method generates a new {@code NodeDescription} based on the properties of the provided {@code Server}.
     * If the input {@code Server} is null, the method will return null.
     *
     * @param server the {@code Server} object to be converted. If null, the method will return null.
     * @return a {@code NodeDescription} object containing data from the input {@code Server},
     *         or null if the input {@code Server} is null.
     */
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

    /**
     * Searches for a server by its name in the current session's compute service.
     * Iterates through the list of available servers and returns the server whose name matches
     * the specified server name.
     *
     * @param serverName the name of the server to find. Must not be null.
     * @return the {@code Server} object with the specified name, or {@code null} if no such server is found.
     */
    private Server findServerByName(String serverName) {
        List<? extends Server> servers = session.computeService().servers().list();
        for (Server server : servers) {
            if (serverName.equals(server.getName())) {
                return server;
            }
        }
        return null;
    }

    /**
     * Removes a server identified by its name and cleans up associated resources such as floating IPs.
     *
     * @param serverName the name of the server to be removed
     * @throws IllegalStateException if the server or its associated floating IPs cannot be deleted
     */
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

    /**
     * Creates a new server in the cloud environment with the specified configuration.
     * The server will be assigned a floating IP address upon creation.
     *
     * @param serverName the name of the server to be created; must be unique
     * @param flavorName the flavor or instance type for the server; determines resources like CPU and memory
     * @param imageName the name of the image used to boot the server; specifies the operating system or application
     * @param securityGroupName the security group applied to the server; defines network access rules
     * @param privateNetworkName the name of the private network to attach the server to
     * @param publicNetworkName the name of the public network used to allocate a floating IP for the server
     * @param keypairName the name of the keypair to enable SSH access to the server; may be null
     * @param userDataAsYaml the user data provided as a YAML string; used for server initialization or configuration; may be null
     * @return the floating IP address assigned to the newly created server
     * @throws IllegalArgumentException if a server with the specified name already exists
     * @throws NullPointerException if the specified private network, flavor, or any required resource does not exist
     * @throws IllegalStateException if the server creation fails or the server does not reach the active state
     */
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

    /**
     * Searches for a flavor by its name in the list of available flavors.
     *
     * @param flavorName the name of the flavor to search for
     * @return the flavor object matching the specified name, or null if no match is found
     */
    private Flavor findFlavorByName(String flavorName) {
        List<? extends Flavor> flavors = session.computeService().flavors().list();
        for (Flavor flavor : flavors) {
            if (flavorName.equals(flavor.getName())) {
                return flavor;
            }
        }
        return null;
    }

    /**
     * Finds and returns a network by its name from the list of available networks.
     *
     * @param networkName the name of the network to search for
     * @return the network with the specified name, or null if no such network is found
     */
    private Network findNetworkByName(String networkName) {
        List<? extends Network> networks = session.networkService().network().list();
        for (Network network : networks) {
            if (networkName.equals(network.getName())) {
                return network;
            }
        }
        return null;
    }

    /**
     * Retrieves the floating IP address associated with the specified server.
     *
     * @param server the server object from which the floating IP is to be retrieved
     * @return the floating IP address as a string if found, otherwise null
     */
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

    /**
     * Mounts a device on a remote host by performing operations such as unmounting, partitioning,
     * formatting, and mounting the specified device. The method supports authentication via a password
     * or an SSH key.
     *
     * @param hostName the hostname or IP address of the remote host
     * @param user the username used to authenticate on the remote host
     * @param credential the password or SSH key used for authentication based on the value of useKey
     * @param useKey a boolean flag indicating whether to use an SSH key for authentication (true)
     *               or a password (false)
     * @param deviceName the full path of the device to be mounted (e.g., /dev/sda)
     * @return true if the device was successfully mounted; false otherwise
     */
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

    /**
     * Disables SELinux on a specified remote host through SSH.
     * This method attempts to set the SELinux mode to permissive by executing
     * the "setenforce 0" command on the remote machine.
     *
     * @param hostName The hostname or IP address of the remote machine where SELinux will be disabled.
     * @param user The SSH user for authentication.
     * @param credential The password or private key to use for authentication, depending on the value of {@code useKey}.
     * @param useKey A flag indicating whether to use a private key for authentication (true) or a password (false).
     * @return {@code true} if the SELinux disabling command executed successfully with a zero exit status; {@code false} otherwise.
     */
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

    /**
     * Constructs a command string to add a new group with the specified name and optional group ID.
     *
     * @param groupName The name of the group to be created. This parameter is mandatory.
     * @param groupId The ID of the group to be created. This parameter is optional and can be null.
     * @return A command string to add the group using the specified parameters.
     */
    private static String buildAddGroupCommand(String groupName, Integer groupId) {
        String command = "groupadd";
        if (groupId != null) {
            command += " -g " + groupId.intValue();
        }
        command += " " + groupName;
        return command;
    }

    /**
     * Builds a command string to add a new user with the specified parameters using the useradd utility.
     *
     * @param userName the username of the new user to be created
     * @param password the password for the new user
     * @param userId the user ID to assign to the new user; if null, no ID will be explicitly assigned
     * @param groupId the group ID to assign to the new user; if null, no group will be explicitly assigned
     * @return a string representing the command to add a new user
     */
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

    /**
     * Builds a cloud configuration string based on the provided user, group, and SSH settings.
     * This method generates configuration commands for user and group modifications, SSH settings,
     * and invokes a helper to assemble the final cloud configuration.
     *
     * @param userName                the name of the user to be configured
     * @param password                the password for the user
     * @param userId                  the unique identifier (ID) for the user
     * @param groupName               the name of the group to be configured
     * @param groupId                 the unique identifier (ID) for the group
     * @param sshPublicKeyContent     the SSH public key content for the user
     * @param permitRootLogin         whether root login via SSH is permitted (true for yes, false for no)
     * @param passwordAuthentication  whether SSH password authentication is allowed (true for yes, false for no)
     * @return the generated cloud configuration string that combines user, group, and SSH settings
     */
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
