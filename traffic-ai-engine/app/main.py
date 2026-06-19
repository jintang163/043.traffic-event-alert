from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.config.settings import settings
from app.api import detect, track, event, fence, track_cross, enhance

app = FastAPI(
    title=settings.PROJECT_NAME,
    version=settings.VERSION,
    description=settings.DESCRIPTION
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.ALLOWED_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(detect.router, prefix=settings.API_V1_PREFIX + "/detect", tags=["detection"])
app.include_router(track.router, prefix=settings.API_V1_PREFIX + "/track", tags=["tracking"])
app.include_router(event.router, prefix=settings.API_V1_PREFIX + "/event", tags=["event"])
app.include_router(fence.router, prefix=settings.API_V1_PREFIX + "/fence", tags=["fence"])
app.include_router(track_cross.router, prefix=settings.API_V1_PREFIX + "/track-cross", tags=["cross-camera-tracking"])
app.include_router(enhance.router, prefix=settings.API_V1_PREFIX + "/enhance", tags=["image-enhancement"])


@app.get("/health")
async def health_check():
    return {"status": "healthy", "version": settings.VERSION}


@app.get("/")
async def root():
    return {
        "name": settings.PROJECT_NAME,
        "version": settings.VERSION,
        "description": settings.DESCRIPTION,
        "docs": "/docs"
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "app.main:app",
        host=settings.HOST,
        port=settings.PORT,
        reload=settings.DEBUG
    )
