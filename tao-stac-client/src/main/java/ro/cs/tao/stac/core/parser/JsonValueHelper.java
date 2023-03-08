package ro.cs.tao.stac.core.parser;

import com.fasterxml.jackson.core.TreeNode;
import ro.cs.tao.utils.DateUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Helper class for JSON parsers.
 *
 * @author Cosmin Cara
 */
public class JsonValueHelper {
    private static final DateTimeFormatter format = DateUtils.getResilientFormatterAtUTC();
    private static final Pattern datePattern = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");

    /**
     * Returns the JSON node value as string
     * @param node  The JSON node
     */
    public static String getStringValue(TreeNode node) {
        if (node == null)
            return null;
        final String val = node.toString().replace("\"", "");
        if ("null".equals(val))
            return null;
        return val;
    }
    /**
     * Returns the field value of a JSON node as string
     * @param node  The JSON node
     * @param field The name of the field of the node
     */
    public static String getString(TreeNode node, String field) {
        TreeNode value = node.get(field);
        return value != null ? getStringValue(value) : null;
    }
    /**
     * Returns the field value of a JSON node as a string list
     * @param node  The JSON node
     * @param field The name of the field of the node
     */
    public static List<String> getStringArray(TreeNode node, String field) {
        TreeNode valueNode = node.get(field);
        if (valueNode == null || !valueNode.isArray()) {
            return null;
        }
        List<String> values = new ArrayList<>(valueNode.size());
        for (int i = 0; i < valueNode.size(); i++) {
            values.add(getStringValue(valueNode.get(i)));
        }
        return values;
    }
    /**
     * Returns the field value of a JSON node as a nullable integer
     * @param node  The JSON node
     * @param field The name of the field of the node
     */
    public static Integer getInt(TreeNode node, String field) {
        TreeNode valueNode = node.get(field);
        return valueNode != null ? Integer.parseInt(getStringValue(valueNode)) : null;
    }
    /**
     * Returns the field value of a JSON node as an array of integers
     * @param node  The JSON node
     * @param field The name of the field of the node
     */
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
    /**
     * Returns the field value of a JSON node as a nullable long
     * @param node  The JSON node
     * @param field The name of the field of the node
     */
    public static Long getLong(TreeNode node, String field) {
        TreeNode valueNode = node.get(field);
        return valueNode != null ? Long.parseLong(getStringValue(valueNode)) : null;
    }
    /**
     * Returns the field value of a JSON node as an array of longs
     * @param node  The JSON node
     * @param field The name of the field of the node
     */
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
    /**
     * Returns the field value of a JSON node as a nullable double
     * @param node  The JSON node
     * @param field The name of the field of the node
     */
    public static Double getDouble(TreeNode node, String field) {
        TreeNode valueNode = node.get(field);
        return valueNode != null ? Double.parseDouble(getStringValue(valueNode)) : null;
    }
    /**
     * Returns the field value of a JSON node as a 1-dimensional array of doubles
     * @param node  The JSON node
     * @param field The name of the field of the node
     */
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
    /**
     * Returns the field value of a JSON node as a 2-dimensional array of doubles
     * @param node  The JSON node
     * @param field The name of the field of the node
     */
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
    /**
     * Returns the field value of a JSON node as a 3-dimensional array of doubles
     * @param node  The JSON node
     * @param field The name of the field of the node
     */
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
    /**
     * Returns the field value of a JSON node as a 4-dimensional array of doubles
     * @param node  The JSON node
     * @param field The name of the field of the node
     */
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
    /**
     * Returns the field value of a JSON node as a date/time
     * @param node  The JSON node
     * @param field The name of the field of the node
     */
    public static LocalDateTime getDateTime(TreeNode node, String field) {
        TreeNode valueNode = node.get(field);
        LocalDateTime value = null;
        if (valueNode != null) {
            String val = getStringValue(valueNode);
            if (val != null) {
                value = LocalDateTime.parse(val, format);
            }
        }
        return value;
    }
    /**
     * Returns the field value of a JSON node as an array of dates/times
     * @param node  The JSON node
     * @param field The name of the field of the node
     */
    public static LocalDateTime[] getDateTimeArray(TreeNode node, String field) {
        TreeNode valueNode = node.get(field);
        if (getArrayRank(valueNode) == 2) {
            valueNode = valueNode.get(0);
        }
        LocalDateTime[] dateTimes = new LocalDateTime[valueNode.size()];
        for (int i = 0; i < valueNode.size(); i++) {
            String value = getStringValue(valueNode.get(i));
            if (value != null) {
                dateTimes[i] = LocalDateTime.parse(value, format);
            }
        }
        return dateTimes;
    }
    /**
     * Tries to "guess" the typed value of a node.
     * It resolves only dates and dates/times to their actual Java type.
     * For other type of values, it returns the string value of the node.
     * @param node  The JSON node
     */
    public static Object tryGuessTypedValue(TreeNode node) {
        final String value = getStringValue(node);
        if (value == null) {
            return value;
        }
        if (datePattern.matcher(value).find() && value.length() == 10) {
            return LocalDate.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } else if (datePattern.matcher(value).find()) {
            return LocalDateTime.parse(value, DateUtils.getResilientFormatterAtUTC());
        } else {
            return value;
        }
    }

    /**
     * Returns the rank (number of dimensions) of the array representing the value of the given node.
     * If the value is not an array, it returns 0.
     * @param node  The JSON node
     */
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
