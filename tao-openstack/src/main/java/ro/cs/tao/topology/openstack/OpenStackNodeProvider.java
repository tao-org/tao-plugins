package ro.cs.tao.topology.openstack;

import org.openstack4j.api.Builders;
import org.openstack4j.api.OSClient;
import org.openstack4j.api.compute.ComputeFloatingIPService;
import org.openstack4j.api.compute.ComputeService;
import org.openstack4j.api.compute.ServerService;
import org.openstack4j.api.storage.BlockStorageService;
import org.openstack4j.api.storage.BlockVolumeService;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.compute.*;
import org.openstack4j.model.compute.builder.ServerCreateBuilder;
import org.openstack4j.model.network.Network;
import org.openstack4j.model.storage.block.Volume;
import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.topology.NodeDescription;
import ro.cs.tao.topology.NodeFlavor;
import ro.cs.tao.topology.TopologyException;
import ro.cs.tao.topology.provider.DefaultNodeProvider;

import java.net.InetAddress;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * OpenStack topology node provider implementation.
 *
 * @author Cosmin Cara
 */
public class OpenStackNodeProvider extends DefaultNodeProvider {
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
        final Set<NodeDescription> registeredNodes = new HashSet<>(super.listNodes());
        List<NodeDescription> remoteNodes = new ArrayList<>();
        if (servers != null) {
            for (Server server : servers) {
                final NodeDescription node = convert(server);
                remoteNodes.add(node);
                if (!registeredNodes.contains(node)) {
                    super.addNode(node);
                } else {
                    registeredNodes.remove(node);
                }
            }
            if (registeredNodes.size() > 0) {
                for (NodeDescription node : registeredNodes) {
                    super.removeNode(node);
                }
            }
        }
        return remoteNodes;
    }

    @Override
    public NodeDescription getNode(String nodeName) throws TopologyException {
        authenticate();
        if ("localhost".equals(nodeName)) {
            return super.getNode(nodeName);
        }
        final Server server = this.openStackAccount.getService().compute().servers().get(nodeName);
        NodeDescription node = super.getNode(nodeName);
        if (node == null && server == null) {
            logger.warning(String.format("Node '%s' doesn't exist", nodeName));
        } else if (node == null) {
            logger.info(String.format("Node '%s' found in OpenStack topology, but not registered locally. Will register it.",
                                      nodeName));
            node = super.addNode(convert(server));
        } else if (server == null) {
            logger.warning(String.format("Node '%s' is registered locally, but not found in OpenStack topology. Will remove it.",
                                         nodeName));
            super.removeNode(node);
            node = null;
        } else {
            NodeDescription remoteNode = convert(server);
            final NodeFlavor flavor = remoteNode.getFlavor();
            if (!node.getFlavor().equals(flavor)) {
                logger.warning(String.format("Node '%s' exists both locally and in OpenStack topology, but with different flavor. Will update it.",
                                             nodeName));
                node.setFlavor(flavor);
                node = super.updateNode(node);
            }
        }
        return node;
    }

    @Override
    public NodeDescription addNode(NodeDescription node) throws TopologyException {
        try {
            authenticate();
            final ConfigurationManager cfgManager = ConfigurationManager.getInstance();
            final String ssdType = cfgManager.getValue(Constants.OPENSTACK_VOLUME_TYPE_SSD, "SSD");
            final String hddType = cfgManager.getValue(Constants.OPENSTACK_VOLUME_TYPE_HDD, "HDD");
            final String defaultSecGroup = cfgManager.getValue(Constants.OPENSTACK_DEFAULT_SECURITY_GROUP);
            final OSClient.OSClientV3 session = this.openStackAccount.getService();
            final ComputeService novaService = session.compute();
            final BlockStorageService blockStorageService = session.blockStorage();
            final ServerService serverService = novaService.servers();
            final ServerCreateBuilder builder = serverService.serverBuilder();
            final Flavor flavor = novaService.flavors().list().stream().filter(f -> f.getName().equals(node.getFlavor().getId())).findFirst().orElse(null);
            final List<String> networkIds = this.master != null ? new ArrayList<>(this.master.getAddresses().getAddresses().keySet()) : new ArrayList<>();
            final String ssdVolumeName = node.getId() + "-ssd";
            final String hddVolumeName = node.getId() + "-hdd";
            final BlockVolumeService volumeService = blockStorageService.volumes();
            Volume ssdVolume = volumeService.get(ssdVolumeName);
            if (ssdVolume == null || (ssdVolume.getAttachments() != null && ssdVolume.getAttachments().size() > 0)) {
                ssdVolume = volumeService.create(Builders.volume()
                                                         .name(ssdVolumeName + "2")
                                                         .volumeType(ssdType)
                                                         .size(240)
                                                         .build());
            }
            Volume hddVolume = volumeService.get(hddVolumeName);
            if (hddVolume == null || (hddVolume.getAttachments() != null && hddVolume.getAttachments().size() > 0)) {
                hddVolume = volumeService.create(Builders.volume()
                                                         .name(hddVolumeName + "2")
                                                         .volumeType(hddType)
                                                         .size(node.getFlavor().getDisk())
                                                         .build());
            }
            final BlockDeviceMappingCreate sddCreate = Builders.blockDeviceMapping().uuid(ssdVolume.getId()).deviceName("/dev/sdb").bootIndex(0).build();
            final BlockDeviceMappingCreate hddCreate = Builders.blockDeviceMapping().uuid(hddVolume.getId()).deviceName("/dev/sdc").bootIndex(1).build();
            builder.name(node.getId())
                    .flavor(flavor)
                    .image(this.master != null ? this.master.getImageId() : "3c3e0e36-41f0-4180-95ef-f32c7173b845"); //CentOS 7
            if (networkIds.size() == 0) {
                final String privateNetworkName = cfgManager.getValue(Constants.OPENSTACK_PRIVATE_NETWORK);
                final String eoDataNetworkName = cfgManager.getValue(Constants.OPENSTACK_DATA_NETWORK);
                if (privateNetworkName != null || eoDataNetworkName != null) {
                    for (Network network : session.networking().network().list()) {
                        if ((privateNetworkName != null && privateNetworkName.equals(network.getName())) ||
                                (eoDataNetworkName != null && eoDataNetworkName.equals(network.getName()))) {
                            networkIds.add(network.getId());
                        }
                    }
                }
            }
            builder.networks(networkIds);
            final ServerCreate newServer = builder.addSecurityGroup(defaultSecGroup)
                    .blockDevice(sddCreate)
                    .blockDevice(hddCreate)
                    .addAdminPass(node.getUserPass())
                    .build();
            final Server server = serverService.boot(newServer);
            /*final FloatingIP floatingIp = getAvailableFloatingIp();
            if (floatingIp == null) {
                throw new Exception("Cannot allocate IP for " + node.getId());
            }
            ActionResponse response = novaService.floatingIps().addFloatingIP(newServer.getName(), floatingIp.getFloatingIpAddress());
            if (!response.isSuccess()) {
                throw new Exception("Cannot register IP for " + node.getId());
            }*/
            if (server != null) {
                return super.addNode(node);
            } else {
                throw new TopologyException("OpenStack node was not created");
            }
        } catch (Exception ex) {
            throw new TopologyException(String.format("Cannot add the node '%s'. Reason: %s", node.getId(), ex.getMessage()));
        }
    }

    @Override
    public NodeDescription addNode(NodeFlavor flavor, String name, String description, String user, String pwd) throws TopologyException {
        NodeDescription node = new NodeDescription();
        node.setFlavor(flavor);
        node.setId(name);
        node.setDescription(description);
        node.setUserName(user);
        node.setUserPass(pwd);
        return addNode(node);
    }

    @Override
    public void removeNode(String nodeName) throws TopologyException {
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
        super.removeNode(nodeName);
    }

    @Override
    public void suspendNode(String nodeName) throws TopologyException {
        authenticate();
        final ServerService serverService = this.openStackAccount.getService().compute().servers();
        NodeDescription localNode = super.getNode(nodeName);
        if (localNode == null) {
            throw new TopologyException(String.format("Node '%s' not registered", nodeName));
        }
        final Server server = serverService.get(nodeName);
        if (server == null) {
            throw new TopologyException(String.format("Node '%s' is not created", nodeName));
        }
        final ActionResponse response = serverService.action(server.getId(), Action.SUSPEND);
        if (response.isSuccess()) {
            super.suspendNode(nodeName);
        } else {
            throw new TopologyException(String.format("Suspending node '%s' failed. Reason: %s",
                                                      nodeName, response.getFault()));
        }
    }

    @Override
    public void resumeNode(String nodeName) throws TopologyException {
        authenticate();
        final ServerService serverService = this.openStackAccount.getService().compute().servers();
        NodeDescription localNode = super.getNode(nodeName);
        if (localNode == null) {
            throw new TopologyException(String.format("Node '%s' not registered", nodeName));
        }
        final Server server = serverService.get(nodeName);
        if (server == null) {
            throw new TopologyException(String.format("Node '%s' is not created", nodeName));
        }
        final ActionResponse response = serverService.action(server.getId(), Action.RESUME);
        if (response.isSuccess()) {
            super.suspendNode(nodeName);
        } else {
            throw new TopologyException(String.format("Resuming node '%s' failed. Reason: %s",
                                                      nodeName, response.getFault()));
        }
    }

    @Override
    public NodeDescription updateNode(NodeDescription node) throws TopologyException {
        throw new TopologyException("Update operation not implemented");
    }

    private FloatingIP getAvailableFloatingIp() {
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
