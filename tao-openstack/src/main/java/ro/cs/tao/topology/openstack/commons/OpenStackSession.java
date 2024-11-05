package ro.cs.tao.topology.openstack.commons;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.exceptions.CodeGenerationException;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import org.apache.http.Header;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.openstack4j.api.OSClient;
import org.openstack4j.api.compute.ComputeService;
import org.openstack4j.api.image.v2.ImageService;
import org.openstack4j.api.networking.NetworkingService;
import org.openstack4j.api.storage.BlockStorageService;
import org.openstack4j.api.storage.ObjectStorageService;
import org.openstack4j.model.common.Identifier;
import org.openstack4j.model.identity.v3.Service;
import org.openstack4j.model.identity.v3.Token;
import org.openstack4j.openstack.OSFactory;
import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.configuration.ConfigurationProvider;
import ro.cs.tao.topology.TopologyException;
import ro.cs.tao.utils.CloseableHttpResponse;
import ro.cs.tao.utils.HttpMethod;
import ro.cs.tao.utils.NetUtils;
import ro.cs.tao.utils.executors.Executor;
import ro.cs.tao.utils.executors.ExecutorType;
import ro.cs.tao.utils.executors.OutputAccumulator;

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
    private static Token authToken;
    private static String region;
    private static Boolean usesImagesV2;
    //private static final OpenStackSession session = new OpenStackSession();
    private Token instanceToken;
    private String tenantId;

    private OpenStackSession() {
    }

    public String getTenantId() {
        return tenantId;
    }

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

    public static ObjectStorageService objectStorageService() {
        final OSClient.OSClientV3 service = getInstance().getService();
        if (!service.supportsObjectStorage()) {
            throw new TopologyException("The remote provider doesn't support Object Storage API");
        }
        return service.objectStorage();
    }

    public static ObjectStorageService objectStorageService(Map<String, String> parameters) {
        final OSClient.OSClientV3 service = getInstance().getService(parameters);
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
        return service.imagesV2();
    }

    public static OpenStackSession getInstance() {
        return new OpenStackSession();
    }

    public OSClient.OSClientV3 getService() {
        OSClient.OSClientV3 client;

        final ConfigurationProvider cfgProvider = ConfigurationManager.getInstance();
        this.tenantId = cfgProvider.getValue(Constants.OPENSTACK_TENANT_ID, null);
        if (tenantId == null || tenantId.isEmpty()) {
            throw new TopologyException(String.format("Missing configuration key [%s]", Constants.OPENSTACK_TENANT_ID));
        }
        region = cfgProvider.getValue(Constants.OPENSTACK_REGION, null);

        if (authToken != null && authToken.getExpires() != null && authToken.getExpires().before(new Date())) {
            logger.fine("OpenStack system token expired, re-authenticating");
            authToken = null;
        }
        if (authToken == null) {
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
            final String appClient = cfgProvider.getValue(Constants.OPENSTACK_CLIENT, null);
            final String appClientSecret = cfgProvider.getValue(Constants.OPENSTACK_CLIENT_SECRET, null);
            final String userSecret = cfgProvider.getValue(Constants.OPENSTACK_USER_SECRET, null);
            final String tokenUrl = cfgProvider.getValue(Constants.OPENSTACK_TOKEN_URL, null);
            final String identityProvider = cfgProvider.getValue(Constants.OPENSTACK_IDENTITY_PROVIDER, null);
            String token = getToken(appClient, appClientSecret, userSecret, tokenUrl, identityProvider,
                                    authUrl, domain, user, password, tenantId, region);
            client = token == null
                             ? OSFactory.builderV3()
                                        .endpoint(authUrl)
                                        .credentials(user, password, Identifier.byId(domain))
                                        .scopeToProject(Identifier.byId(tenantId))
                                        .authenticate()
                             : OSFactory.builderV3()
                                        .token(token)
                                        .endpoint(authUrl)
                                        .scopeToProject(Identifier.byId(tenantId))
                                        .authenticate();
            if (region != null) {
                client = client.useRegion(region);
            }
            //authToken = getAuthToken(authUrl + "/auth/tokens", token, tenantId, domain);
            //if (authToken != null) {
            if (client != null) {
                authToken = client.getToken();
                // hack to force the retrieval of newest version of a service
                List<? extends Service> catalog = authToken.getCatalog();
                catalog.sort((Comparator<Service>) (o1, o2) -> o2.getType().compareTo(o1.getType()));
            }
        } else {
            client = OSFactory.clientFromToken(authToken);
            if (region != null) {
                client = client.useRegion(region);
            }
        }
        return client;
    }

    public OSClient.OSClientV3 getService(Map<String, String> parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException("OpenStack client not configured");
        }
        // if the service client of the instance is null, it means there is no main (system) OpenStack realm,
        // hence we may receive the details for a private bucket
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
        OSClient.OSClientV3 service;
        if (this.instanceToken != null && this.instanceToken.getExpires().before(new Date())) {
            logger.fine("OpenStack token expired, re-authenticating");
            this.instanceToken = null;
        }
        if (this.instanceToken == null) {
            final String appClient = parameters.getOrDefault(Constants.OPENSTACK_CLIENT, null);
            final String appClientSecret = parameters.getOrDefault(Constants.OPENSTACK_CLIENT_SECRET, null);
            final String userSecret = parameters.getOrDefault(Constants.OPENSTACK_USER_SECRET, null);
            final String tokenUrl = parameters.getOrDefault(Constants.OPENSTACK_TOKEN_URL, null);
            final String identityProvider = parameters.getOrDefault(Constants.OPENSTACK_IDENTITY_PROVIDER, null);
            final String region = parameters.getOrDefault(Constants.OPENSTACK_REGION, null);
            String token = getToken(appClient, appClientSecret, userSecret, tokenUrl, identityProvider,
                                    authUrl, domain, user, password, tenantId, region);
            service = token == null
                     ? OSFactory.builderV3()
                                .endpoint(authUrl)
                                .credentials(user, password, Identifier.byId(domain))
                                .scopeToProject(Identifier.byId(tenantId))
                                .authenticate()
                     : OSFactory.builderV3()
                                .token(token)
                                .endpoint(authUrl)
                                .scopeToProject(Identifier.byId(tenantId))
                                .authenticate();
            if (region != null) {
                service = service.useRegion(region);
            }
            this.instanceToken = service.getToken();
        } else {
            service = OSFactory.clientFromToken(this.instanceToken);
        }
        // hack to force the retrieval of newest version of a service
        service.getToken().getCatalog().sort((Comparator<Service>) (o1, o2) -> o2.getType().compareTo(o1.getType()));
        return service;
    }

    private String getToken(String appClient, String appClientSecret, String userSecret, String tokenUrl, String identityProvider,
                            String authUrl, String domain, String user, String password, String tenantId, String region) {
        String token = null;
        if (appClient != null && appClientSecret != null && tokenUrl != null && identityProvider != null) {
            // We need first to get a token from Keycloak
            try {
                Header header = new BasicHeader("Content-Type", "application/x-www-form-urlencoded");
                List<NameValuePair> params = new ArrayList<>();
                params.add(new BasicNameValuePair("password", password));
                params.add(new BasicNameValuePair("grant_type", "password"));
                params.add(new BasicNameValuePair("username", user));
                params.add(new BasicNameValuePair("client_id", appClient));
                params.add(new BasicNameValuePair("client_secret", appClientSecret));
                if (userSecret != null) {
                    params.add(new BasicNameValuePair("totp", generateOTP(userSecret)));
                }
                try (CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.POST, tokenUrl, header, params)) {
                    token = EntityUtils.toString(response.getEntity());
                    Map<String, String> map = new LinkedHashMap<>();
                    map.putAll(new ObjectMapper().readValue(token, map.getClass()));
                    token = map.get("access_token");
                    token = getOSToken(authUrl, user, region, tenantId, domain, appClient, appClientSecret, identityProvider, token);
                }
            } catch (Exception e) {
                logger.severe("Cannot obtain token for OpenStack pre-authentication");
            }
        }
        return token;
    }

    private String generateOTP(String secret) throws CodeGenerationException {
        TimeProvider timeProvider = new SystemTimeProvider();
        CodeGenerator codeGenerator = new DefaultCodeGenerator();
        long counter = timeProvider.getTime() / 30;
        return codeGenerator.generate(secret, counter);
    }

    private String getOSToken(String authUrl, String userName, String region,
                              String projectId, String domainId, String clientId,
                              String clientSecret, String identityProvider,
                              String jwtToken) throws Exception {
        final Map<String, String> env = new HashMap<>();
        try {
            env.put("OS_AUTH_URL", authUrl);
            env.put("OS_INTERFACE", "public");
            env.put("OS_IDENTITY_API_VERSION", "3");
            env.put("OS_USERNAME", userName);
            env.put("OS_REGION_NAME", region);
            env.put("OS_PROJECT_ID", projectId);
            env.put("OS_PROJECT_DOMAIN_ID", domainId);
            env.put("OS_CLIENT_ID", clientId);
            env.put("OS_CLIENT_SECRET", clientSecret);
            env.put("OS_PROTOCOL", "openid");
            env.put("OS_IDENTITY_PROVIDER", identityProvider);
            env.put("OS_AUTH_TYPE", "v3oidcaccesstoken");
            env.put("OS_ACCESS_TOKEN", jwtToken);
            final List<String> args = new ArrayList<>();
            args.add("openstack");
            args.add("token");
            args.add("issue");
            args.add("-f");
            args.add("value");
            args.add("-c");
            args.add("id");
            Executor.setEnvironment(env);
            final Executor<?> executor = Executor.create(ExecutorType.PROCESS, "localhost", args);
            final OutputAccumulator consumer = new OutputAccumulator();
            executor.setOutputConsumer(consumer);
            final int code = executor.execute(false);
            if (code == 0) {
                return consumer.getOutput();
            } else {
                throw new TopologyException(consumer.getOutput());
            }
        } finally {
            final Set<String> keySet = env.keySet();
            for (String key : keySet) {
                Executor.getEnvironment().remove(key);
            }
        }
    }
}
