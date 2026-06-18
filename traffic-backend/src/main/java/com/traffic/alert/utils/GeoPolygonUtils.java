package com.traffic.alert.utils;

import java.util.ArrayList;
import java.util.List;

public class GeoPolygonUtils {

    public static class Point {
        public double lng;
        public double lat;

        public Point(double lng, double lat) {
            this.lng = lng;
            this.lat = lat;
        }
    }

    public static boolean isPointInPolygon(Point point, List<Point> polygon) {
        if (polygon == null || polygon.size() < 3) {
            return false;
        }

        int n = polygon.size();
        boolean inside = false;

        for (int i = 0, j = n - 1; i < n; j = i++) {
            Point pi = polygon.get(i);
            Point pj = polygon.get(j);

            if (((pi.lat > point.lat) != (pj.lat > point.lat)) &&
                    (point.lng < (pj.lng - pi.lng) * (point.lat - pi.lat) / (pj.lat - pi.lat) + pi.lng)) {
                inside = !inside;
            }
        }

        return inside;
    }

    public static boolean isPointInPolygon(double lng, double lat, List<double[]> polygon) {
        List<Point> points = new ArrayList<>();
        for (double[] p : polygon) {
            points.add(new Point(p[0], p[1]));
        }
        return isPointInPolygon(new Point(lng, lat), points);
    }

    public static double calculatePolygonArea(List<Point> polygon) {
        if (polygon == null || polygon.size() < 3) {
            return 0;
        }

        double area = 0;
        int n = polygon.size();

        for (int i = 0; i < n; i++) {
            Point p1 = polygon.get(i);
            Point p2 = polygon.get((i + 1) % n);
            area += p1.lng * p2.lat - p2.lng * p1.lat;
        }

        return Math.abs(area / 2.0);
    }

    public static double calculatePolygonAreaSquareMeters(List<Point> polygon) {
        if (polygon == null || polygon.size() < 3) {
            return 0;
        }

        double area = 0;
        int n = polygon.size();
        double earthRadius = 6378137.0;

        for (int i = 0; i < n; i++) {
            Point p1 = polygon.get(i);
            Point p2 = polygon.get((i + 1) % n);

            double lng1 = Math.toRadians(p1.lng);
            double lat1 = Math.toRadians(p1.lat);
            double lng2 = Math.toRadians(p2.lng);
            double lat2 = Math.toRadians(p2.lat);

            area += (lng2 - lng1) * (2 + Math.sin(lat1) + Math.sin(lat2));
        }

        return Math.abs(area * earthRadius * earthRadius / 2.0);
    }

    public static Point getPolygonCenter(List<Point> polygon) {
        if (polygon == null || polygon.isEmpty()) {
            return new Point(0, 0);
        }

        double sumLng = 0;
        double sumLat = 0;
        int n = polygon.size();

        for (Point p : polygon) {
            sumLng += p.lng;
            sumLat += p.lat;
        }

        return new Point(sumLng / n, sumLat / n);
    }

    public static List<Point> parsePolygonPoints(String pointsJson) {
        List<Point> points = new ArrayList<>();
        if (pointsJson == null || pointsJson.isEmpty()) {
            return points;
        }

        try {
            String clean = pointsJson.trim();
            if (clean.startsWith("[") && clean.endsWith("]")) {
                clean = clean.substring(1, clean.length() - 1);
                String[] pointStrs = clean.split("\\],\\[");
                for (String ps : pointStrs) {
                    ps = ps.replace("[", "").replace("]", "").trim();
                    String[] coords = ps.split(",");
                    if (coords.length >= 2) {
                        double lng = Double.parseDouble(coords[0].trim());
                        double lat = Double.parseDouble(coords[1].trim());
                        points.add(new Point(lng, lat));
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }

        return points;
    }

    public static double distanceMeters(double lng1, double lat1, double lng2, double lat2) {
        double earthRadius = 6378137.0;

        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return earthRadius * c;
    }
}
