import json
import os
import sys
from pathlib import Path

import pytest

# Mirrors /opt/python in the Lambda runtime, where the shared layer is installed.
sys.path.insert(0, str(Path(__file__).parent.parent / "layer" / "python"))

# Consumer handlers are not packages, so add their parent to the path too.
sys.path.insert(0, str(Path(__file__).parent.parent))

os.environ.setdefault("ENVIRONMENT", "test")
os.environ.setdefault("EXPECTED_LIVEMODE", "false")
os.environ.setdefault("AWS_DEFAULT_REGION", "us-east-1")
os.environ.setdefault("AWS_ACCESS_KEY_ID", "test")
os.environ.setdefault("AWS_SECRET_ACCESS_KEY", "test")
os.environ.setdefault("POWERTOOLS_SERVICE_NAME", "stripe-consumers-test")
os.environ.setdefault("POWERTOOLS_TRACE_DISABLED", "true")


@pytest.fixture(autouse=True)
def reset_config_cache():
    """Clear lru_cache on get_config() between tests so mocks take effect."""
    from shared.config import get_config
    get_config.cache_clear()
    yield
    get_config.cache_clear()


@pytest.fixture
def mock_config(mocker):
    return mocker.patch(
        "shared.config.get_config",
        return_value={
            "internal_api_url": "http://localhost:8080",
            "internal_api_secret": "test-secret",
            "stripe_restricted_key": "rk_test_xxx",
        },
    )


def load_fixture(name: str) -> dict:
    """Load an EventBridge envelope fixture from tests/fixtures/."""
    fixture_path = Path(__file__).parent / "fixtures" / name
    return json.loads(fixture_path.read_text())


def make_sqs_record(fixture_name: str, message_id: str = "test-msg-id") -> dict:
    """Wrap an EventBridge fixture in an SQS record (the shape Lambda receives)."""
    envelope = load_fixture(fixture_name)
    return {
        "messageId": message_id,
        "receiptHandle": f"receipt-{message_id}",
        "body": json.dumps(envelope),
        "attributes": {},
        "messageAttributes": {},
        "md5OfBody": "",
        "eventSource": "aws:sqs",
        "awsRegion": "us-east-1",
    }


class FakeLambdaContext:
    """Minimal Lambda context for Powertools' @logger.inject_lambda_context."""
    function_name = "test-function"
    memory_limit_in_mb = "128"
    invoked_function_arn = "arn:aws:lambda:us-east-1:123456789012:function:test"
    aws_request_id = "test-request-id"


FAKE_CONTEXT = FakeLambdaContext()


def make_sqs_event(*fixture_names: str) -> dict:
    """Build a full SQS event (Records array) from one or more fixture names."""
    return {
        "Records": [
            make_sqs_record(name, message_id=f"msg-{i}")
            for i, name in enumerate(fixture_names)
        ]
    }