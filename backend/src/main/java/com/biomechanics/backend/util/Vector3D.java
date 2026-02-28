package com.biomechanics.backend.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class Vector3D {
    private double x;
    private double y;
    private double z;

    public Vector3D(BigDecimal x, BigDecimal y, BigDecimal z) {
        this.x = x != null ? x.doubleValue() : 0.0;
        this.y = y != null ? y.doubleValue() : 0.0;
        this.z = z != null ? z.doubleValue() : 0.0;
    }

    public static Vector3D fromPoints(Vector3D pointA, Vector3D pointB) {
        return new Vector3D(
                pointB.x - pointA.x,
                pointB.y - pointA.y,
                pointB.z - pointA.z
        );
    }

    public double magnitude() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    public Vector3D normalize() {
        double mag = magnitude();
        if (mag == 0) {
            return new Vector3D(0, 0, 0);
        }
        return new Vector3D(x / mag, y / mag, z / mag);
    }

    public double dot(Vector3D other) {
        return this.x * other.x + this.y * other.y + this.z * other.z;
    }

    public double angleDegrees(Vector3D other) {
        Vector3D v1Norm = this.normalize();
        Vector3D v2Norm = other.normalize();

        double dotProduct = v1Norm.dot(v2Norm);

        dotProduct = Math.max(-1.0, Math.min(1.0, dotProduct));

        double angleRadians = Math.acos(dotProduct);
        return Math.toDegrees(angleRadians);
    }


    public double distanceTo(Vector3D other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        double dz = this.z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }


    public double horizontalDistanceTo(Vector3D other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public double angleFromVertical() {
        Vector3D vertical = new Vector3D(0, 0, 1); // Axa Z (sus)
        return this.angleDegrees(vertical);
    }

    public BigDecimal toBigDecimal(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }

    @Override
    public String toString() {
        return String.format("Vector3D(%.4f, %.4f, %.4f)", x, y, z);
    }
}
