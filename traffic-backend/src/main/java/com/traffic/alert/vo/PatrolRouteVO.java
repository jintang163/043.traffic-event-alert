package com.traffic.alert.dto;

import com.traffic.alert.entity.PatrolRoute;
import com.traffic.alert.entity.PatrolRoutePoint;
import lombok.Data;

import java.util.List;

@Data
public class PatrolRouteVO extends PatrolRoute {

    private List<PatrolRoutePoint> points;
}
