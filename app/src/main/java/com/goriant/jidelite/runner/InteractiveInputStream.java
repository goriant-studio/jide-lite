package com.goriant.jidelite.runner;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A thread-safe {@link InputStream} that blocks on {@link #read()} until
 * data is submitted via {@link #submitLine(String)} or the stream is closed.
 * <p>
 * This is intended for use as {@code System.in} when executing user Java code
 * that reads from {@link java.util.Scanner}.
 */
public class InteractiveInputStream extends InputStream {

    private static final byte[] POISON = new byte[0];

    private final LinkedBlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();
    private volatile boolean closed = false;
    private byte[] currentBuffer = null;
    private int currentPosition = 0;

    /**
     * Submits a line of input (from the UI thread). The line is converted to
     * bytes using UTF-8 encoding and queued for consumption.
     */
    public void submitLine(String line) {
        if (closed) {
            return;
        }
        byte[] data = line.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        queue.offer(data);
    }

    @Override
    public int read() throws IOException {
        while (true) {
            if (currentBuffer != null && currentPosition < currentBuffer.length) {
                return currentBuffer[currentPosition++] & 0xFF;
            }
            if (closed) {
                return -1;
            }
            try {
                byte[] next = queue.take();
                if (next == POISON) {
                    return -1;
                }
                currentBuffer = next;
                currentPosition = 0;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return -1;
            }
        }
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (len == 0) {
            return 0;
        }
        int firstByte = read();
        if (firstByte == -1) {
            return -1;
        }
        b[off] = (byte) firstByte;
        int bytesRead = 1;
        while (bytesRead < len && currentBuffer != null && currentPosition < currentBuffer.length) {
            b[off + bytesRead] = currentBuffer[currentPosition++];
            bytesRead++;
        }
        return bytesRead;
    }

    @Override
    public int available() {
        if (currentBuffer != null && currentPosition < currentBuffer.length) {
            return currentBuffer.length - currentPosition;
        }
        return 0;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            queue.offer(POISON);
        }
    }
}
