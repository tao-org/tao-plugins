package ro.cs.tao.stac.core.model.extensions.processing;

public class Expression {
    private String format;
    private Object expression;

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public Object getExpression() {
        return expression;
    }

    public void setExpression(Object expression) {
        this.expression = expression;
    }
}
