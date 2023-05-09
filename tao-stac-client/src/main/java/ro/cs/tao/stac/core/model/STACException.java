package ro.cs.tao.stac.core.model;

import javax.annotation.Nonnull;

public class STACException extends Exception {
    protected final String code;
    protected final String description;

    public STACException(@Nonnull String code, String description) {
        super(description);
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
