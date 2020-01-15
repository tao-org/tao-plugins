package ro.cs.tao.topology.openstack;

import org.openstack4j.api.Builders;
import org.openstack4j.api.compute.ComputeService;
import org.openstack4j.api.compute.ServerService;
import org.openstack4j.api.storage.BlockStorageService;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.compute.*;
import org.openstack4j.model.compute.builder.ServerCreateBuilder;
import org.openstack4j.model.storage.block.Volume;
import ro.cs.tao.topology.NodeDescription;
import ro.cs.tao.topology.NodeFlavor;
import ro.cs.tao.topology.TopologyException;
import ro.cs.tao.topology.provider.DefaultNodeProvider;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * OpenStack topology node provider implementation.
 *
 * @author Cosmin Cara
 */
public class OpenStackNodeProvider extends DefaultNodeProvider {
    private OSAccount openStackAccount;

    @Override
    public void authenticate() {
        if (this.openStackAccount == null) {
            try {
                this.openStackAccount = OSAccount.getInstance();
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
            final ComputeService novaService = this.openStackAccount.getService().compute();
            final BlockStorageService blockStorageService = this.openStackAccount.getService().blockStorage();
            final ServerService serverService = novaService.servers();
            final ServerCreateBuilder builder = serverService.serverBuilder();
            final Flavor flavor = novaService.flavors().get(node.getFlavor().getId());
            final Volume ssdVolume = blockStorageService.volumes().create(Builders.volume()
                                                                               .name("ssd")
                                                                               .volumeType("ssd")
                                                                               .size(250)
                                                                               .build());
            final Volume hddVolume = blockStorageService.volumes().create(Builders.volume()
                                                                                .name("hdd")
                                                                                .volumeType("sata")
                                                                                .size(node.getFlavor().getDisk())
                                                                                .build());
            final BlockDeviceMappingCreate sddCreate = Builders.blockDeviceMapping().uuid(ssdVolume.getId()).deviceName("/dev/hda").bootIndex(0).build();
            final BlockDeviceMappingCreate hddCreate = Builders.blockDeviceMapping().uuid(hddVolume.getId()).deviceName("/dev/hdb").bootIndex(1).build();
            final ServerCreate centos7 = builder.name(node.getId())
                                                .flavor(flavor)
                                                .image("centos7")
                                                .blockDevice(sddCreate)
                                                .blockDevice(hddCreate)
                                                .addAdminPass(node.getUserPass())
                                                .build();
            final Server server = serverService.boot(centos7);
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

    private NodeFlavor convert(Flavor flavor) {
        return flavor != null ?
                new NodeFlavor(flavor.getId(), flavor.getVcpus(), flavor.getRam(),
                               flavor.getDisk(), flavor.getSwap(), flavor.getRxtxFactor()) : null;
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
