import math
from typing import List, Tuple, Optional


class Point:
    def __init__(self, x: float, y: float):
        self.x = x
        self.y = y

    def __repr__(self):
        return f"Point({self.x}, {self.y})"


class GeoPoint:
    def __init__(self, lng: float, lat: float):
        self.lng = lng
        self.lat = lat

    def __repr__(self):
        return f"GeoPoint({self.lng}, {self.lat})"


def point_in_polygon(point: Point, polygon: List[Point]) -> bool:
    if not polygon or len(polygon) < 3:
        return False

    n = len(polygon)
    inside = False

    for i in range(n):
        j = (i + 1) % n
        pi = polygon[i]
        pj = polygon[j]

        if ((pi.y > point.y) != (pj.y > point.y)) and \
                (point.x < (pj.x - pi.x) * (point.y - pi.y) / (pj.y - pi.y) + pi.x):
            inside = not inside

    return inside


def geo_point_in_polygon(point: GeoPoint, polygon: List[GeoPoint]) -> bool:
    points = [Point(p.lng, p.lat) for p in polygon]
    return point_in_polygon(Point(point.lng, point.lat), points)


def bbox_in_polygon(x1: float, y1: float, x2: float, y2: float,
                    polygon: List[Point], check_center: bool = True,
                    check_corners: bool = True) -> bool:
    if check_center:
        cx = (x1 + x2) / 2
        cy = (y1 + y2) / 2
        if point_in_polygon(Point(cx, cy), polygon):
            return True

    if check_corners:
        corners = [
            Point(x1, y1),
            Point(x2, y1),
            Point(x2, y2),
            Point(x1, y2),
        ]
        for corner in corners:
            if point_in_polygon(corner, polygon):
                return True

    return False


def bbox_overlap_polygon_ratio(x1: float, y1: float, x2: float, y2: float,
                               polygon: List[Point]) -> float:
    if not polygon or len(polygon) < 3:
        return 0.0

    min_poly_x = min(p.x for p in polygon)
    max_poly_x = max(p.x for p in polygon)
    min_poly_y = min(p.y for p in polygon)
    max_poly_y = max(p.y for p in polygon)

    if x2 < min_poly_x or x1 > max_poly_x or y2 < min_poly_y or y1 > max_poly_y:
        return 0.0

    cx = (x1 + x2) / 2
    cy = (y1 + y2) / 2
    if point_in_polygon(Point(cx, cy), polygon):
        return 1.0

    corners_inside = 0
    corners = [
        Point(x1, y1),
        Point(x2, y1),
        Point(x2, y2),
        Point(x1, y2),
    ]
    for corner in corners:
        if point_in_polygon(corner, polygon):
            corners_inside += 1

    return corners_inside / 4.0


def calculate_polygon_area(polygon: List[Point]) -> float:
    if not polygon or len(polygon) < 3:
        return 0.0

    area = 0.0
    n = len(polygon)

    for i in range(n):
        j = (i + 1) % n
        area += polygon[i].x * polygon[j].y - polygon[j].x * polygon[i].y

    return abs(area / 2.0)


def calculate_geo_polygon_area(polygon: List[GeoPoint]) -> float:
    if not polygon or len(polygon) < 3:
        return 0.0

    area = 0.0
    n = len(polygon)
    earth_radius = 6378137.0

    for i in range(n):
        j = (i + 1) % n
        p1 = polygon[i]
        p2 = polygon[j]

        lng1 = math.radians(p1.lng)
        lat1 = math.radians(p1.lat)
        lng2 = math.radians(p2.lng)
        lat2 = math.radians(p2.lat)

        area += (lng2 - lng1) * (2 + math.sin(lat1) + math.sin(lat2))

    return abs(area * earth_radius * earth_radius / 2.0)


def get_polygon_center(polygon: List[Point]) -> Point:
    if not polygon:
        return Point(0, 0)

    sum_x = sum(p.x for p in polygon)
    sum_y = sum(p.y for p in polygon)
    n = len(polygon)

    return Point(sum_x / n, sum_y / n)


def get_geo_polygon_center(polygon: List[GeoPoint]) -> GeoPoint:
    if not polygon:
        return GeoPoint(0, 0)

    sum_lng = sum(p.lng for p in polygon)
    sum_lat = sum(p.lat for p in polygon)
    n = len(polygon)

    return GeoPoint(sum_lng / n, sum_lat / n)


def haversine_distance(lng1: float, lat1: float, lng2: float, lat2: float) -> float:
    earth_radius = 6378137.0

    d_lat = math.radians(lat2 - lat1)
    d_lng = math.radians(lng2 - lng1)

    a = math.sin(d_lat / 2) ** 2 + \
        math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * \
        math.sin(d_lng / 2) ** 2

    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))

    return earth_radius * c


def parse_polygon_points(points_str: str) -> List[GeoPoint]:
    points = []
    if not points_str:
        return points

    try:
        import json
        coords = json.loads(points_str)
        if isinstance(coords, list):
            for coord in coords:
                if isinstance(coord, list) and len(coord) >= 2:
                    points.append(GeoPoint(float(coord[0]), float(coord[1])))
    except Exception:
        pass

    return points
