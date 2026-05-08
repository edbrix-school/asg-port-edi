package com.asg.portediintegration.utils;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Slf4j
public class ChecksumUtils {

    /**
     * Computes SHA-256 checksum of the given InputStream while optionally writing to OutputStream.
     * This allows streaming calculation without loading the entire file into memory.
     *
     * @param inputStream  The source input stream
     * @param outputStream Optional output stream to write the bytes (can be null)
     * @return SHA-256 checksum as a hex string
     * @throws IOException
     * @throws NoSuchAlgorithmException
     */
    public static String calculateSHA256(InputStream inputStream, OutputStream outputStream)
            throws IOException, NoSuchAlgorithmException {

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        int bytesRead;

        while ((bytesRead = inputStream.read(buffer)) != -1) {
            digest.update(buffer, 0, bytesRead);
            if (outputStream != null) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }

        byte[] checksumBytes = digest.digest();
        return bytesToHex(checksumBytes);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
