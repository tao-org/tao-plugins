package ro.cs.tao.topology.openstack;

import org.openstack4j.api.Builders;
import org.openstack4j.api.OSClient;
import org.openstack4j.api.compute.ComputeService;
import org.openstack4j.api.compute.ServerService;
import org.openstack4j.api.storage.BlockVolumeService;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.compute.*;
import org.openstack4j.model.compute.builder.ServerCreateBuilder;
import org.openstack4j.model.image.Image;
import org.openstack4j.model.network.Network;
import org.openstack4j.model.storage.block.Volume;
import org.openstack4j.model.storage.block.VolumeAttachment;
import org.openstack4j.model.storage.block.VolumeType;
import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.configuration.ConfigurationProvider;
import ro.cs.tao.topology.NodeDescription;
import ro.cs.tao.topology.NodeFlavor;
import ro.cs.tao.topology.NodeProvider;
import ro.cs.tao.topology.TopologyException;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * OpenStack topology node provider implementation.
 *
 * @author Cosmin Cara
 */
public class OpenStackNodeProvider implements NodeProvider {
    private final Logger logger = Logger.getLogger(OpenStackNodeProvider.class.getName());
    private OSAccount openStackAccount;
    private Server master;

    @Override
    public void authenticate() {
        if (this.openStackAccount == null) {
            try {
                this.openStackAccount = OSAccount.getInstance();
                final OSClient.OSClientV3 service = this.openStackAccount.getService();
                this.master = service.compute().servers().get(InetAddress.getLocalHost().getHostName());
            } catch (Exception ex) {
                throw new TopologyException(String.format("OpenStack authentication failed. Reason: %s", ex.getMessage()));
            }
        }
    }

    @Override
    public List<NodeFlavor> listFlavors() throws TopologyException {
        authenticate();
        final List<? extends Flavor> remoteFlavors = this.openStackAccount.getService().compute().flavors().list();
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
        authenticate();
        final List<? extends Server> servers = this.openStackAccount.getService().compute().servers().list();
        //final Set<NodeDescription> registeredNodes = new HashSet<>(super.listNodes());
        List<NodeDescription> remoteNodes = new ArrayList<>();
        if (servers != null) {
            for (Server server : servers) {
                remoteNodes.add(convert(server));
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
        final Server server = this.openStackAccount.getService().compute().servers().get(nodeName);
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
        final OSClient.OSClientV3 session = this.openStackAccount.getService();
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
            final ComputeService novaService = session.compute();
            final ServerService serverService = novaService.servers();
            final ServerCreateBuilder builder = serverService.serverBuilder();
            final Flavor flavor = novaService.flavors().list().stream().filter(f -> f.getName().equals(node.getFlavor().getId())).findFirst().orElse(null);
            final List<String> networkIds = getNetworks();
            final String ssdVolumeName = node.getId() + "-ssd";
            final String hddVolumeName = node.getId() + "-hdd";
            final int ssdSize = Integer.parseInt(cfgProvider.getValue(Constants.OPENSTACK_VOLUME_SSD_SIZE, "240"));
            final int hddSize = Integer.parseInt(cfgProvider.getValue(Constants.OPENSTACK_VOLUME_HDD_SIZE, "0"));
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
            server = serverService.boot(newServer);
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
        final ComputeService novaService = this.openStackAccount.getService().compute();
        Server server = novaService.servers().get(nodeName);
        if (server == null) {
            logger.warning(String.format("Node %s doesn't exist on %s", nodeName, getClass().getSimpleName()));
        } else {
            final List<Address> IPtoDelete = new ArrayList<>();
            for (List<? extends Address> addresses : server.getAddresses().getAddresses().values()) {
                addresses.stream().filter(a -> a.getType().equalsIgnoreCase("floating")).findFirst().ifPresent(IPtoDelete::add);
            }
            novaService.servers().delete(nodeName);
            final Map<String, ? extends FloatingIP> ipMap = novaService.floatingIps().list().stream().collect(Collectors.toMap(FloatingIP::getFixedIpAddress, Function.identity()));
            for (Address address : IPtoDelete) {
                novaService.floatingIps().deallocateIP(ipMap.get(address.getAddr()).getId());
            }
        }
    }

    @Override
    public void suspend(String nodeName) throws TopologyException {
        authenticate();
        final ServerService serverService = this.openStackAccount.getService().compute().servers();
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
        final ServerService serverService = this.openStackAccount.getService().compute().servers();
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

    /*private FloatingIP getAvailableFloatingIp() {
        FloatingIP floatingIP = null;
        final ComputeFloatingIPService floatingIPService = this.openStackAccount.getService().compute().floatingIps();
        final List<? extends FloatingIP> floatingIPS = floatingIPService.list();
        for (FloatingIP ip : floatingIPS) {
            if (ip.getInstanceId() == null) { // no instance is attached to the IP, hence it's free to use
                floatingIP = ip;
                break;
            }
        }
        if (floatingIP == null) { // all floating IPs are attached, create a new one
            floatingIP = floatingIPService.allocateIP(ConfigurationManager.getInstance().getValue(Constants.OPENSTACK_FLOATING_IP_POOL));
        }
        return floatingIP;
    }*/

    private Volume createVolume(String volumeName, String volumeType, int sizeGB) {
        final OSClient.OSClientV3 session = this.openStackAccount.getService();
        if (!session.supportsBlockStorage()) {
            throw new TopologyException("The remote provider doesn't support BlockStorage API");
        }
        final BlockVolumeService volumeService = session.blockStorage().volumes();
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
        final OSClient.OSClientV3 session = this.openStackAccount.getService();
        if (!session.supportsBlockStorage()) {
            throw new TopologyException("The remote provider doesn't support BlockStorage API");
        }
        if (volume != null) {
            final BlockVolumeService volumeService = session.blockStorage().volumes();
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
        final OSClient.OSClientV3 session = this.openStackAccount.getService();
        if (!session.supportsCompute()) {
            throw new TopologyException("The remote provider doesn't support Compute API");
        }
        if (server != null) {
            if (session.compute().servers().get(server.getId()) != null) { // make sure it exists
                final ActionResponse response = session.compute().servers().delete(server.getId());
                if (!response.isSuccess()) {
                    logger.severe(String.format("Server '%s' could not be deleted. Reason: %s",
                                                server.getName(), response.getFault()));
                }
            }
        }
    }

    private List<String> getNetworks() {
        final OSClient.OSClientV3 session = this.openStackAccount.getService();
        if (!session.supportsNetwork()) {
            throw new TopologyException("The remote provider doesn't support Network API");
        }
        final List<String> networkIds = this.master != null ?
                new ArrayList<>(this.master.getAddresses().getAddresses().keySet()) : new ArrayList<>();
        if (networkIds.size() == 0) {
            final ConfigurationProvider cfgProvider = ConfigurationManager.getInstance();
            final String privateNetworkName = cfgProvider.getValue(Constants.OPENSTACK_PRIVATE_NETWORK);
            final String eoDataNetworkName = cfgProvider.getValue(Constants.OPENSTACK_DATA_NETWORK);
            if (privateNetworkName != null || eoDataNetworkName != null) {
                for (Network network : session.networking().network().list()) {
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
        final OSClient.OSClientV3 session = this.openStackAccount.getService();
        if (!session.supportsBlockStorage()) {
            throw new TopologyException("The remote provider doesn't support BlockStorage API");
        }
        final List<? extends VolumeType> volumeTypes = session.blockStorage().volumes().listVolumeTypes();
        if (volumeType == null) {
            throw new TopologyException("The remote provider API returned an empty VolumeType list");
        }
        if (volumeTypes.stream().noneMatch(vt -> vt.getName().equals(volumeType))) {
            throw new TopologyException("The VolumeType '%s' doesn't exist", volumeType);
        }
    }

    private String getImageId(String imageName) {
        final OSClient.OSClientV3 session = this.openStackAccount.getService();
        if (!session.supportsImage()) {
            throw new TopologyException("Remote provider doesn't support Image API");
        }
        String imageId = null;
        for (Image image : session.images().list()) {
            if (imageName.equals(image.getName())) {
                imageId = image.getId();
                break;
            }
        }
        if (imageId == null) {
            throw new TopologyException("Image '%s' doesn't exist", imageName);
        }
        return imageId;
    }

    private NodeFlavor convert(Flavor flavor) {
        return flavor != null ?
                new NodeFlavor(flavor.getName(), flavor.getVcpus(), flavor.getRam(),
                               flavor.getDisk(), flavor.getSwap() == 0 ? flavor.getRam() / 1024 * 2 : flavor.getSwap(),
                               flavor.getRxtxFactor()) : null;
    }

    private NodeDescription convert(Server server) {
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
}
