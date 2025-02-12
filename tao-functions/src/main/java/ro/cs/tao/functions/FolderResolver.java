package ro.cs.tao.functions;

import ro.cs.tao.eodata.naming.TokenResolver;
import ro.cs.tao.utils.StringUtilities;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FolderResolver implements TokenResolver {
    private static final Pattern folderPattern = Pattern.compile("(FOLDER\\((\\d{2}[A-Z]{3})\\))");

    public static final Function<String, String> FOLDER = (str) -> {
        final int idx = str.lastIndexOf('/');
        return idx > 0 ? str.substring(idx + 1) : str;
    };

    @Override
    public String resolve(String expression) {
        if (StringUtilities.isNullOrEmpty(expression)) {
            return expression;
        }
        String transformed = expression.endsWith("/")
                             ? expression.substring(0, expression.length() - 1)
                             : expression;
        Matcher matcher = folderPattern.matcher(expression);
        if (matcher.find()) {
            transformed = transformed.replace(matcher.group(1), FOLDER.apply(matcher.group(2)));
        }
        return transformed;
    }
}
