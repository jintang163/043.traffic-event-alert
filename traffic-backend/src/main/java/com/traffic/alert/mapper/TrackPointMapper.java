package com.traffic.alert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.traffic.alert.entity.TrackPoint;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TrackPointMapper extends BaseMapper<TrackPoint> {

    @Insert("<script>" +
            "INSERT INTO track_point (track_id, camera_id, camera_name, frame_no, frame_time, " +
            "bbox_x1, bbox_y1, bbox_x2, bbox_y2, bbox_confidence, " +
            "longitude, latitude, geom, pixel_x, pixel_y, geom_pixel, " +
            "velocity_x, velocity_y, speed, direction, reid_feature, snapshot_url, " +
            "is_key_point, key_point_type, create_time) VALUES " +
            "(#{p.trackId}, #{p.cameraId}, #{p.cameraName}, #{p.frameNo}, #{p.frameTime}, " +
            "#{p.bboxX1}, #{p.bboxY1}, #{p.bboxX2}, #{p.bboxY2}, #{p.bboxConfidence}, " +
            "#{p.longitude}, #{p.latitude}, " +
            "<if test='p.longitude != null and p.latitude != null'>" +
            "ST_GeometryFromText(CONCAT('POINT(', #{p.longitude}, ' ', #{p.latitude}, ')'), 4326), " +
            "</if>" +
            "<if test='p.longitude == null or p.latitude == null'>NULL, </if>" +
            "#{p.pixelX}, #{p.pixelY}, " +
            "<if test='p.pixelX != null and p.pixelY != null'>" +
            "ST_GeometryFromText(CONCAT('POINT(', #{p.pixelX}, ' ', #{p.pixelY}, ')')), " +
            "</if>" +
            "<if test='p.pixelX == null or p.pixelY == null'>NULL, </if>" +
            "#{p.velocityX}, #{p.velocityY}, #{p.speed}, #{p.direction}, #{p.reidFeature}, #{p.snapshotUrl}, " +
            "#{p.isKeyPoint}, #{p.keyPointType}, NOW())" +
            "</script>")
    int insertWithGeom(@Param("p") TrackPoint point);

    @Select("SELECT tp.*, " +
            "ST_AsText(tp.geom) as geomWkt, " +
            "ST_AsText(tp.geom_pixel) as geomPixelWkt " +
            "FROM track_point tp " +
            "WHERE tp.track_id = #{trackId} " +
            "AND tp.frame_time BETWEEN #{startTime} AND #{endTime} " +
            "AND tp.deleted = 0 " +
            "ORDER BY tp.frame_time ASC")
    List<TrackPoint> selectByTrackAndTimeRange(@Param("trackId") Long trackId,
                                               @Param("startTime") LocalDateTime startTime,
                                               @Param("endTime") LocalDateTime endTime);

    @Select("SELECT tp.*, " +
            "ST_AsText(tp.geom) as geomWkt, " +
            "ST_AsText(tp.geom_pixel) as geomPixelWkt " +
            "FROM track_point tp " +
            "WHERE tp.track_id = #{trackId} AND tp.deleted = 0 " +
            "ORDER BY tp.frame_time ASC")
    List<TrackPoint> selectAllByTrackId(@Param("trackId") Long trackId);

    @Select("<script>" +
            "SELECT tp.*, " +
            "ST_AsText(tp.geom) as geomWkt, " +
            "ST_AsText(tp.geom_pixel) as geomPixelWkt " +
            "FROM track_point tp " +
            "WHERE tp.deleted = 0 " +
            "AND MBRContains(ST_GeometryFromText(CONCAT('POLYGON((', " +
            "#{minLng}, ' ', #{minLat}, ', ', " +
            "#{maxLng}, ' ', #{minLat}, ', " +
            "#{maxLng}, ' ', #{maxLat}, ', " +
            "#{minLng}, ' ', #{maxLat}, ', " +
            "#{minLng}, ' ', #{minLat}, '))'), 4326), tp.geom) " +
            "<if test='startTime != null'>AND tp.frame_time &gt;= #{startTime}</if> " +
            "<if test='endTime != null'>AND tp.frame_time &lt;= #{endTime}</if> " +
            "ORDER BY tp.frame_time ASC" +
            "</script>")
    List<TrackPoint> selectBySpatialRange(@Param("minLng") BigDecimal minLng,
                                          @Param("minLat") BigDecimal minLat,
                                          @Param("maxLng") BigDecimal maxLng,
                                          @Param("maxLat") BigDecimal maxLat,
                                          @Param("startTime") LocalDateTime startTime,
                                          @Param("endTime") LocalDateTime endTime);
}
