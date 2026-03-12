package com.goriant.jidelite.editor;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class BracketHelperTest {

    @Test
    public void testAutoCloseChar() {
        assertEquals('}', BracketHelper.autoCloseChar('{'));
        assertEquals(')', BracketHelper.autoCloseChar('('));
        assertEquals(']', BracketHelper.autoCloseChar('['));
        assertEquals('"', BracketHelper.autoCloseChar('"'));
        assertEquals('\'', BracketHelper.autoCloseChar('\''));
        assertEquals('\0', BracketHelper.autoCloseChar('a'));
    }

    @Test
    public void testShouldAutoClose() {
        String text = "public void test() { }";
        // End of file
        assertTrue(BracketHelper.shouldAutoClose(text, text.length(), '{'));
        // Before whitespace
        assertTrue(BracketHelper.shouldAutoClose(text, text.length() - 2, '{'));
        // Before a closing bracket
        assertTrue(BracketHelper.shouldAutoClose(text, text.length() - 1, '{'));
        
        // Before a letter -- should not auto-close
        assertFalse(BracketHelper.shouldAutoClose("public voi test() {}", 11, '('));

        // Inside a string -- should not auto close braces
        assertFalse(BracketHelper.shouldAutoClose("String s = \"test\";", 14, '{'));
        // But quotes inside a string? 
        assertFalse(BracketHelper.shouldAutoClose("String s = \"test\";", 14, '"'));
        
        // Quotes auto-close in empty context
        assertTrue(BracketHelper.shouldAutoClose("String s = ", 11, '"'));
    }

    @Test
    public void testFindMatchingBracket() {
        String code = "public void test() {\n  if (true) { \n    System.out.println(\"hello\");\n  }\n}";
        int openBracePos = code.indexOf('{');
        int closeBracePos = code.lastIndexOf('}');
        
        assertEquals(closeBracePos, BracketHelper.findMatchingBracket(code, openBracePos));
        
        int innerOpenBracePos = code.indexOf('{', openBracePos + 1);
        int innerCloseBracePos = code.indexOf('}', innerOpenBracePos + 1);
        
        assertEquals(innerCloseBracePos, BracketHelper.findMatchingBracket(code, innerOpenBracePos));
        
        // Match parentheses
        int openParen = code.indexOf('(');
        int closeParen = code.indexOf(')');
        assertEquals(closeParen, BracketHelper.findMatchingBracket(code, openParen));
    }

    @Test
    public void testIsBetweenMatchedPair() {
        assertTrue(BracketHelper.isBetweenMatchedPair("{}", 1));
        assertTrue(BracketHelper.isBetweenMatchedPair("()", 1));
        assertTrue(BracketHelper.isBetweenMatchedPair("[]", 1));
        assertTrue(BracketHelper.isBetweenMatchedPair("\"\"", 1));
        assertTrue(BracketHelper.isBetweenMatchedPair("''", 1));
        
        assertFalse(BracketHelper.isBetweenMatchedPair("{x}", 1));
        assertFalse(BracketHelper.isBetweenMatchedPair("{x}", 2));
    }
}
