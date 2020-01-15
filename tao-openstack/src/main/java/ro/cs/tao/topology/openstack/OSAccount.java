package ro.cs.tao.topology.openstack;

import org.openstack4j.api.OSClient;
import org.openstack4j.model.common.Identifier;
import org.openstack4j.openstack.OSFactory;
import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.topology.TopologyException;

import java.util.logging.Logger;

/**
 * Singleton container class for OpenStack authentication information.
 *
 * @author Cosmin Cara
 */
public class OSAccount {
    private static OSAccount instance;
    private static final Logger logger = Logger.getLogger(OSAccount.class.getName());

    private final String domain;
    private final String user;
    private final String password;
    private final String authenticationURL;
    private final String tenantId;


    public static synchronized OSAccount getInstance() {
        if (instance == null) {
            final ConfigurationManager configurationManager = ConfigurationManager.getInstance();
            final String domain = configurationManager.getValue(Constants.OPENSTACK_DOMAIN, null);
            if (domain == null || domain.isEmpty()) {
                throw new TopologyException(String.format("Missing configuration key [%s]", Constants.OPENSTACK_DOMAIN));
            }
            final String user = configurationManager.getValue(Constants.OPENSTACK_USER, null);
            if (user == null || user.isEmpty()) {
                throw new TopologyException(String.format("Missing configuration key [%s]", Constants.OPENSTACK_USER));
            }
            final String password = configurationManager.getValue(Constants.OPENSTACK_PASSWORD, null);
            if (password == null || password.isEmpty()) {
                throw new TopologyException(String.format("Missing configuration key [%s]", Constants.OPENSTACK_PASSWORD));
            }
            final String authUrl = configurationManager.getValue(Constants.OPENSTACK_AUTH_URL, null);
            if (authUrl == null || authUrl.isEmpty()) {
                throw new TopologyException(String.format("Missing configuration key [%s]", Constants.OPENSTACK_AUTH_URL));
            }
            final String tenantId = configurationManager.getValue(Constants.OPENSTACK_TENANT_ID, null);
            if (tenantId == null || tenantId.isEmpty()) {
                throw new TopologyException(String.format("Missing configuration key [%s]", Constants.OPENSTACK_TENANT_ID));
            }
            OSAccount.instance = new OSAccount(domain, user, password, authUrl, tenantId);
        }
        return instance;
    }

    private OSAccount(String domain, String user, String password, String authenticationURL, String tenantId) {
        this.domain = domain;
        this.user = user;
        this.password = password;
        this.authenticationURL = authenticationURL;
        this.tenantId = tenantId;
    }

    public OSClient.OSClientV3 getService() {
        return OSFactory.builderV3()
                        .endpoint(authenticationURL)
                        .credentials(user, password, Identifier.byName(domain))
                        .scopeToProject(Identifier.byId(tenantId))
                        .authenticate();
    }
}
