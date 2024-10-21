package ro.cs.tao.tests.scripts.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.util.EntityUtils;
import ro.cs.tao.datasource.CollectionDescription;
import ro.cs.tao.datasource.QueryException;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.DataSourceParameter;
import ro.cs.tao.datasource.param.JavaType;
import ro.cs.tao.datasource.param.ParameterProvider;
import ro.cs.tao.eodata.enums.SensorType;
import ro.cs.tao.utils.HttpMethod;
import ro.cs.tao.utils.NetUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * @author Adrian Draghici
 */
public abstract class MockDatasetSearchParameterProvider implements ParameterProvider {

    private static final String DATASET_NAME_REGEX = ".+#(.*)";
    private static final Logger LOGGER = Logger.getLogger(MockDatasetSearchParameterProvider.class.getName());
    private String[] datasets;
    private Map<String, Map<String, DataSourceParameter>> datasetsParameters;
    private Map<String, CollectionDescription> datasetsDescriptions;
    private final String serviceURL;
    private String apiKey;

    MockDatasetSearchParameterProvider(String serviceURL) {
        this.serviceURL = serviceURL;
    }

    @Override
    public Map<String, Map<String, DataSourceParameter>> getSupportedParameters() {
        if (this.datasetsParameters == null) {
            initialize();
        }
        return this.datasetsParameters;
    }

    @Override
    public String[] getSupportedSensors() {
        if (this.datasets == null) {
            initialize();
        }
        return this.datasets;
    }

    @Override
    public Map<String, CollectionDescription> getSensorTypes() {
        if (this.datasetsDescriptions == null) {
            initialize();
        }
        return this.datasetsDescriptions;
    }

    private void initialize() {
        if (this.apiKey == null) {
            this.apiKey = authenticate();
        }
        final DatasetSearchResponse datasetSearchResponse;
        try (ro.cs.tao.utils.CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.POST, serviceURL + "dataset-search", "X-Auth-Token", apiKey, "")) {
            switch (response.getStatusLine().getStatusCode()) {
                case 200:
                    final String body = EntityUtils.toString(response.getEntity());
                    if (body.contains("SERVER_ERROR")) {
                        throw new QueryException("The request was not successful. Reason: API Server Error");
                    }
                    datasetSearchResponse = new ObjectMapper().readValue(body, DatasetSearchResponse.class);
                    break;
                case 401:
                    throw new QueryException("The supplied credentials are invalid!");
                default:
                    throw new QueryException(String.format("The request was not successful. Reason: %s",
                            response.getStatusLine().getReasonPhrase()));
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        this.datasetsParameters = extractDatasetsParameters(datasetSearchResponse.getData());
        this.datasets = this.datasetsParameters.keySet().toArray(new String[0]);
        this.datasetsDescriptions = extractDatasetsDescriptions(Arrays.asList(this.datasets), datasetSearchResponse.getData());
        groupDatasetsByCommonParameters(this.datasetsParameters, this.datasetsDescriptions);
        LOGGER.info("Datasets processed: " + countDatasets(this.datasetsParameters));
    }

    private Map<String, Map<String, DataSourceParameter>> extractDatasetsParameters(DatasetSearchResponse.Data[] datasetSearchResponseData) {
        LOGGER.info("Extracting parameters descriptors...");
        final Map<String, Map<String, DataSourceParameter>> datasetsParameters = new LinkedHashMap<>();
        for (DatasetSearchResponse.Data dataset : datasetSearchResponseData) {
            if (isIncomplete(dataset)) {
                LOGGER.info(dataset.getCollectionName() + ": skipped, due to incomplete data.");
                continue;
            }
            final Map<String, DataSourceParameter> collectionParameters = extractDatasetSupportedParameters(dataset.getDatasetAlias());
            datasetsParameters.put(dataset.getCollectionName(), collectionParameters);
            LOGGER.info(dataset.getCollectionName() + ": " + collectionParameters.size() + " parameters found.");
        }
        LOGGER.info(datasetsParameters.size() + " datasets extracted.");
        return datasetsParameters;
    }

    private Map<String, DataSourceParameter> extractDatasetSupportedParameters(String datasetName) {
        final Map<String, DataSourceParameter> datasetParameters = new LinkedHashMap<>();
        final DatasetFiltersRequest datasetFiltersRequest = new DatasetFiltersRequest();
        datasetFiltersRequest.setDatasetName(datasetName);
        final DatasetFiltersResponse datasetFiltersResponse;
        try (ro.cs.tao.utils.CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.POST, serviceURL + "dataset-filters", "X-Auth-Token", apiKey, new ObjectMapper().writeValueAsString(datasetFiltersRequest))) {
            switch (response.getStatusLine().getStatusCode()) {
                case 200:
                    final String body = EntityUtils.toString(response.getEntity());
                    if (body.contains("SERVER_ERROR")) {
                        throw new QueryException("The request was not successful. Reason: API Server Error");
                    }
                    datasetFiltersResponse = new ObjectMapper().readValue(body, DatasetFiltersResponse.class);
                    break;
                case 401:
                    throw new QueryException("The supplied credentials are invalid!");
                default:
                    throw new QueryException(String.format("The request was not successful. Reason: %s",
                            response.getStatusLine().getReasonPhrase()));
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        final String[] datasets = new String[]{datasetName};
        datasetParameters.put(CommonParameterNames.PLATFORM, new DataSourceParameter(CommonParameterNames.PLATFORM, "datasetName", JavaType.STRING.value(), "Dataset", datasets[0], false, (Object[]) datasets));
        datasetParameters.put(CommonParameterNames.START_DATE, new DataSourceParameter(CommonParameterNames.START_DATE, CommonParameterNames.START_DATE, JavaType.DATE.value(), "Start Date", false));
        datasetParameters.put(CommonParameterNames.END_DATE, new DataSourceParameter(CommonParameterNames.END_DATE, CommonParameterNames.END_DATE, JavaType.DATE.value(), "End Date", false));
        datasetParameters.put(CommonParameterNames.FOOTPRINT, new DataSourceParameter(CommonParameterNames.FOOTPRINT, "spatialFilter", JavaType.POLYGON.value(), "Area of Interest", false));
        for (DatasetFiltersResponse.Data parametersData : datasetFiltersResponse.getData()) {
            final DataSourceParameter dataSourceParameter = buildDataSourceParameter(parametersData);
            if (dataSourceParameter != null && !datasetParameters.containsKey(dataSourceParameter.getName())) {
                datasetParameters.put(dataSourceParameter.getName(), dataSourceParameter);
            }
        }
        return datasetParameters;
    }

    private static DataSourceParameter buildDataSourceParameter(DatasetFiltersResponse.Data parametersData) {
        final String paramName = extractParameterName(parametersData);
        if (paramName.isEmpty()) {
            return null;
        }
        final String paramRemoteName = parametersData.getFieldLabel();
        final Class<?> paramType;
        if (parametersData.getFieldConfig().getType().toLowerCase().contains("number")) {
            paramType = JavaType.DOUBLE.value();
        } else {
            paramType = JavaType.STRING.value();
        }
        final String paramLabel = extractParameterLabel(parametersData);
        if (paramLabel.isEmpty()) {
            return null;
        }
        final Object[] paramValueSet = parametersData.getValueList();
        return new DataSourceParameter(paramName.substring(0, 1).toLowerCase() + paramName.substring(1), paramRemoteName, paramType, paramLabel, null, false, paramValueSet);
    }

    private static String extractParameterName(DatasetFiltersResponse.Data parametersData) {
        final StringBuilder name = new StringBuilder();
        if (parametersData.getDictionaryLink().matches(DATASET_NAME_REGEX)) {
            for (String s : parametersData.getDictionaryLink().replaceAll(DATASET_NAME_REGEX, "$1").split("_")) {
                if (name.length() > 1) {
                    name.append(s.substring(0, 1).toUpperCase()).append(s.substring(1));
                } else {
                    name.append(s);
                }
            }
        }
        return name.toString();
    }

    private static String extractParameterLabel(DatasetFiltersResponse.Data parametersData) {
        final StringBuilder name = new StringBuilder();
        if (parametersData.getDictionaryLink().matches(DATASET_NAME_REGEX)) {
            for (String s : parametersData.getDictionaryLink().replaceAll(DATASET_NAME_REGEX, "$1").split("_")) {
                if (name.length() > 1) {
                    name.append(" ");
                }
                name.append(s.substring(0, 1).toUpperCase()).append(s.substring(1));
            }
        }
        return name.toString();
    }

    private static void groupDatasetsByCommonParameters(Map<String, Map<String, DataSourceParameter>> datasetsParameters, Map<String, CollectionDescription> datasetsDescriptions) {
        LOGGER.info("Grouping parameters descriptors...");
        final String[] datasets = datasetsParameters.keySet().toArray(new String[0]);
        for (int datasetIndex = 0; datasetIndex < datasets.length - 1; datasetIndex++) {
            final String dataset = datasets[datasetIndex];
            for (int nextDatasetIndex = datasetIndex + 1; nextDatasetIndex < datasets.length; nextDatasetIndex++) {
                final String nextDataset = datasets[nextDatasetIndex];
                updateCommonDatasets(datasetsParameters, datasetsDescriptions, dataset, nextDataset);
            }
        }
    }

    private static void updateCommonDatasets(Map<String, Map<String, DataSourceParameter>> datasetsParameters, Map<String, CollectionDescription> datasetsDescriptions, String mainDataset, String commonDataset) {
        final Map<String, DataSourceParameter> mainDatasetParameters = datasetsParameters.get(mainDataset);
        final Map<String, DataSourceParameter> commonDatasetParameters = datasetsParameters.get(commonDataset);
        if (mainDatasetParameters != null && commonDatasetParameters != null && commonParameters(mainDatasetParameters, commonDatasetParameters)) {
            mergeCommonDatasetsParameters(datasetsParameters, mainDataset, commonDataset);
            mergeCommonDatasetsDescriptions(datasetsDescriptions, mainDataset, commonDataset);
        }
    }

    private static void mergeCommonDatasetsParameters(Map<String, Map<String, DataSourceParameter>> datasetsParameters, String mainDataset, String commonDataset) {
        final Map<String, DataSourceParameter> mainDatasetParameters = datasetsParameters.get(mainDataset);
        final Map<String, DataSourceParameter> commonDatasetParameters = datasetsParameters.get(commonDataset);
        final List<Object> mainDatasetCollections = new ArrayList<>(Arrays.asList(mainDatasetParameters.get(CommonParameterNames.PLATFORM).getValueSet()));
        mainDatasetCollections.addAll(Arrays.asList(commonDatasetParameters.get(CommonParameterNames.PLATFORM).getValueSet()));
        mainDatasetParameters.get(CommonParameterNames.PLATFORM).setValueSet(mainDatasetCollections.toArray());
        for (String commonDatasetParameterName : commonDatasetParameters.keySet()) {
            if (!mainDatasetParameters.containsKey(commonDatasetParameterName)) {
                mainDatasetParameters.put(commonDatasetParameterName, commonDatasetParameters.get(commonDatasetParameterName));
            }
        }
        datasetsParameters.remove(commonDataset);
    }

    private static void mergeCommonDatasetsDescriptions(Map<String, CollectionDescription> datasetsDescriptions, String mainDataset, String commonDataset) {
        final CollectionDescription mainDatasetDescriptions = datasetsDescriptions.get(mainDataset);
        final CollectionDescription commonDatasetDescriptions = datasetsDescriptions.get(commonDataset);
        if (!mainDatasetDescriptions.getDescription().equals(commonDatasetDescriptions.getDescription())) {
            mainDatasetDescriptions.setDescription(mainDatasetDescriptions.getDescription() + "\n" + commonDatasetDescriptions.getDescription());
        }
        final int mainTemporalCoverageStart = Integer.parseInt(mainDatasetDescriptions.getTemporalCoverage().replaceAll(".+?(\\d{4}).+", "$1"));
        final int mainTemporalCoverageEnd = Integer.parseInt(mainDatasetDescriptions.getTemporalCoverage().replaceAll(".+(\\d{4})", "$1"));
        final int commonTemporalCoverageStart = Integer.parseInt(mainDatasetDescriptions.getTemporalCoverage().replaceAll(".+?(\\d{4}).+", "$1"));
        final int commonTemporalCoverageEnd = Integer.parseInt(mainDatasetDescriptions.getTemporalCoverage().replaceAll(".+(\\d{4})", "$1"));
        mainDatasetDescriptions.setTemporalCoverage("from " + Math.min(mainTemporalCoverageStart, commonTemporalCoverageStart) + " to " + Math.max(mainTemporalCoverageEnd, commonTemporalCoverageEnd));
        datasetsDescriptions.remove(commonDataset);
    }

    private static boolean commonParameters(Map<String, DataSourceParameter> mainDatasetParameters, Map<String, DataSourceParameter> otherDatasetParameters) {
        final String mainDatasetRemoteName = (String) mainDatasetParameters.get(CommonParameterNames.PLATFORM).getDefaultValue();
        final String otherDatasetRemoteName = (String) otherDatasetParameters.get(CommonParameterNames.PLATFORM).getDefaultValue();
        if (mainDatasetRemoteName.contains("_")) {
            if (!otherDatasetRemoteName.startsWith(mainDatasetRemoteName.split("_")[0])) {
                return false;
            }
        } else {
            if (!otherDatasetRemoteName.startsWith(mainDatasetRemoteName)) {
                return false;
            }
        }
        for (DataSourceParameter datasetParameter : mainDatasetParameters.values()) {
            for (DataSourceParameter otherDatasetParameter : otherDatasetParameters.values()) {
                if (!datasetParameter.getRemoteName().equals(otherDatasetParameter.getRemoteName()) && datasetParameter.getName().equals(otherDatasetParameter.getName())) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Map<String, CollectionDescription> extractDatasetsDescriptions(List<String> datasets, DatasetSearchResponse.Data[] datasetSearchResponseData) {
        LOGGER.info("Extracting datasets descriptors...");
        final Map<String, CollectionDescription> datasetsDescriptions = new LinkedHashMap<>();
        for (DatasetSearchResponse.Data datasetData : datasetSearchResponseData) {
            if (datasets.contains(datasetData.getCollectionName())) {
                final CollectionDescription collectionDescription = buildDatasetCollectionDescription(datasetData);
                datasetsDescriptions.put(datasetData.getCollectionName(), collectionDescription);
            }
        }
        return datasetsDescriptions;
    }

    private static CollectionDescription buildDatasetCollectionDescription(DatasetSearchResponse.Data datasetData) {
        final CollectionDescription collectionDescription = new CollectionDescription();
        collectionDescription.setMission(datasetData.getCollectionName());
        collectionDescription.setSensorType(SensorType.OPTICAL);
        collectionDescription.setDescription(datasetData.getAbstractText());
        collectionDescription.setTemporalCoverage(datasetData.getTemporalCoverage().replaceAll(".+?(\\d{4}).+?(\\d{4}).+", "from $1 to $2"));
        collectionDescription.setSpatialCoverage("Global");
        return collectionDescription;
    }

    private static boolean isIncomplete(DatasetSearchResponse.Data datasetData) {
        return datasetData.getAbstractText() == null || datasetData.getTemporalCoverage() == null;
    }

    private static int countDatasets(Map<String, Map<String, DataSourceParameter>> datasetsParameters) {
        int nrDatasets = 0;
        for (Map<String, DataSourceParameter> datasetParameters : datasetsParameters.values()) {
            nrDatasets += datasetParameters.get(CommonParameterNames.PLATFORM).getValueSet().length;
        }
        return nrDatasets;
    }

    public abstract String authenticate();

}
