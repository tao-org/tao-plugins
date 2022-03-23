package ro.cs.tao.topology.openstack.commons;

import org.openstack4j.api.OSClient;
import org.openstack4j.api.compute.ComputeService;
import org.openstack4j.api.image.ImageService;
import org.openstack4j.api.networking.NetworkingService;
import org.openstack4j.api.storage.BlockStorageService;
import org.openstack4j.api.storage.ObjectStorageService;
import org.openstack4j.model.common.Identifier;
import org.openstack4j.model.identity.v3.Service;
import org.openstack4j.model.identity.v3.Token;
import org.openstack4j.openstack.OSFactory;
import ro.cs.tao.topology.TopologyException;

import java.util.*;
import java.util.logging.Logger;

/**
 * Singleton container class for OpenStack authentication information.
 *
 * @author Cosmin Cara
 */
public class OpenStackSession {
    //private static OpenStackSession instance;
    private static final Logger logger = Logger.getLogger(OpenStackSession.class.getName());
    private static final Map<Integer, OpenStackSession> sessions = Collections.synchronizedMap(new HashMap<>());
    private final String domain;
    private final String user;
    private final String password;
    private final String authenticationURL;
    private final String tenantId;
    private Token token;

    public ComputeService computeService() {
        final OSClient.OSClientV3 service = getService();
        if (!service.supportsCompute()) {
            throw new TopologyException("The remote provider doesn't support Compute API");
        }
        return service.compute();
    }

    public BlockStorageService blockStorageService() {
        final OSClient.OSClientV3 service = getService();
        if (!service.supportsBlockStorage()) {
            throw new TopologyException("The remote provider doesn't support Block Storage API");
        }
        return service.blockStorage();
    }

    public static ObjectStorageService objectStorageService(Map<String, String> parameters) {
        final OSClient.OSClientV3 service = getInstance(parameters).getService();
        if (!service.supportsObjectStorage()) {
            throw new TopologyException("The remote provider doesn't support Object Storage API");
        }
        return service.objectStorage();
    }

    public NetworkingService networkService() {
        final OSClient.OSClientV3 service = getService();
        if (!service.supportsNetwork()) {
            throw new TopologyException("The remote provider doesn't support Network API");
        }
        return service.networking();
    }

    public ImageService imageService() {
        final OSClient.OSClientV3 service = getService();
        if (!service.supportsImage()) {
            throw new TopologyException("The remote provider doesn't support Image API");
        }
        return service.images();
    }

    public static OpenStackSession getInstance(Map<String, String> parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException("OpenStack client not configured");
        }
        final String domain = parameters.get(Constants.OPENSTACK_DOMAIN);
        if (domain == null || domain.isEmpty()) {
            throw new TopologyException(String.format("Missing configuration key [%s]", Constants.OPENSTACK_DOMAIN));
        }
        final String user = parameters.get(Constants.OPENSTACK_USER);
        if (user == null || user.isEmpty()) {
            throw new TopologyException(String.format("Missing configuration key [%s]", Constants.OPENSTACK_USER));
        }
        final String password = parameters.get(Constants.OPENSTACK_PASSWORD);
        if (password == null || password.isEmpty()) {
            throw new TopologyException(String.format("Missing configuration key [%s]", Constants.OPENSTACK_PASSWORD));
        }
        final String authUrl = parameters.get(Constants.OPENSTACK_AUTH_URL);
        if (authUrl == null || authUrl.isEmpty()) {
            throw new TopologyException(String.format("Missing configuration key [%s]", Constants.OPENSTACK_AUTH_URL));
        }
        final String tenantId = parameters.get(Constants.OPENSTACK_TENANT_ID);
        if (tenantId == null || tenantId.isEmpty()) {
            throw new TopologyException(String.format("Missing configuration key [%s]", Constants.OPENSTACK_TENANT_ID));
        }
        final int hash = Objects.hash(domain, user, password, tenantId);
        if (!sessions.containsKey(hash)) {
            sessions.put(hash, new OpenStackSession(domain, user, password, authUrl, tenantId));
        }
        return sessions.get(hash);
    }

    private OpenStackSession(String domain, String user, String password, String authenticationURL, String tenantId) {
        this.domain = domain;
        this.user = user;
        this.password = password;
        this.authenticationURL = authenticationURL;
        this.tenantId = tenantId;
    }

    public OSClient.OSClientV3 getService() {
        OSClient.OSClientV3 service;
        if (this.token == null) {
            service = OSFactory.builderV3()
                                .endpoint(authenticationURL)
                                .credentials(user, password, Identifier.byName(domain))
                                .scopeToProject(Identifier.byId(tenantId))
                                .authenticate();
            this.token = service.getToken();
        } else {
            service = OSFactory.clientFromToken(this.token);
        }
        // hack to force the retrieval of newest version of a service
        service.getToken().getCatalog().sort((Comparator<Service>) (o1, o2) -> o2.getType().compareTo(o1.getType()));
        return service;
    }
}
