package ro.cs.tao.functions;

import ro.cs.tao.eodata.naming.TokenResolver;
import ro.cs.tao.utils.StringUtilities;
import ro.cs.tao.utils.TriFunction;

import java.util.function.Function;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringResolver implements TokenResolver {
    private static final Pattern substringPattern = Pattern.compile("(SUBSTRING\\(([A-Za-z0-9_\\/\\\\.-]+),(\\d+),(\\d+)\\))");
    private static final Pattern upperPattern = Pattern.compile("(UPPER\\(([A-Za-z0-9_\\/\\\\.-]+)\\))");
    private static final Pattern lowerPattern = Pattern.compile("(LOWER\\(([A-Za-z0-9_\\/\\\\.-]+)\\))");
    private static final Pattern charPattern = Pattern.compile("(CHAR\\((\\d{2,3})\\))");

    public static final TriFunction<String, Integer, Integer, String> SUBSTRING = (str, i, j) -> {
        return (!StringUtilities.isNullOrEmpty(str) && i > 0 && j > 0 && i + j < str.length())
               ? str.substring(i, i + j)
               : str;
    };

    public static final Function<String, String> UPPER = (str) -> {
        return !StringUtilities.isNullOrEmpty(str) ? str.toUpperCase() : str;
    };

    public static final Function<String, String> LOWER = (str) -> {
        return !StringUtilities.isNullOrEmpty(str) ? str.toLowerCase() : str;
    };

    public static final Function<String, String> CHAR = (str) -> {
        if (!StringUtilities.isNullOrEmpty(str)) {
            return null;
        }
        try {
            int value = Integer.parseInt(str);
            if (value < 32 || value > 126) {
                return null;
            }
            char chr = (char) value;
            return new String(new char[] { chr });
        } catch (Throwable t) {
            return null;
        }
    };

    @Override
    public String resolve(String expression) {
        if (StringUtilities.isNullOrEmpty(expression)) {
            return expression;
        }
        String transformed = expression;
        try {
            Matcher matcher = substringPattern.matcher(expression);
            if (matcher.find()) {
                transformed = transformed.replace(matcher.group(1),
                                                  SUBSTRING.apply(matcher.group(2),
                                                                  Integer.parseInt(matcher.group(3)),
                                                                  Integer.parseInt(matcher.group(4))));
            }
            matcher = upperPattern.matcher(expression);
            if (matcher.find()) {
                transformed = transformed.replace(matcher.group(1), UPPER.apply(matcher.group(2)));
            }
            matcher = lowerPattern.matcher(expression);
            if (matcher.find()) {
                transformed = transformed.replace(matcher.group(1), LOWER.apply(matcher.group(2)));
            }
            matcher = charPattern.matcher(expression);
            if (matcher.find()) {
                transformed = transformed.replace(matcher.group(1), CHAR.apply(matcher.group(2)));
            }
        } catch (Throwable t) {
            Logger.getLogger(StringResolver.class.getName()).warning(t.getMessage());
        }
        return transformed;
    }
}
