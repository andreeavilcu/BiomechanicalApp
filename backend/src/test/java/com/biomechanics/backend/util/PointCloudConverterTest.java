package com.biomechanics.backend.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("PointCloudConverter Tests")
class PointCloudConverterTest {

    @Nested
    @DisplayName("pointsToCompressedPly()")
    class PointsToCompressedPly {

        @Test
        @DisplayName("Null input returns null")
        void shouldReturnNullForNullPoints() {
            assertThat(PointCloudConverter.pointsToCompressedPly(null)).isNull();
        }

        @Test
        @DisplayName("Empty list returns null")
        void shouldReturnNullForEmptyList() {
            assertThat(PointCloudConverter.pointsToCompressedPly(Collections.emptyList())).isNull();
        }

        @Test
        @DisplayName("Valid points are successfully compressed")
        void shouldCompressValidPoints() {
            List<List<Double>> points = List.of(
                    List.of(1.0, 2.0, 3.0),
                    List.of(4.0, 5.0, 6.0),
                    List.of(7.0, 8.0, 9.0)
            );

            byte[] compressed = PointCloudConverter.pointsToCompressedPly(points);

            assertThat(compressed).isNotNull();
            assertThat(compressed.length).isGreaterThan(0);
        }

        @Test
        @DisplayName("Points with less than 3 coordinates are skipped without exception")
        void shouldSkipUndersizedPoints() {
            List<List<Double>> points = new ArrayList<>();
            points.add(List.of(1.0, 2.0, 3.0));
            points.add(List.of(4.0, 5.0));
            points.add(List.of(7.0, 8.0, 9.0));

            byte[] compressed = PointCloudConverter.pointsToCompressedPly(points);

            assertThat(compressed).isNotNull();
        }
    }

    @Nested
    @DisplayName("decompressPly()")
    class DecompressPly {

        @Test
        @DisplayName("Null input returns null")
        void shouldReturnNullForNullInput() throws IOException {
            assertThat(PointCloudConverter.decompressPly(null)).isNull();
        }

        @Test
        @DisplayName("Empty array returns null")
        void shouldReturnNullForEmptyArray() throws IOException {
            assertThat(PointCloudConverter.decompressPly(new byte[0])).isNull();
        }

        @Test
        @DisplayName("Correctly compressed data is decompressed and contains PLY header")
        void shouldDecompressAndContainPlyHeader() throws IOException {
            List<List<Double>> points = List.of(
                    List.of(0.1, 0.2, 0.3),
                    List.of(1.0, 2.0, 3.0)
            );

            byte[] compressed = PointCloudConverter.pointsToCompressedPly(points);
            assertThat(compressed).isNotNull();

            byte[] decompressed = PointCloudConverter.decompressPly(compressed);

            assertThat(decompressed).isNotNull();
            assertThat(new String(decompressed, StandardCharsets.US_ASCII)).startsWith("ply\n");
        }

        @Test
        @DisplayName("Invalid data (non-gzip) throws IOException")
        void shouldThrowForInvalidGzipData() {
            byte[] invalid = new byte[]{1, 2, 3, 4, 5};

            assertThatThrownBy(() -> PointCloudConverter.decompressPly(invalid))
                    .isInstanceOf(IOException.class);
        }
    }

    @Nested
    @DisplayName("Round-trip compress/decompress")
    class RoundTrip {

        @Test
        @DisplayName("Compression followed by decompression reproduces PLY header with correct point count")
        void shouldProduceCorrectVertexCount() throws IOException {
            List<List<Double>> points = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                points.add(List.of((double) i, (double) i * 2, (double) i * 3));
            }

            byte[] compressed = PointCloudConverter.pointsToCompressedPly(points);
            byte[] decompressed = PointCloudConverter.decompressPly(compressed);
            String header = new String(decompressed, StandardCharsets.US_ASCII);

            assertThat(header).contains("element vertex 10");
        }
    }
}
