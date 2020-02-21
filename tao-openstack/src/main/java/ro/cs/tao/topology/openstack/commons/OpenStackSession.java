package ro.cs.tao.topology.openstack.commons;

import org.openstack4j.api.OSClient;
import org.openstack4j.api.compute.ComputeService;
import org.openstack4j.api.image.ImageService;
import org.openstack4j.api.networking.NetworkingService;
import org.openstack4j.api.storage.BlockStorageService;
import org.openstack4j.api.storage.ObjectStorageService;
import org.openstack4j.model.common.Identifier;
import org.openstack4j.model.identity.v3.Service;
import org.openstack4j.openstack.OSFactory;
import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.configuration.ConfigurationProvider;
import ro.cs.tao.topology.TopologyException;

import java.util.Comparator;
import java.util.logging.Logger;

/**
 * Singleton container class for OpenStack authentication information.
 *
 * @author Cosmin Cara
 */
public class OpenStackSession {
    private static OpenStackSession instance;
    private static final Logger logger = Logger.getLogger(OpenStackSession.class.getName());

    private final String domain;
    private final String user;
    private final String password;
    private final String authenticationURL;
    private final String tenantId;

    public static ComputeService computeService() {
        final OSClient.OSClientV3 service = getInstance().getService();
        if (!service.supportsCompute()) {
            throw new TopologyException("The remote provider doesn't support Compute API");
        }
        return service.compute();
    }

    public static BlockStorageService blockStorageService() {
        final OSClient.OSClientV3 service = getInstance().getService();
        if (!service.supportsBlockStorage()) {
            throw new TopologyException("The remote provider doesn't support Block Storage API");
        }
        return service.blockStorage();
    }

    public static ObjectStorageService objectStorageService() {
        final OSClient.OSClientV3 service = getInstance().getService();
        if (!service.supportsObjectStorage()) {
            throw new TopologyException("The remote provider doesn't support Object Storage API");
        }
        return service.objectStorage();
    }

    public static NetworkingService networkService() {
        final OSClient.OSClientV3 service = getInstance().getService();
        if (!service.supportsNetwork()) {
            throw new TopologyException("The remote provider doesn't support Network API");
        }
        return service.networking();
    }

    public static ImageService imageService() {
        final OSClient.OSClientV3 service = getInstance().getService();
        if (!service.supportsImage()) {
            throw new TopologyException("The remote provider doesn't support Image API");
        }
        return service.images();
    }

    private static synchronized OpenStackSession getInstance() {
        if (instance == null) {
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
            OpenStackSession.instance = new OpenStackSession(domain, user, password, authUrl, tenantId);
        }
        return instance;
    }

    private OpenStackSession(String domain, String user, String password, String authenticationURL, String tenantId) {
        this.domain = domain;
        this.user = user;
        this.password = password;
        this.authenticationURL = authenticationURL;
        this.tenantId = tenantId;
    }

    public OSClient.OSClientV3 getService() {
        OSClient.OSClientV3 service = OSFactory.builderV3()
                                                .endpoint(authenticationURL)
                                                .credentials(user, password, Identifier.byName(domain))
                                                .scopeToProject(Identifier.byId(tenantId))
                                                .authenticate();
        // hack to force the retrieval of newest version of a service
        service.getToken().getCatalog().sort((Comparator<Service>) (o1, o2) -> o2.getType().compareTo(o1.getType()));
        return service;
    }
}
