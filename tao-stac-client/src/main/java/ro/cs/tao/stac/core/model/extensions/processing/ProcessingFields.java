package ro.cs.tao.stac.core.model.extensions.processing;

public class ProcessingFields {

    public static String PREFIX = "file:";
    /**
     * The name commonly used to refer to the processing level to make it easier to search
     * for product level across collections or items.
     */
    public static final String PROCESSING_LEVEL = "processing:level";
    /**
     * An expression or processing chain that describes how the data has been processed.
     * Alternatively, you can also link to a processing chain with the relation type processing-expression
     */
    public static final String PROCESSING_EXPRESSION = "processing:expression";
    /**
     * Lineage Information provided as free text information about the how observations were processed or models
     * that were used to create the resource being described NASA ISO.
     * For example, GRD Post Processing for "GRD" product of Sentinel-1 satellites.
     */
    public static final String PROCESSING_LINEAGE = "processing:lineage";
    /**
     * The name of the facility that produced the data.
     * For example, Copernicus S1 Core Ground Segment - DPA for product of Sentinel-1 satellites.
     */
    public static final String PROCESSING_FACILITY = "processing:facility";
    /**
     * Processing date and time of the corresponding data formatted according to RFC 3339, section 5.6, in UTC.
     */
    public static final String PROCESSING_DATETIME = "processing:datetime";
    /**
     * The version of the primary processing software or processing chain that produced the data.
     * For example, this could be the processing baseline for the Sentinel missions.
     */
    public static final String PROCESSING_VERSION = "processing:version";
    /**
     * A dictionary with name/version for key/value describing one or more applications or libraries
     * that were involved during the production of the data for provenance purposes.
     */
    public static final String PROCESSING_SOFTWARE = "processing:software";
}
