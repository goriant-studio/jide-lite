package com.goriant.jidelite.runner;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;

public class InteractiveInputStreamTest {

    @Test
    public void testReadAndSubmit() throws Exception {
        InteractiveInputStream in = new InteractiveInputStream();
        
        Thread reader = new Thread(() -> {
            try {
                int firstByte = in.read();
                assertEquals('T', firstByte);
                int secondByte = in.read();
                assertEquals('o', secondByte);
                int thirdByte = in.read();
                assertEquals('\n', thirdByte);
            } catch (IOException e) {
                fail("IOException thrown");
            }
        });
        
        reader.start();
        Thread.sleep(100); // give time for reader to block
        
        in.submitLine("To\n");
        reader.join();
        in.close();
    }

    @Test
    public void testCloseUnblocksRead() throws Exception {
        InteractiveInputStream in = new InteractiveInputStream();
        
        Thread reader = new Thread(() -> {
            try {
                int val = in.read();
                assertEquals(-1, val);
            } catch (IOException e) {
                fail("IOException thrown");
            }
        });
        
        reader.start();
        Thread.sleep(100);
        
        in.close();
        reader.join();
    }
}
