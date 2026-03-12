package com.goriant.jidelite.editor;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ImportSuggesterTest {

    @Test
    public void testSuggestImportFromError() {
        ImportSuggester suggester = new ImportSuggester();
        String error1 = "source.java:5: error: cannot find symbol\n  Scanner sc = new Scanner(System.in);\n  ^\n  symbol:   class Scanner\n  location: class Main";
        ImportSuggester.Suggestion s1 = suggester.suggestImport(error1);
        assertNotNull(s1);
        assertEquals("Scanner", s1.simpleName);
        assertEquals("java.util.Scanner", s1.qualifiedName);
        assertEquals("import java.util.Scanner;", s1.importStatement);

        String error2 = "source.java:2: error: cannot find symbol\nList<String> list = new ArrayList<>();\n^\n  symbol:   class List";
        ImportSuggester.Suggestion s2 = suggester.suggestImport(error2);
        assertNotNull(s2);
        assertEquals("List", s2.simpleName);
        assertEquals("java.util.List", s2.qualifiedName);

        String errorUnknown = "error: cannot find symbol: class MyCustomClass";
        assertNull(suggester.suggestImport(errorUnknown));
    }

    @Test
    public void testInsertImport() {
        ImportSuggester suggester = new ImportSuggester();
        String code = "public class Main {\n    public static void main(String[] args) {\n    }\n}";
        String importedCode = suggester.insertImport(code, "import java.util.Scanner;");
        assertEquals("import java.util.Scanner;\n\npublic class Main {\n    public static void main(String[] args) {\n    }\n}", importedCode);

        String codeWithPackage = "package com.example;\n\npublic class Main {}";
        String importedCodeWithPackage = suggester.insertImport(codeWithPackage, "import java.util.Scanner;");
        assertEquals("package com.example;\n\nimport java.util.Scanner;\n\npublic class Main {}", importedCodeWithPackage);
        
        // Prevent duplicates
        String existingImport = "import java.util.Scanner;\npublic class Main {}";
        String noDuplicate = suggester.insertImport(existingImport, "import java.util.Scanner;");
        assertEquals(existingImport, noDuplicate);
    }
}
