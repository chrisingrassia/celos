package com.collective.celos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.UUID;

public class Util {

    public static <T> T requireNonNull(T object) {
        if (object == null) throw new NullPointerException();
        else return object;
    }

    // DATETIME UTILITIES
    
    public static DateTime toFullHour(DateTime dt) {
        return toFullMinute(dt).withMinuteOfHour(0);
    }

    public static DateTime toFullMinute(DateTime dt) {
        return toFullSecond(dt).withSecondOfMinute(0);
    }
    
    public static DateTime toFullSecond(DateTime dt) {
        return dt.withMillisOfSecond(0);
    }

    public static boolean isFullHour(DateTime dt) {
        return isFullMinute(dt) && dt.getMinuteOfHour() == 0;
    }
    
    public static boolean isFullMinute(DateTime dt) {
        return isFullSecond(dt) && dt.getSecondOfMinute() == 0;
    }
    
    public static boolean isFullSecond(DateTime dt) {
        return dt.getMillisOfSecond() == 0;
    }

    public static String toNominalTimeFormat(DateTime dt) {
        return dt.toString(DateTimeFormat.forPattern("YYYY-MM-dd'T'HH:mm'Z"));
    }

    // JSON UTILITIES
    
    public static ObjectNode newObjectNode() {
        return JsonNodeFactory.instance.objectNode();
    }
    
    public static ArrayNode newArrayNode() {
        return JsonNodeFactory.instance.arrayNode();
    }

    public static String getStringProperty(ObjectNode properties, String name) {
        JsonNode node = properties.get(name);
        if (node == null) {
            throw new IllegalArgumentException("Property " + name + " not set.");
        } else if (!node.isTextual()) {
            throw new IllegalArgumentException("Property " + name + " is not a string, but " + node);
        } else {
            return node.textValue();
        }
    }
    
    public static int getIntProperty(ObjectNode properties, String name) {
        JsonNode node = properties.get(name);
        if (node == null) {
            throw new IllegalArgumentException("Property " + name + " not set.");
        } else if (!node.isInt()) {
            throw new IllegalArgumentException("Property " + name + " is not an integer, but " + node);
        } else {
            return node.intValue();
        }
    }
    
    public static ArrayNode getArrayProperty(ObjectNode properties, String name) {
        JsonNode node = properties.get(name);
        if (node == null) {
            throw new IllegalArgumentException("Property " + name + " not set.");
        } else if (!node.isArray()) {
            throw new IllegalArgumentException("Property " + name + " is not an array, but " + node);
        } else {
            return (ArrayNode) node;
        }
    }

    public static ObjectNode getObjectProperty(ObjectNode properties, String name) {
        JsonNode node = properties.get(name);
        if (node == null) {
            throw new IllegalArgumentException("Property " + name + " not set.");
        } else if (!node.isObject()) {
            throw new IllegalArgumentException("Property " + name + " is not an object, but " + node);
        } else {
            return (ObjectNode) node;
        }
    }

    public static ScheduledTime max(ScheduledTime a, ScheduledTime b) {
        requireNonNull(a);
        requireNonNull(b);
        if (a.compareTo(b) <= 0) {
            return b;
        } else {
            return a;
        }
    }

    /**
     * we need those conversions to be able to parse hdfs URI strings which contain '$', '{' and '}' symbols
     */
    private static final Map<String, String> conversions = ImmutableMap.of(
            "$", Character.toString((char) 0xE000),
            "{", Character.toString((char) 0xE001),
            "}", Character.toString((char) 0xE002));

    private static final Map<String, String> backConversions;

    static {
        backConversions = Maps.newHashMap();
        for (Map.Entry<String, String> entry : conversions.entrySet()) {
            backConversions.put(entry.getValue(), entry.getKey());
        }
    }

    public static String augmentHdfsPath(String hdfsPrefix, String path) throws URISyntaxException {

        for (String ch : conversions.keySet()) {
            path = path.replace(ch.toString(), conversions.get(ch).toString());
        }
        URI oldUri = URI.create(path);

        String host = oldUri.getHost();
        if (oldUri.getRawSchemeSpecificPart().startsWith("///") && host == null) {
            host = "";
        }

        URI newUri = new URI(oldUri.getScheme(), oldUri.getUserInfo(), host, oldUri.getPort(), hdfsPrefix + oldUri.getPath(), oldUri.getQuery(), oldUri.getFragment());
        path = newUri.toString();
        for (String ch : backConversions.keySet()) {
            path = path.replace(ch.toString(), backConversions.get(ch).toString());
        }
        return path;
    }

    private final static String AUGMENTED_DB_NAME = "celosci_%s_%s";

    public static String augmentDbName(UUID uuid, String dbName) {
        return String.format(AUGMENTED_DB_NAME, dbName, uuid.toString().replace("-", "_"));
    }

}
