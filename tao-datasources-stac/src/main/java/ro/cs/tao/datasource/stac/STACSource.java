package ro.cs.tao.datasource.stac;

import ro.cs.tao.component.WebServiceAuthentication;
import ro.cs.tao.component.enums.AuthenticationType;
import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.configuration.ConfigurationProvider;
import ro.cs.tao.datasource.AbstractDataSource;
import ro.cs.tao.datasource.QueryException;
import ro.cs.tao.datasource.param.DataSourceParameter;
import ro.cs.tao.datasource.param.ParameterProvider;
import ro.cs.tao.datasource.persistence.DataSourceConfigurationProvider;
import ro.cs.tao.datasource.stac.parameters.STACParameterProvider;
import ro.cs.tao.stac.core.STACClient;

import java.io.IOException;
import java.sql.*;
import java.util.Map;
import java.util.logging.Logger;

public class STACSource extends AbstractDataSource<STACQuery, STACClient> {
    public static final String NAME = "STAC Data Source";
    private static final String dbConnectionString;
    private static final String dbUser;
    private static final String dbPass;
    private static DataSourceConfigurationProvider configurationProvider;
    protected WebServiceAuthentication authentication;
    private final Logger logger = Logger.getLogger(STACSource.class.getName());

    static {
        final ConfigurationProvider configurationProvider = ConfigurationManager.getInstance();
        dbConnectionString = configurationProvider.getValue("spring.datasource.url");
        dbUser = configurationProvider.getValue("spring.datasource.username");
        dbPass = configurationProvider.getValue("spring.datasource.password");
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            Logger.getLogger(STACSource.class.getName()).severe("PostgreSQL driver not registered");
        }
    }

    /*public STACSource() {
        super();
        setParameterProvider(new STACParameterProvider(this));
    }*/

    public STACSource(String provider) {
        super(provider);
        setId(provider);
        readInfo();
        setParameterProvider(new STACParameterProvider(this));
    }

    @Override
    public String defaultId() {
        return NAME;
    }

    @Override
    public STACSource clone() throws CloneNotSupportedException {
        return new STACSource(this.id);
    }

    @Override
    public boolean requiresAuthentication() { return authentication != null && authentication.getType() != AuthenticationType.NONE; }

    public WebServiceAuthentication getAuthentication() {
        return authentication;
    }

    public static void setConfigurationProvider(DataSourceConfigurationProvider provider) {
        configurationProvider = provider;
    }

    public DataSourceConfigurationProvider getConfigurationProvider() {
        return configurationProvider;
    }

    @Override
    protected STACQuery createQueryImpl(String collection) {
        final ParameterProvider parameterProvider = getParameterProvider();
        if (parameterProvider == null) {
            throw new QueryException("No parameter provider found for this data source");
        }
        final Map<String, Map<String, DataSourceParameter>> supportedParameters =
                parameterProvider.getSupportedParameters();
        if (supportedParameters == null || !supportedParameters.containsKey(collection)) {
            throw new QueryException(String.format("Parameters not defined for this data source and collection %s",
                                                   collection));
        }
        return new STACQuery(this, collection);
    }

    @Override
    public STACClient authenticate() throws IOException {
        return new STACClient(this.connectionString, this.authentication);
    }

    @Override
    public boolean ping() {
        return false;
    }

    @Override
    public void close() {
        // NO-OP
    }

    public Connection getConnection() {
        Connection connection = null;
        try {
            connection = DriverManager.getConnection(dbConnectionString, dbUser, dbPass);
        } catch (SQLException e) {
            this.logger.warning(e.getMessage());
        }
        return connection;
    }

    protected void readInfo() {
        try (Connection sqlConnection = getConnection();
             PreparedStatement cStmt = sqlConnection.prepareStatement("SELECT id, application_path FROM component.container WHERE name = ?");
             PreparedStatement aStmt = sqlConnection.prepareStatement("SELECT id, auth_type, username, password, login_url, header_name FROM component.wps_authentication WHERE id = ?")) {
            cStmt.setString(1, this.id);
            String containerId = null;
            try (ResultSet rs = cStmt.executeQuery()) {
                if (rs.next()) {
                    containerId = rs.getString(1);
                    this.connectionString = rs.getString(2);
                } else {
                    throw new SQLException("Not found");
                }
            }
            aStmt.setString(1, containerId);
            try (ResultSet rs = aStmt.executeQuery()) {
                if (rs.next()) {
                    this.authentication = new WebServiceAuthentication();
                    this.authentication.setId(rs.getString(1));
                    this.authentication.setType(AuthenticationType.valueOf(rs.getString(2)));
                    this.authentication.setUser(rs.getString(3));
                    this.authentication.setPassword(rs.getString(4));
                    this.authentication.setLoginUrl(rs.getString(5));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
