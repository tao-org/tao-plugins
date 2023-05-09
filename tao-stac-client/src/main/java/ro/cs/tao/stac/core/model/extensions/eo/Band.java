package ro.cs.tao.stac.core.model.extensions.eo;

public class Band {
    private String name;
    private BandType common_name;
    private double center_wavelength;
    private double full_width_half_max;
    private double solar_illumination;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BandType getCommon_name() {
        return common_name;
    }

    public void setCommon_name(BandType common_name) {
        this.common_name = common_name;
    }

    public double getCenter_wavelength() {
        return center_wavelength;
    }

    public void setCenter_wavelength(double center_wavelength) {
        this.center_wavelength = center_wavelength;
    }

    public double getFull_width_half_max() {
        return full_width_half_max;
    }

    public void setFull_width_half_max(double full_width_half_max) {
        this.full_width_half_max = full_width_half_max;
    }

    public double getSolar_illumination() {
        return solar_illumination;
    }

    public void setSolar_illumination(double solar_illumination) {
        this.solar_illumination = solar_illumination;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append(name).append(": ");
        if (common_name != null) {
            builder.append(common_name).append(", ");
        }
        builder.append("cwl: ").append(center_wavelength > 0 ? center_wavelength : "n/a").append(",");
        builder.append("fwhm: ").append(full_width_half_max > 0 ? full_width_half_max : "n/a").append(",");
        builder.append("solar illumination: ").append(solar_illumination > 0 ? solar_illumination : "n/a");
        return builder.toString();
    }
}
