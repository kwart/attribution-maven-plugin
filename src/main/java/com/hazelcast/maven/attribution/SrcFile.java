package com.hazelcast.maven.attribution;

/**
 * Simple holder object for source file content.
 */
public class SrcFile {
    private final String gav;
    private final String sourceName;
    private final byte[] bytes;

    public SrcFile(String gav, String sourceName, byte[] bytes) {
        this.gav = gav;
        this.sourceName = sourceName;
        this.bytes = bytes;
    }

    public String getGav() {
        return gav;
    }

    public String getSourceName() {
        return sourceName;
    }

    public byte[] getBytes() {
        return bytes;
    }
}
