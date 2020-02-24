package ro.cs.tao.sen2agri;

import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.configuration.ConfigurationProvider;
import ro.cs.tao.execution.model.ExecutionTask;
import ro.cs.tao.execution.model.ExternalTaskListener;

import java.io.IOException;
import java.net.Socket;
import java.util.logging.Logger;

public class Sen2AgriTaskNotifier implements ExternalTaskListener {
    private final String hostName;
    private final int port;
    private final boolean enabled;
    private final Logger logger;

    public Sen2AgriTaskNotifier() {
        final ConfigurationProvider cfgManager = ConfigurationManager.getInstance();
        hostName = cfgManager.getValue(Constants.HOSTNAME);
        port = Integer.parseInt(cfgManager.getValue(Constants.PORT, "8088"));
        enabled = hostName != null;
        logger = Logger.getLogger(Sen2AgriTaskNotifier.class.getName());
        if (!enabled) {
            logger.severe("Sen2Agri configuration not found");
        }
    }

    @Override
    public void onStarted(ExecutionTask task) {
        if (enabled) {
            try (Socket socket = createSocket()) {

            } catch (IOException e) {
                logger.severe(e.getMessage());
            }
        }
    }

    @Override
    public void onUpdated(ExecutionTask task) {
        if (enabled) {
            try (Socket socket = createSocket()) {

            } catch (IOException e) {
                logger.severe(e.getMessage());
            }
        }
    }

    @Override
    public void onCompleted(ExecutionTask task, String processOutput) {
        if (enabled) {
            try (Socket socket = createSocket()) {

            } catch (IOException e) {
                logger.severe(e.getMessage());
            }
        }
    }

    @Override
    public void onError(ExecutionTask task, String reason, int errorCode, String processOutput) {
        if (enabled) {
            try (Socket socket = createSocket()) {

            } catch (IOException e) {
                logger.severe(e.getMessage());
            }
        }
    }

    private Socket createSocket() throws IOException {
        return new Socket(hostName, port);
    }
}
