package ro.cs.tao.execution.drmaa.kubernetes;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QuantityParser {
    public enum QuantityType {
        CPU, MEMORY
    }

    public static BigDecimal parseQuantity(String quantity, QuantityType type) {
        if (quantity == null || quantity.isEmpty()) {
            return BigDecimal.ZERO;
        }

        quantity = quantity.trim();

        // CPU Parsing
        if (type == QuantityType.CPU) {
            if (quantity.endsWith("n")) {
                return new BigDecimal(quantity.replace("n", "")).divide(BigDecimal.valueOf(1_000_000_000));
            } else if (quantity.endsWith("u")) {
                return new BigDecimal(quantity.replace("u", "")).divide(BigDecimal.valueOf(1_000_000));
            } else if (quantity.endsWith("m")) {
                return new BigDecimal(quantity.replace("m", "")).divide(BigDecimal.valueOf(1000));
            } else {
                return new BigDecimal(quantity); // assume cores
            }
        }

        // Memory Parsing (Kubernetes-style binary suffixes)
        Pattern pattern = Pattern.compile("([0-9.]+)([a-zA-Z]+)?");
        Matcher matcher = pattern.matcher(quantity);

        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid quantity format: " + quantity);
        }

        BigDecimal value = new BigDecimal(matcher.group(1));
        String suffix = matcher.group(2);

        if (suffix == null) return value;

        switch (suffix) {
            case "Ki": return value.multiply(BigDecimal.valueOf(1024));
            case "Mi": return value.multiply(BigDecimal.valueOf(1024 * 1024));
            case "Gi": return value.multiply(BigDecimal.valueOf(1024L * 1024L * 1024L));
            case "Ti": return value.multiply(BigDecimal.valueOf(1024L * 1024L * 1024L * 1024L));
            case "K":  return value.multiply(BigDecimal.valueOf(1000));
            case "M":  return value.multiply(BigDecimal.valueOf(1000 * 1000));
            case "G":  return value.multiply(BigDecimal.valueOf(1000L * 1000L * 1000L));
            case "T":  return value.multiply(BigDecimal.valueOf(1000L * 1000L * 1000L * 1000L));
            default:
                throw new IllegalArgumentException("Unknown memory unit suffix: " + suffix);
        }
    }
}
