package ro.cs.tao.stac.core.parser;

import com.fasterxml.jackson.core.TreeNode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class JsonValueHelper {

    private static final List<DateTimeFormatter> formats = new ArrayList<DateTimeFormatter>() {{
        add(DateTimeFormatter.ISO_DATE_TIME);
        add(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        add(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        add(DateTimeFormatter.ISO_ZONED_DATE_TIME);
        add(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssSSSSXXX"));
    }};
    private static final Pattern floatPattern = Pattern.compile("(\\d+)\\.(\\d+)");
    private static final Pattern intPattern = Pattern.compile("(\\d+)\\.(\\d+)");
    private static final Pattern datePattern = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern dateTimePattern1 = Pattern.compile("(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2})");
    private static final Pattern dateTimePattern2 = Pattern.compile("(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z)");
    private static final Pattern dateTimePattern3 = Pattern.compile("(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d+)");
    private static final Pattern dateTimePattern4 = Pattern.compile("(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2,6}\\+\\d{2}:\\d{2})");

    public static String getStringValue(TreeNode node) {
        return node != null ? node.toString().replace("\"", "") : null;
    }

    public static String getString(TreeNode node, String field) {
        TreeNode value = node.get(field);
        return value != null ? getStringValue(value) : null;
    }

    public static List<String> getStringArray(TreeNode node, String field) {
        TreeNode valueNode = node.get(field);
        if (!valueNode.isArray()) {
            return null;
        }
        List<String> values = new ArrayList<>(valueNode.size());
        for (int i = 0; i < valueNode.size(); i++) {
            values.add(getStringValue(valueNode.get(i)));
        }
        return values;
    }

    public static Integer getInt(TreeNode node, String field) {
        TreeNode valueNode = node.get(field);
        return valueNode != null ? Integer.parseInt(getStringValue(valueNode)) : null;
    }

    public static int[] getIntArray(TreeNode node, String field) {
        TreeNode valueNode = node.get(field);
        if (!valueNode.isArray()) {
            return null;
        }
        int[] values = new int[valueNode.size()];
        for (int i = 0; i < valueNode.size(); i++) {
            values[i] = Integer.parseInt(getStringValue(valueNode.get(i)));
        }
        return values;
    }

    public static Long getLong(TreeNode node, String field) {
        TreeNode valueNode = node.get(field);
        return valueNode != null ? Long.parseLong(getStringValue(valueNode)) : null;
    }

    public static long[] getLongArray(TreeNode node, String field) {
        TreeNode valueNode = node.get(field);
        if (!valueNode.isArray()) {
            return null;
        }
        long[] values = new long[valueNode.size()];
        for (int i = 0; i < valueNode.size(); i++) {
            values[i] = Long.parseLong(getStringValue(valueNode.get(i)));
        }
        return values;
    }

    public static Double getDouble(TreeNode node, String field) {
        TreeNode valueNode = node.get(field);
        return valueNode != null ? Double.parseDouble(getStringValue(valueNode)) : null;
    }

    public static double[] getDoubleArray1(TreeNode node, String field) {
        TreeNode valueNode = node.get(field);
        if (getArrayRank(valueNode) == 2) {
            valueNode = valueNode.get(0);
        }
        final int size = valueNode.size();
        double[] values = new double[size];
        for (int i = 0; i < size; i++) {
            values[i] = Double.parseDouble(getStringValue(valueNode.get(i)));
        }
        return values;
    }

    public static double[][] getDoubleArray2(TreeNode node, String field) {
        TreeNode valueNode = node.get(field);
        if (getArrayRank(valueNode) == 3) {
            valueNode = valueNode.get(0);
        }
        final int size = valueNode.size();
        double[][] values = new double[size][];
        for (int i = 0; i < size; i++) {
            TreeNode childNode = valueNode.get(i);
            values[i] = new double[childNode.size()];
            for (int j = 0; j < childNode.size(); j++) {
                values[i][j] = Double.parseDouble(getStringValue(childNode.get(j)));
            }
        }
        return values;
    }

    public static double[][][] getDoubleArray3(TreeNode node, String field) {
        TreeNode valueNode = node.get(field);
        if (getArrayRank(valueNode) == 4) {
            valueNode = valueNode.get(0);
        }
        final int size = valueNode.size();
        double[][][] values = new double[size][][];
        for (int i = 0; i < size; i++) {
            TreeNode arr1 = valueNode.get(i);
            values[i] = new double[arr1.size()][];
            for (int j = 0; j < arr1.size(); j++) {
                TreeNode arr2 = arr1.get(j);
                values[i][j] = new double[arr2.size()];
                for (int k = 0; k < arr2.size(); k++) {
                    values[i][j][k] = Double.parseDouble(getStringValue(arr2.get(k)));
                }
            }
        }
        return values;
    }

    public static double[][][][] getDoubleArray4(TreeNode node, String field) {
        TreeNode valueNode = node.get(field);
        if (getArrayRank(valueNode) == 5) {
            valueNode = valueNode.get(0);
        }
        final int size = valueNode.size();
        double[][][][] values = new double[size][][][];
        for (int i = 0; i < size; i++) {
            TreeNode arr1 = valueNode.get(i);
            values[i] = new double[arr1.size()][][];
            for (int j = 0; j < arr1.size(); j++) {
                TreeNode arr2 = arr1.get(j);
                values[i][j] = new double[arr2.size()][];
                for (int k = 0; k < arr2.size(); k++) {
                    TreeNode arr3 = arr2.get(k);
                    values[i][j][k] = new double[arr3.size()];
                    for (int l = 0; l < arr3.size(); l++) {
                        values[i][j][k][l] = Double.parseDouble(getStringValue(arr3.get(l)));
                    }
                }
            }
        }
        return values;
    }

    public static LocalDateTime getDateTime(TreeNode node, String field) {
        TreeNode valueNode = node.get(field);
        LocalDateTime value = null;
        if (valueNode != null && !"null".equals(valueNode.toString())) {
            for (DateTimeFormatter format : formats) {
                try {
                    value = LocalDateTime.parse(getStringValue(valueNode), format);
                    break;
                } catch (Exception ignored) {
                }
            }
            if (value == null) {
                throw new DateTimeParseException("Unsupported date/time format", valueNode.toString(), 0);
            }
        }
        return value;
    }

    public static LocalDateTime[] getDateTimeArray(TreeNode node, String field) {
        TreeNode valueNode = node.get(field);
        if (getArrayRank(valueNode) == 2) {
            valueNode = valueNode.get(0);
        }
        LocalDateTime[] dateTimes = new LocalDateTime[valueNode.size()];
        for (int i = 0; i < valueNode.size(); i++) {
            for (DateTimeFormatter format : formats) {
                try {
                    dateTimes[i] = LocalDateTime.parse(getStringValue(valueNode.get(i)), format);
                    break;
                } catch (Exception ignored) {
                }
            }
        }
        return dateTimes;
    }

    public static Object tryGuessTypedValue(TreeNode node) {
        final String value = getStringValue(node);
        if (value == null) {
            return value;
        }
        if (intPattern.matcher(value).find() && !value.contains(" ") && !value.contains(".")) {
            return Long.parseLong(value);
        } else if (floatPattern.matcher(value).find() && !value.contains(" ") && !value.contains("T")) {
            return Double.parseDouble(value);
        } else if (datePattern.matcher(value).find() && value.length() == 10) {
            return LocalDate.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } else if (dateTimePattern1.matcher(value).find() && value.length() == 19) {
            return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        } else if (dateTimePattern2.matcher(value).find()) {
            return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));
        } else if (dateTimePattern3.matcher(value).find()) {
            return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));
        } else if (dateTimePattern4.matcher(value).find()) {
            return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssSSSSXXX"));
        } else {
            return value;
        }
    }

    private static int getArrayRank(final TreeNode node) {
        int i = 0;
        TreeNode current = node;
        while (current.isArray()) {
            current = current.get(0);
            i++;
        }
        return i;
    }
}
