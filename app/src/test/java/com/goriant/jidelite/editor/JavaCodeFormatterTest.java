package com.goriant.jidelite.editor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class JavaCodeFormatterTest {

    private final JavaCodeFormatter formatter = new JavaCodeFormatter();

    @Test
    void formatExpandsSingleLineClassIntoIndentedBlocks() {
        String formatted = formatter.format(
                "public class Main{public static void main(String[] args){System.out.println(\"Hi\");}}"
        );

        assertThat(formatted).isEqualTo(
                "public class Main {\n"
                        + "    public static void main(String[] args) {\n"
                        + "        System.out.println(\"Hi\");\n"
                        + "    }\n"
                        + "}"
        );
    }

    @Test
    void formatPreservesCommentsStringsAndForHeader() {
        String formatted = formatter.format(
                "class Demo{void run(){for(int i=0;i<2;i++){// tick\n"
                        + "System.out.println(\"{\"+i);\n"
                        + "/* keep { } */\n"
                        + "}}}"
        );

        assertThat(formatted).isEqualTo(
                "class Demo {\n"
                        + "    void run() {\n"
                        + "        for(int i=0; i<2; i++) {\n"
                        + "            // tick\n"
                        + "            System.out.println(\"{\"+i);\n"
                        + "            /* keep { } */\n"
                        + "        }\n"
                        + "    }\n"
                        + "}"
        );
    }

    @Test
    void formatNormalizesWindowsLineEndingsAndTrailingWhitespace() {
        String formatted = formatter.format(
                "class Demo {\r\n"
                        + "    void run() {   \r\n"
                        + "        System.out.println(\"x\");   \r\n"
                        + "    }\r\n"
                        + "}\r\n"
        );

        assertThat(formatted).isEqualTo(
                "class Demo {\n"
                        + "    void run() {\n"
                        + "        System.out.println(\"x\");\n"
                        + "    }\n"
                        + "}"
        );
    }

    @Test
    void formatReturnsEmptyStringForBlankInput() {
        assertThat(formatter.format(" \n\t ")).isEmpty();
    }
}
