package ro.cs.tao.datasource.stac;

import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.configuration.ConfigurationProvider;
import ro.cs.tao.datasource.AbstractDataSource;
import ro.cs.tao.datasource.QueryException;
import ro.cs.tao.datasource.param.DataSourceParameter;
import ro.cs.tao.datasource.param.ParameterProvider;
import ro.cs.tao.datasource.stac.parameters.STACParameterProvider;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.logging.Logger;

public class STACSource  extends AbstractDataSource<STACQuery, Void> {
    private final String dbUser;
    private final String dbPass;
    private final Logger logger;

    public STACSource() {
        super();
        final ConfigurationProvider configurationProvider = ConfigurationManager.getInstance();
        this.connectionString = configurationProvider.getValue("spring.datasource.url");
        this.dbUser = configurationProvider.getValue("spring.datasource.username");
        this.dbPass = configurationProvider.getValue("spring.datasource.password");
        this.logger = Logger.getLogger(STACSource.class.getName());
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            this.logger.severe("PostgreSQL driver not registered");
        }
        setParameterProvider(new STACParameterProvider(this));
    }

    @Override
    public String defaultId() {
        return "STAC Service";
    }

    @Override
    public STACSource clone() throws CloneNotSupportedException {
        return (STACSource) super.clone();
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
    public Void authenticate() throws IOException {
        return null;
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
            connection = DriverManager.getConnection(this.connectionString, this.dbUser, this.dbPass);
        } catch (SQLException e) {
            this.logger.warning(e.getMessage());
        }
        return connection;
    }
}
