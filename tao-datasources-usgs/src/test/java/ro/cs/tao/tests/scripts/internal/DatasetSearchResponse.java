package ro.cs.tao.tests.scripts.internal;

/**
 * @author Adrian Draghici
 */
public class DatasetSearchResponse {
    private long requestId;
    private String version;
    private long sessionId;
    private Data[] data;

    public static class Data {
        private String abstractText;
        private String acquisitionStart;
        private String acquisitionEnd;
        private String[] catalogs;
        private String collectionName;
        private String collectionLongName;
        private String datasetId;
        private String datasetAlias;
        private String datasetCategoryName;
        private String dataOwner;
        private String dateUpdated;
        private String doiNumber;
        private String ingestFrequency;
        private String keywords;
        private String legacyId;
        private long sceneCount;
        private SpatialBounds spatialBounds;

        public static class SpatialBounds{
            private double north;
            private double east;
            private double south;
            private double west;

            public double getNorth() {
                return north;
            }

            public void setNorth(double north) {
                this.north = north;
            }

            public double getEast() {
                return east;
            }

            public void setEast(double east) {
                this.east = east;
            }

            public double getSouth() {
                return south;
            }

            public void setSouth(double south) {
                this.south = south;
            }

            public double getWest() {
                return west;
            }

            public void setWest(double west) {
                this.west = west;
            }
        }
        private String temporalCoverage;
        private boolean supportCloudCover;
        private boolean supportDeletionSearch;
        private boolean allowInKmz;

        public String getAbstractText() {
            return abstractText;
        }

        public void setAbstractText(String abstractText) {
            this.abstractText = abstractText;
        }

        public String getAcquisitionStart() {
            return acquisitionStart;
        }

        public void setAcquisitionStart(String acquisitionStart) {
            this.acquisitionStart = acquisitionStart;
        }

        public String getAcquisitionEnd() {
            return acquisitionEnd;
        }

        public void setAcquisitionEnd(String acquisitionEnd) {
            this.acquisitionEnd = acquisitionEnd;
        }

        public String[] getCatalogs() {
            return catalogs;
        }

        public void setCatalogs(String[] catalogs) {
            this.catalogs = catalogs;
        }

        public String getCollectionName() {
            return collectionName;
        }

        public void setCollectionName(String collectionName) {
            this.collectionName = collectionName;
        }

        public String getCollectionLongName() {
            return collectionLongName;
        }

        public void setCollectionLongName(String collectionLongName) {
            this.collectionLongName = collectionLongName;
        }

        public String getDatasetId() {
            return datasetId;
        }

        public void setDatasetId(String datasetId) {
            this.datasetId = datasetId;
        }

        public String getDatasetAlias() {
            return datasetAlias;
        }

        public void setDatasetAlias(String datasetAlias) {
            this.datasetAlias = datasetAlias;
        }

        public String getDatasetCategoryName() {
            return datasetCategoryName;
        }

        public void setDatasetCategoryName(String datasetCategoryName) {
            this.datasetCategoryName = datasetCategoryName;
        }

        public String getDataOwner() {
            return dataOwner;
        }

        public void setDataOwner(String dataOwner) {
            this.dataOwner = dataOwner;
        }

        public String getDateUpdated() {
            return dateUpdated;
        }

        public void setDateUpdated(String dateUpdated) {
            this.dateUpdated = dateUpdated;
        }

        public String getDoiNumber() {
            return doiNumber;
        }

        public void setDoiNumber(String doiNumber) {
            this.doiNumber = doiNumber;
        }

        public String getIngestFrequency() {
            return ingestFrequency;
        }

        public void setIngestFrequency(String ingestFrequency) {
            this.ingestFrequency = ingestFrequency;
        }

        public String getKeywords() {
            return keywords;
        }

        public void setKeywords(String keywords) {
            this.keywords = keywords;
        }

        public String getLegacyId() {
            return legacyId;
        }

        public void setLegacyId(String legacyId) {
            this.legacyId = legacyId;
        }

        public long getSceneCount() {
            return sceneCount;
        }

        public void setSceneCount(long sceneCount) {
            this.sceneCount = sceneCount;
        }

        public SpatialBounds getSpatialBounds() {
            return spatialBounds;
        }

        public void setSpatialBounds(SpatialBounds spatialBounds) {
            this.spatialBounds = spatialBounds;
        }

        public String getTemporalCoverage() {
            return temporalCoverage;
        }

        public void setTemporalCoverage(String temporalCoverage) {
            this.temporalCoverage = temporalCoverage;
        }

        public boolean isSupportCloudCover() {
            return supportCloudCover;
        }

        public void setSupportCloudCover(boolean supportCloudCover) {
            this.supportCloudCover = supportCloudCover;
        }

        public boolean isSupportDeletionSearch() {
            return supportDeletionSearch;
        }

        public void setSupportDeletionSearch(boolean supportDeletionSearch) {
            this.supportDeletionSearch = supportDeletionSearch;
        }

        public boolean isAllowInKmz() {
            return allowInKmz;
        }

        public void setAllowInKmz(boolean allowInKmz) {
            this.allowInKmz = allowInKmz;
        }
    }

    private String errorCode;
    private String errorMessage;

    public long getRequestId() {
        return requestId;
    }

    public void setRequestId(long requestId) {
        this.requestId = requestId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public long getSessionId() {
        return sessionId;
    }

    public void setSessionId(long sessionId) {
        this.sessionId = sessionId;
    }

    public Data[] getData() {
        return data;
    }

    public void setData(Data[] data) {
        this.data = data;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
