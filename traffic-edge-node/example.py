import json
import logging
import os
import signal
import sys
import time
import uuid

from edge_sdk import EdgeNodeClient, EdgeNodeConfig

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

    client = EdgeNodeClient(config=config)

    signal.signal(signal.SIGINT, signal_handler)
    signal.signal(signal.SIGTERM, signal_handler)

    registered = client.register()
    if registered:
        logger.info("Node registered successfully")
    else:
        logger.warning("Node registration failed, will continue anyway")

    client.start()

    event_types = [
        "accident",
        "debris",
        "reverse",
        "congestion",
        "stopped_vehicle",
    ]

    event_counter = 0

    try:
        while True:
            status = client.get_status()
            logger.info(
                f"Node status: online={status['is_online']}, "
                f"queue_size={status['event_queue_size']}"
            )

            event_type = event_types[event_counter % len(event_types)]
            event_data = {
                "eventId": f"EVT{uuid.uuid4().hex[:12]}",
                "cameraId": 1001,
                "cameraName": "Camera-K100-01",
                "confidence": 0.85 + (event_counter % 10) * 0.01,
                "description": f"模拟{event_type}事件 #{event_counter + 1}",
                "severity": "high" if event_type in ("accident", "reverse") else "medium",
                "timestamp": time.strftime("%Y-%m-%dT%H:%M:%S"),
            }

            result = client.submit_event(
                event_type=event_type,
                event_data=event_data,
            )

            logger.info(
                f"Event submitted: uuid={result['event_uuid']}, "
                f"type={result['event_type']}, "
                f"uploaded={result['uploaded']}, cached={result['cached']}"
            )

            event_counter += 1
            time.sleep(5)

    except KeyboardInterrupt:
        logger.info("Keyboard interrupt received")
    finally:
        if client:
            client.stop()
        logger.info("Example finished")


if __name__ == "__main__":
    main()
