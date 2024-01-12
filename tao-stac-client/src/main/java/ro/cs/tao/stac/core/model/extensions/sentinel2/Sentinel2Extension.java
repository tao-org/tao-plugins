package ro.cs.tao.stac.core.model.extensions.sentinel2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.TreeNode;
import ro.cs.tao.stac.core.model.Extensible;
import ro.cs.tao.stac.core.model.extensions.Extension;
import ro.cs.tao.stac.core.model.extensions.fileinfo.FileInfoExtension;
import ro.cs.tao.stac.core.parser.JsonValueHelper;

import java.time.LocalDateTime;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class Sentinel2Extension<E extends Extensible> extends Extension<E> {

    public Sentinel2Extension(E parent) {
        super(parent);
    }

    @Override
    public String getPrefix() {
        return S2Fields.PREFIX;
    }

    @Override
    public void extractField(TreeNode node, String name) throws JsonProcessingException {
        try {
            if (S2Fields.TILE_ID.equals(name)) {
                setTileId(JsonValueHelper.getString(node, name));
            } else if (S2Fields.GRANULE_ID.equals(name)) {
                setGranuleId(JsonValueHelper.getString(node, name));
            } else if (S2Fields.DATATAKE_ID.equals(name)) {
                setDatatakeId(JsonValueHelper.getString(node, name));
            } else if (S2Fields.PRODUCT_URI.equals(name)) {
                setProductUri(JsonValueHelper.getString(node, name));
            } else if (S2Fields.DATASTRIP_ID.equals(name)) {
                setDatastripId(JsonValueHelper.getString(node, name));
            } else if (S2Fields.PRODUCT_TYPE.equals(name)) {
                setProductType(JsonValueHelper.getString(node, name));
            } else if (S2Fields.DATATAKE_TYPE.equals(name)) {
                setDatatakeType(JsonValueHelper.getString(node, name));
            } else if (S2Fields.GENERATION_TIME.equals(name)) {
                setGenerationTime(JsonValueHelper.getDateTime(node, name));
            } else if (S2Fields.PROCESSING_BASELINE.equals(name)) {
                setProcessingBaseline(JsonValueHelper.getString(node, name));
            } else if (S2Fields.WATER_PERCENTAGE.equals(name)) {
                setWaterPercentage(JsonValueHelper.getDouble(node, name));
            } else if (S2Fields.MEAN_SOLAR_ZENITH.equals(name)) {
                setMeanSolarZenith(JsonValueHelper.getDouble(node, name));
            } else if (S2Fields.MEAN_SOLAR_AZIMUTH.equals(name)) {
                setMeanSolarAzimuth(JsonValueHelper.getDouble(node, name));
            } else if (S2Fields.SNOW_ICE_PERCENTAGE.equals(name)) {
                setSnowIcePercentage(JsonValueHelper.getDouble(node, name));
            } else if (S2Fields.VEGETATION_PERCENTAGE.equals(name)) {
                setVegetationPercentage(JsonValueHelper.getDouble(node, name));
            } else if (S2Fields.THIN_CIRRUS_PERCENTAGE.equals(name)) {
                setThinCirrusPercentage(JsonValueHelper.getDouble(node, name));
            } else if (S2Fields.CLOUD_SHADOW_PERCENTAGE.equals(name)) {
                setCloudShadowPercentage(JsonValueHelper.getDouble(node, name));
            } else if (S2Fields.NODATA_PIXEL_PERCENTAGE.equals(name)) {
                setNodataPixelPercentage(JsonValueHelper.getDouble(node, name));
            } else if (S2Fields.UNCLASSIFIED_PERCENTAGE.equals(name)) {
                setUnclassifiedPercentage(JsonValueHelper.getDouble(node, name));
            } else if (S2Fields.DARK_FEATURES_PERCENTAGE.equals(name)) {
                setDarkFeaturesPercentage(JsonValueHelper.getDouble(node, name));
            } else if (S2Fields.NOT_VEGETATED_PERCENTAGE.equals(name)) {
                setNotVegetatedPercentage(JsonValueHelper.getDouble(node, name));
            } else if (S2Fields.DEGRADED_MSI_DATA_PERCENTAGE.equals(name)) {
                setDegradedMsiDataPercentage(JsonValueHelper.getDouble(node, name));
            } else if (S2Fields.HIGH_PROBA_CLOUDS_PERCENTAGE.equals(name)) {
                setHighProbaCloudsPercentage(JsonValueHelper.getDouble(node, name));
            } else if (S2Fields.MEDIUM_PROBA_CLOUDS_PERCENTAGE.equals(name)) {
                setMediumProbaCloudsPercentage(JsonValueHelper.getDouble(node, name));
            } else if (S2Fields.SATURATED_DEFECTIVE_PIXEL_PERCENTAGE.equals(name)) {
                setSaturatedDefectivePixelPercentage(JsonValueHelper.getDouble(node, name));
            } else if (S2Fields.REFLECTANCE_CONVERSION_FACTOR.equals(name)) {
                setReflectanceConversionFactor(JsonValueHelper.getDouble(node, name));
            } else if (S2Fields.MGRS_TILE.equals(name)) {
                setMgrsTile(JsonValueHelper.getString(node, name));
            }
        } catch (Exception e) {
            Logger.getLogger(FileInfoExtension.class.getName()).warning("Cannot extract field " + name + ": " + e.getMessage());
        }
    }

    public String getTileId() {
        return parent.getField(S2Fields.TILE_ID);
    }

    public void setTileId(String tileId) {
        parent.addField(S2Fields.TILE_ID, tileId);
    }

    public String getDatatakeId() {
        return parent.getField(S2Fields.DATATAKE_ID);
    }

    public void setDatatakeId(String datatakeId) {
        parent.addField(S2Fields.DATATAKE_ID, datatakeId);
    }

    public String getProductUri() {
        return parent.getField(S2Fields.PRODUCT_URI);
    }

    public void setProductUri(String productUri) {
        parent.addField(S2Fields.PRODUCT_URI, productUri);
    }

    public String getDatastripId() {
        return parent.getField(S2Fields.DATASTRIP_ID);
    }

    public void setDatastripId(String datastripId) {
        parent.addField(S2Fields.DATASTRIP_ID, datastripId);
    }

    public String getProductType() {
        return parent.getField(S2Fields.PRODUCT_TYPE);
    }

    public void setProductType(String productType) {
        parent.addField(S2Fields.PRODUCT_TYPE, productType);
    }

    public String getDatatakeType() {
        return parent.getField(S2Fields.DATATAKE_TYPE);
    }

    public void setDatatakeType(String datatakeType) {
        parent.addField(S2Fields.DATATAKE_TYPE, datatakeType);
    }

    public LocalDateTime getGenerationTime() {
        return parent.getField(S2Fields.GENERATION_TIME);
    }

    public void setGenerationTime(LocalDateTime generationTime) {
        parent.addField(S2Fields.GENERATION_TIME, generationTime);
    }

    public String getProcessingBaseline() {
        return parent.getField(S2Fields.PROCESSING_BASELINE);
    }

    public void setProcessingBaseline(String processingBaseline) {
        parent.addField(S2Fields.PROCESSING_BASELINE, processingBaseline);
    }

    public double getReflectanceConversionFactor() {
        return parent.getField(S2Fields.REFLECTANCE_CONVERSION_FACTOR);
    }

    public void setReflectanceConversionFactor(double reflectanceConversionFactor) {
        parent.addField(S2Fields.REFLECTANCE_CONVERSION_FACTOR, reflectanceConversionFactor);
    }

    public double getWaterPercentage() {
        return parent.getField(S2Fields.WATER_PERCENTAGE);
    }

    public void setWaterPercentage(double waterPercentage) {
        parent.addField(S2Fields.WATER_PERCENTAGE, waterPercentage);
    }

    public double getSnowIcePercentage() {
        return parent.getField(S2Fields.SNOW_ICE_PERCENTAGE);
    }

    public void setSnowIcePercentage(double snowIcePercentage) {
        parent.addField(S2Fields.SNOW_ICE_PERCENTAGE, snowIcePercentage);
    }

    public double getVegetationPercentage() {
        return parent.getField(S2Fields.VEGETATION_PERCENTAGE);
    }

    public void setVegetationPercentage(double vegetationPercentage) {
        parent.addField(S2Fields.VEGETATION_PERCENTAGE, vegetationPercentage);
    }

    public double getThinCirrusPercentage() {
        return parent.getField(S2Fields.THIN_CIRRUS_PERCENTAGE);
    }

    public void setThinCirrusPercentage(double thinCirrusPercentage) {
        parent.addField(S2Fields.THIN_CIRRUS_PERCENTAGE, thinCirrusPercentage);
    }

    public double getCloudShadowPercentage() {
        return parent.getField(S2Fields.CLOUD_SHADOW_PERCENTAGE);
    }

    public void setCloudShadowPercentage(double cloudShadowPercentage) {
        parent.addField(S2Fields.CLOUD_SHADOW_PERCENTAGE, cloudShadowPercentage);
    }

    public double getNodataPixelPercentage() {
        return parent.getField(S2Fields.NODATA_PIXEL_PERCENTAGE);
    }

    public void setNodataPixelPercentage(double nodataPixelPercentage) {
        parent.addField(S2Fields.NODATA_PIXEL_PERCENTAGE, nodataPixelPercentage);
    }

    public double getUnclassifiedPercentage() {
        return parent.getField(S2Fields.UNCLASSIFIED_PERCENTAGE);
    }

    public void setUnclassifiedPercentage(double unclassifiedPercentage) {
        parent.addField(S2Fields.UNCLASSIFIED_PERCENTAGE, unclassifiedPercentage);
    }

    public double getDarkFeaturesPercentage() {
        return parent.getField(S2Fields.DARK_FEATURES_PERCENTAGE);
    }

    public void setDarkFeaturesPercentage(double darkFeaturesPercentage) {
        parent.addField(S2Fields.DARK_FEATURES_PERCENTAGE, darkFeaturesPercentage);
    }

    public double getNotVegetatedPercentage() {
        return parent.getField(S2Fields.NOT_VEGETATED_PERCENTAGE);
    }

    public void setNotVegetatedPercentage(double notVegetatedPercentage) {
        parent.addField(S2Fields.NOT_VEGETATED_PERCENTAGE, notVegetatedPercentage);
    }

    public double getDegradedMsiDataPercentage() {
        return parent.getField(S2Fields.DEGRADED_MSI_DATA_PERCENTAGE);
    }

    public void setDegradedMsiDataPercentage(double degradedMsiDataPercentage) {
        parent.addField(S2Fields.DEGRADED_MSI_DATA_PERCENTAGE, degradedMsiDataPercentage);
    }

    public double getHighProbaCloudsPercentage() {
        return parent.getField(S2Fields.HIGH_PROBA_CLOUDS_PERCENTAGE);
    }

    public void setHighProbaCloudsPercentage(double highProbaCloudsPercentage) {
        parent.addField(S2Fields.HIGH_PROBA_CLOUDS_PERCENTAGE, highProbaCloudsPercentage);
    }

    public double getMediumProbaCloudsPercentage() {
        return parent.getField(S2Fields.MEDIUM_PROBA_CLOUDS_PERCENTAGE);
    }

    public void setMediumProbaCloudsPercentage(double mediumProbaCloudsPercentage) {
        parent.addField(S2Fields.MEDIUM_PROBA_CLOUDS_PERCENTAGE, mediumProbaCloudsPercentage);
    }

    public double getSaturatedDefectivePixelPercentage() {
        return parent.getField(S2Fields.SATURATED_DEFECTIVE_PIXEL_PERCENTAGE);
    }

    public void setSaturatedDefectivePixelPercentage(double saturatedDefectivePixelPercentage) {
        parent.addField(S2Fields.SATURATED_DEFECTIVE_PIXEL_PERCENTAGE, saturatedDefectivePixelPercentage);
    }

    public String getGranuleId() {
        return parent.getField(S2Fields.GRANULE_ID);
    }

    public void setGranuleId(String granuleId) {
        parent.addField(S2Fields.GRANULE_ID, granuleId);
    }

    public String getMgrsTile() {
        return parent.getField(S2Fields.MGRS_TILE);
    }

    public void setMgrsTile(String mgrsTile) {
        if (!Pattern.compile("^\\\\d\\\\d?[CDEFGHJKLMNPQRSTUVWX][ABCDEFGHJKLMNPQRSTUVWXYZ][ABCDEFGHJKLMNPQRSTUV]$").matcher(mgrsTile).matches()) {
            throw new IllegalArgumentException("Invalid mgrs_tile value");
        }
        parent.addField(S2Fields.MGRS_TILE, mgrsTile);
    }

    public double getMeanSolarZenith() {
        return parent.getField(S2Fields.MEAN_SOLAR_ZENITH);
    }

    public void setMeanSolarZenith(double meanSolarZenith) {
        parent.addField(S2Fields.MEAN_SOLAR_ZENITH, meanSolarZenith);
    }

    public double getMeanSolarAzimuth() {
        return parent.getField(S2Fields.MEAN_SOLAR_AZIMUTH);
    }

    public void setMeanSolarAzimuth(double meanSolarAzimuth) {
        parent.addField(S2Fields.MEAN_SOLAR_AZIMUTH, meanSolarAzimuth);
    }
}
