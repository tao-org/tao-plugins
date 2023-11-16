package ro.cs.tao.services;

import org.apache.http.util.EntityUtils;
import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.configuration.ConfigurationProvider;
import ro.cs.tao.services.interfaces.LogoutListener;
import ro.cs.tao.user.SessionDuration;
import ro.cs.tao.utils.CloseableHttpResponse;
import ro.cs.tao.utils.HttpMethod;
import ro.cs.tao.utils.NetUtils;

import java.io.IOException;
import java.util.logging.Logger;

public class PostLogoutHandler implements LogoutListener {
    private final static String templateJson = "{\"user_id\":\"%s\",\"usage_secs\":%d}";
    private final Logger logger = Logger.getLogger(PostLogoutHandler.class.getName());

    @Override
    public void doAction(String userId, String token, SessionDuration sessionDuration, int processingTime) {
        final ConfigurationProvider cfgProvider = ConfigurationManager.getInstance();
        final int includeSessionTime = cfgProvider.getBooleanValue("quota.include.session.time") ? 1 : 0;
        final String serviceAPIUrl = cfgProvider.getValue("dunia.service.api.url", "https://dunia.esa.int/api/quota/tao");
        final String serviceAPIHeader = cfgProvider.getValue("dunia.service.api.key");
        final String header;
        if (serviceAPIHeader != null) {
            final String[] items = serviceAPIHeader.split(":");
            header = items[0];
            token = items[1];
        } else {
            header = "X-Auth-Token";
        }
        final String json = String.format(templateJson, userId, includeSessionTime * sessionDuration.getDuration() + processingTime);
        try (CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.POST, serviceAPIUrl, header, token, json)) {
            if (response.getStatusLine().getStatusCode() > 201) {
                throw new IOException(EntityUtils.toString(response.getEntity()));
            }
        } catch (Exception e) {
            logger.severe(e.getMessage());
        }
    }
}
