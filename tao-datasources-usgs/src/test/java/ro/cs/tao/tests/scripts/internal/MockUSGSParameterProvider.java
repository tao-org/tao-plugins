package ro.cs.tao.tests.scripts.internal;

import ro.cs.tao.datasource.ProductFetchStrategy;

import java.util.Map;

/**
 * @author Adrian Draghici
 */
abstract class MockUSGSParameterProvider extends MockDatasetSearchParameterProvider {
    MockUSGSParameterProvider(String serviceURL) {
        super(serviceURL);
    }

    @Override
    public abstract String authenticate();

    @Override
    public Map<String, ProductFetchStrategy> getRegisteredProductFetchStrategies() {
        return null;
    }
}