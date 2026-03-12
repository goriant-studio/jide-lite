package com.goriant.jidelite.editor;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects missing-import compile errors and suggests {@code import} statements.
 */
public class ImportSuggester {

    /** Patterns that capture a simple type name from common compiler error messages. */
    private static final Pattern[] ERROR_PATTERNS = {
            // Janino: 'Identifier "Scanner" could not be resolved'
            Pattern.compile("(?i)Identifier\\s+[\"']?(\\w+)[\"']?\\s+could not be resolved"),
            // javac: 'cannot find symbol'  →  'symbol:   class Scanner'
            Pattern.compile("(?i)symbol\\s*:\\s*(?:class|variable|method)?\\s*(\\w+)"),
            // javac: 'error: cannot find symbol'  →  'Scanner sc'
            Pattern.compile("(?i)cannot find symbol.*?\\b([A-Z]\\w+)"),
    };

    private static final Pattern PACKAGE_PATTERN = Pattern.compile("(?m)^\\s*package\\s+[\\w.]+\\s*;");
    private static final Pattern IMPORT_PATTERN = Pattern.compile("(?m)^\\s*import\\s+");

    private final Map<String, String> knownTypes;

    public ImportSuggester() {
        knownTypes = buildKnownTypes();
    }

    /** Result of a successful import suggestion. */
    public static final class Suggestion {
        public final String simpleName;
        public final String qualifiedName;
        public final String importStatement;

        Suggestion(String simpleName, String qualifiedName) {
            this.simpleName = simpleName;
            this.qualifiedName = qualifiedName;
            this.importStatement = "import " + qualifiedName + ";";
        }
    }

    /**
     * Parse compiler output to find a missing type and suggest an import.
     *
     * @return a {@link Suggestion} if a known type is detected, otherwise {@code null}.
     */
    public Suggestion suggestImport(String errorText) {
        if (errorText == null || errorText.isBlank()) {
            return null;
        }
        for (Pattern pattern : ERROR_PATTERNS) {
            Matcher matcher = pattern.matcher(errorText);
            if (matcher.find()) {
                String simpleName = matcher.group(1);
                String qualifiedName = knownTypes.get(simpleName);
                if (qualifiedName != null) {
                    return new Suggestion(simpleName, qualifiedName);
                }
            }
        }
        return null;
    }

    /**
     * Inserts an import statement into Java source code, placing it after the
     * package declaration (or at the top if absent), before the first class.
     * Returns the original source unchanged if the import already exists.
     */
    public String insertImport(String sourceCode, String importStatement) {
        if (sourceCode == null || importStatement == null) {
            return sourceCode;
        }
        // Check for duplicate.
        if (sourceCode.contains(importStatement)) {
            return sourceCode;
        }

        String lineToInsert = importStatement + "\n";
        Matcher packageMatcher = PACKAGE_PATTERN.matcher(sourceCode);
        if (packageMatcher.find()) {
            int insertPos = packageMatcher.end();
            // Skip whitespace/newlines after the package statement.
            while (insertPos < sourceCode.length() && Character.isWhitespace(sourceCode.charAt(insertPos))) {
                insertPos++;
            }
            return sourceCode.substring(0, insertPos) + lineToInsert + "\n" + sourceCode.substring(insertPos);
        }

        // No package — insert before the first import or at the top.
        Matcher importMatcher = IMPORT_PATTERN.matcher(sourceCode);
        if (importMatcher.find()) {
            int insertPos = importMatcher.start();
            return sourceCode.substring(0, insertPos) + lineToInsert + sourceCode.substring(insertPos);
        }

        // No package, no imports — insert at the very top.
        return lineToInsert + "\n" + sourceCode;
    }

    private static Map<String, String> buildKnownTypes() {
        Map<String, String> types = new HashMap<>();

        // java.util
        types.put("ArrayList", "java.util.ArrayList");
        types.put("Arrays", "java.util.Arrays");
        types.put("Collection", "java.util.Collection");
        types.put("Collections", "java.util.Collections");
        types.put("Comparator", "java.util.Comparator");
        types.put("Deque", "java.util.Deque");
        types.put("HashMap", "java.util.HashMap");
        types.put("HashSet", "java.util.HashSet");
        types.put("Iterator", "java.util.Iterator");
        types.put("LinkedHashMap", "java.util.LinkedHashMap");
        types.put("LinkedHashSet", "java.util.LinkedHashSet");
        types.put("LinkedList", "java.util.LinkedList");
        types.put("List", "java.util.List");
        types.put("Map", "java.util.Map");
        types.put("Objects", "java.util.Objects");
        types.put("Optional", "java.util.Optional");
        types.put("PriorityQueue", "java.util.PriorityQueue");
        types.put("Queue", "java.util.Queue");
        types.put("Random", "java.util.Random");
        types.put("Scanner", "java.util.Scanner");
        types.put("Set", "java.util.Set");
        types.put("Stack", "java.util.Stack");
        types.put("TreeMap", "java.util.TreeMap");
        types.put("TreeSet", "java.util.TreeSet");
        types.put("Vector", "java.util.Vector");
        types.put("Date", "java.util.Date");
        types.put("Calendar", "java.util.Calendar");
        types.put("UUID", "java.util.UUID");
        types.put("Properties", "java.util.Properties");
        types.put("StringTokenizer", "java.util.StringTokenizer");
        types.put("Locale", "java.util.Locale");

        // java.util.stream
        types.put("Stream", "java.util.stream.Stream");
        types.put("Collectors", "java.util.stream.Collectors");
        types.put("IntStream", "java.util.stream.IntStream");

        // java.util.function
        types.put("Function", "java.util.function.Function");
        types.put("Predicate", "java.util.function.Predicate");
        types.put("Consumer", "java.util.function.Consumer");
        types.put("Supplier", "java.util.function.Supplier");
        types.put("BiFunction", "java.util.function.BiFunction");

        // java.util.concurrent
        types.put("ExecutorService", "java.util.concurrent.ExecutorService");
        types.put("Executors", "java.util.concurrent.Executors");
        types.put("Future", "java.util.concurrent.Future");
        types.put("CountDownLatch", "java.util.concurrent.CountDownLatch");
        types.put("ConcurrentHashMap", "java.util.concurrent.ConcurrentHashMap");
        types.put("AtomicInteger", "java.util.concurrent.atomic.AtomicInteger");

        // java.io
        types.put("BufferedReader", "java.io.BufferedReader");
        types.put("BufferedWriter", "java.io.BufferedWriter");
        types.put("File", "java.io.File");
        types.put("FileInputStream", "java.io.FileInputStream");
        types.put("FileOutputStream", "java.io.FileOutputStream");
        types.put("FileReader", "java.io.FileReader");
        types.put("FileWriter", "java.io.FileWriter");
        types.put("IOException", "java.io.IOException");
        types.put("InputStream", "java.io.InputStream");
        types.put("InputStreamReader", "java.io.InputStreamReader");
        types.put("OutputStream", "java.io.OutputStream");
        types.put("OutputStreamWriter", "java.io.OutputStreamWriter");
        types.put("PrintStream", "java.io.PrintStream");
        types.put("PrintWriter", "java.io.PrintWriter");
        types.put("Reader", "java.io.Reader");
        types.put("Serializable", "java.io.Serializable");
        types.put("Writer", "java.io.Writer");

        // java.nio
        types.put("Path", "java.nio.file.Path");
        types.put("Paths", "java.nio.file.Paths");
        types.put("Files", "java.nio.file.Files");
        types.put("ByteBuffer", "java.nio.ByteBuffer");
        types.put("StandardCharsets", "java.nio.charset.StandardCharsets");

        // java.math
        types.put("BigDecimal", "java.math.BigDecimal");
        types.put("BigInteger", "java.math.BigInteger");

        // java.time
        types.put("LocalDate", "java.time.LocalDate");
        types.put("LocalDateTime", "java.time.LocalDateTime");
        types.put("LocalTime", "java.time.LocalTime");
        types.put("Instant", "java.time.Instant");
        types.put("Duration", "java.time.Duration");
        types.put("ZonedDateTime", "java.time.ZonedDateTime");
        types.put("DateTimeFormatter", "java.time.format.DateTimeFormatter");

        // java.text
        types.put("SimpleDateFormat", "java.text.SimpleDateFormat");
        types.put("DecimalFormat", "java.text.DecimalFormat");
        types.put("NumberFormat", "java.text.NumberFormat");

        // java.net
        types.put("URL", "java.net.URL");
        types.put("URI", "java.net.URI");
        types.put("HttpURLConnection", "java.net.HttpURLConnection");
        types.put("Socket", "java.net.Socket");
        types.put("ServerSocket", "java.net.ServerSocket");

        // java.util.regex
        types.put("Pattern", "java.util.regex.Pattern");
        types.put("Matcher", "java.util.regex.Matcher");

        return types;
    }
}
