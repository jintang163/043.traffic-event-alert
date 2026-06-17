package com.traffic.alert.vo;

import com.traffic.alert.entity.PtzCruise;
import com.traffic.alert.entity.PtzCruisePoint;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class PtzCruiseVO extends PtzCruise {
    private List<PtzCruisePoint> points;
}
