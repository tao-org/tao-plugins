package ro.cs.tao.utils.cwl;

import org.w3id.cwl.cwl1_2.CWLVersion;

import java.util.*;

public class CWLConstants {
    public final static String CWL_GRAPH_ID = "$graph";
    public final static String CWL_VERSION = CWLVersion.V1_2.toString();
    public final static String YAML_FILE_NAME = "inputs.yaml";
    public final static String WF_OUTPUT_NAME_SUFFIX = "_output_name";
    public final static String MAIN_WF_OUTPUT_SOURCE_PREFIX = "workflow_";
    public final static String MAIN_WF_ID = "workflow_start";
    public final static String INPUT_PARAM_POSITION = "source";
    public final static String OUTPUT_PARAM_POSITION = "target";
    public final static String PARAMETER_PARAM_POSITION = "param";
    public static final Set<String> INTERPRETERS = new HashSet<>(Arrays.asList("bash", "sh", "/bin/bash", "/usr/bin/env"));
    public final static Map<String, List<String>> DATA_FORMAT_TYPE = new LinkedHashMap<>(){{
        put("Raster",Arrays.asList(".asc", ".bmp", ".bip", ".bpw", ".bsq", ".bt", ".dat", ".dem", ".dim", ".gff", ".gfw", ".gif", ".grd", ".h4", ".h5", ".hdf",".hdf5", ".hf2",
                ".hfz", ".hdr", ".hgt", ".img", ".jp2", ".jpg", ".jpe", ".jpeg", ".jpc", ".jpx", ".nc", ".nsf", ".ntf", ".ovr", ".png", ".raw", ".safe", ".slc", ".tff", ".tif", ".tiff", ".vrt"));
        put("Vector", Arrays.asList(".shp", ".dbf", ".shx", ".geojson", ".json", ".kml", ".kmz", ".gpkg", ".dxf", ".svg"));
    }};
    public final static String UNKNOWN_DATA_FORMAT_TYPE = "Unknown";
}
