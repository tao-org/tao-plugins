package ro.cs.tao.stac.core.model;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class Asset extends Extensible {

    private static final Set<String> declaredFieldNames;

    static {
        final Field[] declaredFields = Asset.class.getDeclaredFields();
        declaredFieldNames = Arrays.stream(declaredFields).map(Field::getName).collect(Collectors.toSet());
    }

    protected String href;
    protected String title;
    protected String description;
    protected String type;
    protected List<String> roles;

    public static Set<String> coreFieldNames(){
        return declaredFieldNames;
    }

    public String getHref() {
        return href;
    }

    public void setHref(String href) {
        this.href = href;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<String> getRoles() {
        if (this.roles == null) {
            this.roles = new ArrayList<>();
        }
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }
}
