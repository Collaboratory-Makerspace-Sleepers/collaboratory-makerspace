import json
import responses as resp_mock

from tests.conftest import FAKE_CONTEXT, make_sqs_event

BASE_URL = "http://localhost:8080"
EVENTS_URL = f"{BASE_URL}/api/internal/billing/events"

CONFIG = {
    "internal_api_url": BASE_URL,
    "internal_api_secret": "test-secret",
    "stripe_restricted_key": "rk_test_xxx",
}


@resp_mock.activate
def test_dispute_created_forwarded(mocker):
    mocker.patch("alert_consumer.handler.get_config", return_value=CONFIG)
    resp_mock.add(resp_mock.POST, EVENTS_URL, status=200)

    from alert_consumer.handler import handler
    result = handler(make_sqs_event("dispute_created.json"), FAKE_CONTEXT)

    assert result == {"batchItemFailures": []}
    body = json.loads(resp_mock.calls[0].request.body)
    assert body["type"] == "charge.dispute.created"
    assert body["source"] == "EVENTBRIDGE"


@resp_mock.activate
def test_409_treated_as_success(mocker):
    mocker.patch("alert_consumer.handler.get_config", return_value=CONFIG)
    resp_mock.add(resp_mock.POST, EVENTS_URL, status=409)

    from alert_consumer.handler import handler
    result = handler(make_sqs_event("dispute_created.json"), FAKE_CONTEXT)

    assert result == {"batchItemFailures": []}


@resp_mock.activate
def test_422_routes_to_batch_item_failures(mocker):
    mocker.patch("alert_consumer.handler.get_config", return_value=CONFIG)
    resp_mock.add(resp_mock.POST, EVENTS_URL, status=422)

    from alert_consumer.handler import handler
    result = handler(make_sqs_event("dispute_created.json"), FAKE_CONTEXT)

    assert len(result["batchItemFailures"]) == 1