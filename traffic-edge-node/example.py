import json
import logging
import os
import signal
import sys
import time
import uuid

from edge_sdk import EdgeNodeClient, EdgeNodeConfig, TensorRTDetector

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
logger = logging.getLogger("example")

client: EdgeNodeClient | None = None


def signal_handler(signum, frame):
    global client
    logger.info(f"Received signal {signum}, shutting down...")
    if client:
        client.stop()
    sys.exit(0)


def main():
    global client

    config_path = os.path.join(os.path.dirname(__file__), "config.example.json")
    config = EdgeNodeConfig.from_json_file(config_path)

    logger.info(f"Starting edge node: {config.node_code} ({config.node_name})")
    logger.info(f"Server URL: {config.server_url}")

    detector = TensorRTDetector(
        model_path=config.model_path,
        engine_path=config.engine_path,
        confidence_threshold=config.confidence_threshold,
        cooldown_seconds=config.cooldown_seconds,
    )

    client = EdgeNodeClient(config=config, detector=detector)

    signal.signal(signal.SIGINT, signal_handler)
    signal.signal(signal.SIGTERM, signal_handler)

    registered = client.register()
    if registered:
        logger.info("Node registered successfully")
    else:
        logger.warning("Node registration failed, will continue anyway")

    for cam in config.cameras:
        client.add_stream(
            stream_url=cam.get("stream_url", ""),
            camera_id=cam.get("camera_id", 0),
            camera_name=cam.get("camera_name", ""),
            fps=cam.get("fps", 25.0),
        )
        logger.info(f"Added stream: {cam.get('camera_name', '')} ({cam.get('camera_id', 0)})")

    if not config.cameras:
        logger.info("No cameras configured, running in event submission mode")

    client.start()

    try:
        while True:
            status = client.get_status()
            logger.info(
                f"Node status: online={status['is_online']}, "
                f"queue_size={status['event_queue_size']}, "
                f"streams={len(status.get('streams', {}))}, "
                f"detector={status.get('detector_loaded', False)}"
            )
            time.sleep(10)
    except KeyboardInterrupt:
        logger.info("Keyboard interrupt received")
    finally:
        if client:
            client.stop()
        logger.info("Example finished")


if __name__ == "__main__":
    main()
