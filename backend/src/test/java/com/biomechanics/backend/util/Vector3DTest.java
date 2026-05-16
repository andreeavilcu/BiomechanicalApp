package com.biomechanics.backend.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Vector3D Tests")
class Vector3DTest {

    private static final double EPSILON = 1e-6;

    @Nested
    @DisplayName("magnitude()")
    class Magnitude {

        @Test
        @DisplayName("Zero vector has magnitude 0")
        void zeroVectorHasMagnitudeZero() {
            assertThat(new Vector3D(0, 0, 0).magnitude()).isCloseTo(0.0, within(EPSILON));
        }

        @Test
        @DisplayName("Unit vector on X has magnitude 1")
        void unitXVectorHasMagnitudeOne() {
            assertThat(new Vector3D(1, 0, 0).magnitude()).isCloseTo(1.0, within(EPSILON));
        }

        @Test
        @DisplayName("Vector (3, 4, 0) has magnitude 5")
        void pythagorean345() {
            assertThat(new Vector3D(3, 4, 0).magnitude()).isCloseTo(5.0, within(EPSILON));
        }

        @Test
        @DisplayName("Magnitude is positive for negative components")
        void negativeComponentsStillPositiveMagnitude() {
            assertThat(new Vector3D(-3, -4, 0).magnitude()).isCloseTo(5.0, within(EPSILON));
        }
    }

    @Nested
    @DisplayName("normalize()")
    class Normalize {

        @Test
        @DisplayName("Normalized vector has magnitude 1")
        void normalizedVectorHasMagnitudeOne() {
            Vector3D v = new Vector3D(3, 4, 0).normalize();
            assertThat(v.magnitude()).isCloseTo(1.0, within(EPSILON));
        }

        @Test
        @DisplayName("Normalized zero vector remains zero (does not throw exception)")
        void normalizeZeroVectorReturnsZero() {
            Vector3D result = new Vector3D(0, 0, 0).normalize();
            assertThat(result.magnitude()).isCloseTo(0.0, within(EPSILON));
        }

        @Test
        @DisplayName("Direction is preserved after normalization")
        void directionPreservedAfterNormalization() {
            Vector3D v = new Vector3D(0, 5, 0).normalize();
            assertThat(v.getX()).isCloseTo(0.0, within(EPSILON));
            assertThat(v.getY()).isCloseTo(1.0, within(EPSILON));
            assertThat(v.getZ()).isCloseTo(0.0, within(EPSILON));
        }
    }

    @Nested
    @DisplayName("dot()")
    class Dot {

        @Test
        @DisplayName("Dot product of perpendicular vectors is 0")
        void perpendicularVectorsHaveZeroDot() {
            Vector3D v1 = new Vector3D(1, 0, 0);
            Vector3D v2 = new Vector3D(0, 1, 0);
            assertThat(v1.dot(v2)).isCloseTo(0.0, within(EPSILON));
        }

        @Test
        @DisplayName("Dot product of the same vector is the squared magnitude")
        void selfDotEqualsSquaredMagnitude() {
            Vector3D v = new Vector3D(3, 4, 0);
            assertThat(v.dot(v)).isCloseTo(25.0, within(EPSILON));
        }

        @Test
        @DisplayName("Dot product is commutative")
        void dotProductIsCommutative() {
            Vector3D v1 = new Vector3D(1, 2, 3);
            Vector3D v2 = new Vector3D(4, 5, 6);
            assertThat(v1.dot(v2)).isCloseTo(v2.dot(v1), within(EPSILON));
        }
    }

    @Nested
    @DisplayName("angleDegrees()")
    class AngleDegrees {

        @Test
        @DisplayName("Parallel vectors have 0 degrees angle")
        void parallelVectorsHaveZeroAngle() {
            Vector3D v1 = new Vector3D(1, 0, 0);
            Vector3D v2 = new Vector3D(5, 0, 0);
            assertThat(v1.angleDegrees(v2)).isCloseTo(0.0, within(EPSILON));
        }

        @Test
        @DisplayName("Perpendicular vectors have 90 degrees angle")
        void perpendicularVectorsHave90Degrees() {
            Vector3D v1 = new Vector3D(1, 0, 0);
            Vector3D v2 = new Vector3D(0, 1, 0);
            assertThat(v1.angleDegrees(v2)).isCloseTo(90.0, within(EPSILON));
        }

        @Test
        @DisplayName("Opposite vectors have 180 degrees angle")
        void oppositeVectorsHave180Degrees() {
            Vector3D v1 = new Vector3D(1, 0, 0);
            Vector3D v2 = new Vector3D(-1, 0, 0);
            assertThat(v1.angleDegrees(v2)).isCloseTo(180.0, within(EPSILON));
        }

        @Test
        @DisplayName("Angle is always between 0 and 180 degrees")
        void angleIsBetweenZeroAnd180() {
            Vector3D v1 = new Vector3D(1, 2, 3);
            Vector3D v2 = new Vector3D(-4, 5, -6);
            double angle = v1.angleDegrees(v2);
            assertThat(angle).isBetween(0.0, 180.0);
        }
    }

    @Nested
    @DisplayName("fromPoints()")
    class FromPoints {

        @Test
        @DisplayName("Vector from A to B is B - A")
        void shouldCalculateDifference() {
            Vector3D a = new Vector3D(1, 2, 3);
            Vector3D b = new Vector3D(4, 6, 8);
            Vector3D result = Vector3D.fromPoints(a, b);

            assertThat(result.getX()).isCloseTo(3.0, within(EPSILON));
            assertThat(result.getY()).isCloseTo(4.0, within(EPSILON));
            assertThat(result.getZ()).isCloseTo(5.0, within(EPSILON));
        }

        @Test
        @DisplayName("fromPoints(A, A) returns the zero vector")
        void samePointReturnsZeroVector() {
            Vector3D a = new Vector3D(3, 5, 7);
            Vector3D result = Vector3D.fromPoints(a, a);

            assertThat(result.getX()).isCloseTo(0.0, within(EPSILON));
            assertThat(result.getY()).isCloseTo(0.0, within(EPSILON));
            assertThat(result.getZ()).isCloseTo(0.0, within(EPSILON));
        }
    }

    @Nested
    @DisplayName("distanceTo()")
    class DistanceTo {

        @Test
        @DisplayName("Distance to the same point is 0")
        void distanceToSamePointIsZero() {
            Vector3D v = new Vector3D(1, 2, 3);
            assertThat(v.distanceTo(v)).isCloseTo(0.0, within(EPSILON));
        }

        @Test
        @DisplayName("Distance from (0,0,0) to (3,4,0) is 5")
        void pythagorean345Distance() {
            Vector3D origin = new Vector3D(0, 0, 0);
            Vector3D point  = new Vector3D(3, 4, 0);
            assertThat(origin.distanceTo(point)).isCloseTo(5.0, within(EPSILON));
        }

        @Test
        @DisplayName("Distance is symmetric")
        void distanceIsSymmetric() {
            Vector3D v1 = new Vector3D(1, 2, 3);
            Vector3D v2 = new Vector3D(4, 6, 8);
            assertThat(v1.distanceTo(v2)).isCloseTo(v2.distanceTo(v1), within(EPSILON));
        }
    }

    @Nested
    @DisplayName("horizontalDistanceTo()")
    class HorizontalDistance {

        @Test
        @DisplayName("Ignores Z component and calculates 2D distance")
        void shouldIgnoreZComponent() {
            Vector3D v1 = new Vector3D(0, 0, 100);
            Vector3D v2 = new Vector3D(3, 4, 200);
            assertThat(v1.horizontalDistanceTo(v2)).isCloseTo(5.0, within(EPSILON));
        }

        @Test
        @DisplayName("Points with same X and Y but different Z have zero horizontal distance")
        void sameXYDifferentZHasZeroHorizontalDistance() {
            Vector3D v1 = new Vector3D(5, 10, 0);
            Vector3D v2 = new Vector3D(5, 10, 50);
            assertThat(v1.horizontalDistanceTo(v2)).isCloseTo(0.0, within(EPSILON));
        }
    }

    @Nested
    @DisplayName("angleFromVertical()")
    class AngleFromVertical {

        @Test
        @DisplayName("Vertical vector (Z+) has 0 degrees angle from vertical")
        void verticalVectorHasZeroAngle() {
            Vector3D vertical = new Vector3D(0, 0, 1);
            assertThat(vertical.angleFromVertical()).isCloseTo(0.0, within(EPSILON));
        }

        @Test
        @DisplayName("Horizontal vector (X+) has 90 degrees angle from vertical")
        void horizontalVectorHas90Degrees() {
            Vector3D horizontal = new Vector3D(1, 0, 0);
            assertThat(horizontal.angleFromVertical()).isCloseTo(90.0, within(EPSILON));
        }
    }
}