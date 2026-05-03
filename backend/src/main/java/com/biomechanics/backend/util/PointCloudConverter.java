package com.biomechanics.backend.util;

import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Slf4j
public class PointCloudConverter {

    private PointCloudConverter() {
    }

    public static byte[] pointsToCompressedPly(List<List<Double>> points) {
        if (points == null || points.isEmpty()) {
            log.warn("Point cloud is null or empty - returning null");
            return null;
        }

        try {
            byte[] plyBytes = generateBinaryPly(points);
            log.debug("Generated binary PLY: {} points, {} KB", points.size(), plyBytes.length / 1024);

            byte[] compressed = gzipCompress(plyBytes);
            double ratio = (1.0 - (double) compressed.length / plyBytes.length) * 100;
            log.info("Point cloud compressed: {} KB -> {} KB ({}% reduction)",
                    plyBytes.length / 1024, compressed.length / 1024, String.format("%.1f", ratio));

            return compressed;

        } catch (IOException e) {
            log.error("Failed to convert points to compressed PLY: {}", e.getMessage());
            return null;
        }
    }

    public static byte[] decompressPly(byte[] compressed) throws IOException {
        if (compressed == null || compressed.length == 0) {
            return null;
        }
        return gzipDecompress(compressed);
    }


    private static byte[] generateBinaryPly(List<List<Double>> points) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        String header = "ply\n" +
                "format binary_little_endian 1.0\n" +
                "element vertex " + points.size() + "\n" +
                "property float x\n" +
                "property float y\n" +
                "property float z\n" +
                "end_header\n";
        out.write(header.getBytes(StandardCharsets.US_ASCII));

        ByteBuffer buffer = ByteBuffer.allocate(points.size() * 12).order(ByteOrder.LITTLE_ENDIAN);
        for (List<Double> point : points) {
            if (point.size() < 3) continue;
            buffer.putFloat(point.get(0).floatValue());
            buffer.putFloat(point.get(1).floatValue());
            buffer.putFloat(point.get(2).floatValue());
        }
        out.write(buffer.array());

        return out.toByteArray();
    }

    private static byte[] gzipCompress(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            gzip.write(data);
        }
        return baos.toByteArray();
    }

    private static byte[] gzipDecompress(byte[] compressed) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPInputStream gzip = new GZIPInputStream(new java.io.ByteArrayInputStream(compressed))) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = gzip.read(buf)) > 0) {
                baos.write(buf, 0, len);
            }
        }
        return baos.toByteArray();
    }
}