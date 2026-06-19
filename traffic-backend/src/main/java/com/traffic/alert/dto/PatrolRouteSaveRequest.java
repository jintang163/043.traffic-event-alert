package com.traffic.alert.dto;

import lombok.Data;

import java.util.List;

@Data
public class PatrolRouteSaveRequest {

    private Long id;

    private String routeName;

    private String routeCode;

    private String description;

    private Integer status;

    private Integer staySeconds;

    private Integer loopMode;

    private List<PatrolRoutePointDTO> points;

    @Data
    public static class PatrolRoutePointDTO {
        private Long cameraId;
        private String cameraName;
        private String cameraCode;
        private Integer sortOrder;
        private Integer staySeconds;
        private Double longitude;
        private Double latitude;
        private String location;
    }
}
