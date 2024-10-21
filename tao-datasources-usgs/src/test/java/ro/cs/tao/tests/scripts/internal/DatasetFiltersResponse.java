package ro.cs.tao.tests.scripts.internal;

import java.util.HashMap;

/**
 * @author Adrian Draghici
 */
public class DatasetFiltersResponse {

    private long requestId;
    private String version;
    private long sessionId;
    private Data[] data;

    public static class Data {
        private String id;
        private long legacyFieldId;
        private String dictionaryLink;
        private FieldConfig fieldConfig;

        public static class FieldConfig {
            private String type;
            private Filters[] filters;

            public static class Filters {
                private String type;
                private FiltersOptions options;

                public static class FiltersOptions {
                }

                public String getType() {
                    return type;
                }

                public void setType(String type) {
                    this.type = type;
                }

                public FiltersOptions getOptions() {
                    return options;
                }

                public void setOptions(Object options) {
                    if (options instanceof FiltersOptions) {
                        this.options = (FiltersOptions) options;
                    } else {
                        this.options = null;
                    }
                }
            }

            private String listSql;
            private FieldConfigOptions options;

            public static class FieldConfigOptions {
                private String size;
                private boolean multiple;

                public String getSize() {
                    return size;
                }

                public void setSize(String size) {
                    this.size = size;
                }

                public boolean isMultiple() {
                    return multiple;
                }

                public void setMultiple(boolean multiple) {
                    this.multiple = multiple;
                }
            }

            private Validator[] validators;

            public static class Validator {
                private String type;
                private ValidatorOptions options;

                public static class ValidatorOptions {
                }

                public String getType() {
                    return type;
                }

                public void setType(String type) {
                    this.type = type;
                }

                public ValidatorOptions getOptions() {
                    return options;
                }

                public void setOptions(ValidatorOptions options) {
                    this.options = options;
                }
            }

            private String numElements;
            private String displayListId;

            public String getType() {
                return type;
            }

            public void setType(String type) {
                this.type = type;
            }

            public Filters[] getFilters() {
                return filters;
            }

            public void setFilters(Filters[] filters) {
                this.filters = filters;
            }

            public String getListSql() {
                return listSql;
            }

            public void setListSql(String listSql) {
                this.listSql = listSql;
            }

            public FieldConfigOptions getOptions() {
                return options;
            }

            public void setOptions(Object options) {
                if (options instanceof FieldConfigOptions) {
                    this.options = (FieldConfigOptions) options;
                } else {
                    this.options = null;
                }
            }

            public Validator[] getValidators() {
                return validators;
            }

            public void setValidators(Validator[] validators) {
                this.validators = validators;
            }

            public String getNumElements() {
                return numElements;
            }

            public void setNumElements(String numElements) {
                this.numElements = numElements;
            }

            public String getDisplayListId() {
                return displayListId;
            }

            public void setDisplayListId(String displayListId) {
                this.displayListId = displayListId;
            }
        }

        private String fieldLabel;
        private String searchSql;
        private String[] valueList;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public long getLegacyFieldId() {
            return legacyFieldId;
        }

        public void setLegacyFieldId(long legacyFieldId) {
            this.legacyFieldId = legacyFieldId;
        }

        public String getDictionaryLink() {
            return dictionaryLink;
        }

        public void setDictionaryLink(String dictionaryLink) {
            this.dictionaryLink = dictionaryLink;
        }

        public FieldConfig getFieldConfig() {
            return fieldConfig;
        }

        public void setFieldConfig(FieldConfig fieldConfig) {
            this.fieldConfig = fieldConfig;
        }

        public String getFieldLabel() {
            return fieldLabel;
        }

        public void setFieldLabel(String fieldLabel) {
            this.fieldLabel = fieldLabel;
        }

        public String getSearchSql() {
            return searchSql;
        }

        public void setSearchSql(String searchSql) {
            this.searchSql = searchSql;
        }

        public String[] getValueList() {
            return valueList;
        }

        public void setValueList(Object valueList) {
            if (valueList instanceof HashMap) {
                this.valueList = ((HashMap<?, ?>) valueList).keySet().stream().map(o -> (String) o).filter(o->!o.isEmpty()).distinct().toArray(String[]::new);
            } else {
                this.valueList = null;
            }
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
