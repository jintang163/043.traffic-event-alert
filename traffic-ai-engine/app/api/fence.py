from typing import List, Optional

from fastapi import APIRouter, HTTPException, status

from app.core.fence_manager import fence_manager
from app.schemas.fence import FenceConfig

router = APIRouter()


@router.get("/", response_model=List[FenceConfig])
async def list_fences(camera_id: Optional[int] = None):
    if camera_id is not None:
        return fence_manager.get_fences_by_camera(camera_id)
    return fence_manager.get_all_fences()


@router.get("/{fence_id}", response_model=FenceConfig)
async def get_fence(fence_id: str):
    fence = fence_manager.get_fence(fence_id)
    if fence is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Fence {fence_id} not found"
        )
    return fence


@router.post("/", response_model=FenceConfig)
async def add_fence(fence: FenceConfig):
    fence_manager.add_fence(fence)
    return fence


@router.put("/{fence_id}", response_model=FenceConfig)
async def update_fence(fence_id: str, fence: FenceConfig):
    if fence_id != fence.fence_id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Fence ID mismatch"
        )
    fence_manager.update_fence(fence)
    return fence


@router.delete("/{fence_id}")
async def delete_fence(fence_id: str):
    fence = fence_manager.get_fence(fence_id)
    if fence is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Fence {fence_id} not found"
        )
    fence_manager.remove_fence(fence_id)
    return {"status": "success", "message": f"Fence {fence_id} deleted"}


@router.post("/batch-load")
async def batch_load_fences(fences: List[dict]):
    fence_manager.load_from_list(fences)
    return {
        "status": "success",
        "message": f"Loaded {len(fence_manager.get_all_fences())} fences",
        "count": len(fence_manager.get_all_fences())
    }


@router.get("/check/point")
async def check_point_in_fence(
    lng: float,
    lat: float,
    camera_id: Optional[int] = None
):
    intruded = fence_manager.check_point_intrusion(lng, lat, camera_id)
    return {
        "intruded": len(intruded) > 0,
        "fences": [
            {
                "fence_id": f.fence_id,
                "fence_name": f.fence_name,
                "fence_type": f.fence_type,
                "alert_level": f.alert_level,
            }
            for f in intruded
        ]
    }


@router.get("/types")
async def get_fence_types():
    return {
        "types": [
            {"value": 1, "label": "施工区", "color": "#faad14"},
            {"value": 2, "label": "应急车道", "color": "#ff4d4f"},
            {"value": 3, "label": "禁入区", "color": "#52c41a"},
            {"value": 4, "label": "自定义", "color": "#1890ff"},
        ],
        "alert_levels": [
            {"value": 1, "label": "一般"},
            {"value": 2, "label": "严重"},
            {"value": 3, "label": "紧急"},
            {"value": 4, "label": "特急"},
        ],
        "detect_targets": [
            {"value": "person", "label": "行人"},
            {"value": "car", "label": "轿车"},
            {"value": "truck", "label": "卡车"},
            {"value": "bus", "label": "公交车"},
            {"value": "motorcycle", "label": "摩托车"},
            {"value": "bicycle", "label": "自行车"},
        ],
    }
