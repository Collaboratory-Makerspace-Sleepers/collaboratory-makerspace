import json
import pytest
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
def test_invoice_paid_forwarded_to_internal_api(mocker):
    mocker.patch("billing_consumer.handler.get_config", return_value=CONFIG)
    resp_mock.add(resp_mock.POST, EVENTS_URL, status=200)

    from billing_consumer.handler import handler
    result = handler(make_sqs_event("invoice_paid.json"), FAKE_CONTEXT)

    assert result == {"batchItemFailures": []}
    body = json.loads(resp_mock.calls[0].request.body)
    assert body["type"] == "invoice.paid"


@resp_mock.activate
def test_invoice_payment_failed_forwarded(mocker):
    mocker.patch("billing_consumer.handler.get_config", return_value=CONFIG)
    resp_mock.add(resp_mock.POST, EVENTS_URL, status=200)

    from billing_consumer.handler import handler
    result = handler(make_sqs_event("invoice_payment_failed.json"), FAKE_CONTEXT)

    assert result == {"batchItemFailures": []}
    body = json.loads(resp_mock.calls[0].request.body)
    assert body["type"] == "invoice.payment_failed"


@resp_mock.activate
def test_checkout_session_completed_forwarded(mocker):
    mocker.patch("billing_consumer.handler.get_config", return_value=CONFIG)
    resp_mock.add(resp_mock.POST, EVENTS_URL, status=200)

    from billing_consumer.handler import handler
    result = handler(make_sqs_event("checkout_session_completed.json"), FAKE_CONTEXT)

    assert result == {"batchItemFailures": []}
    body = json.loads(resp_mock.calls[0].request.body)
    assert body["type"] == "checkout.session.completed"


@resp_mock.activate
def test_409_duplicate_treated_as_success(mocker):
    mocker.patch("billing_consumer.handler.get_config", return_value=CONFIG)
    resp_mock.add(resp_mock.POST, EVENTS_URL, status=409)

    from billing_consumer.handler import handler
    result = handler(make_sqs_event("invoice_paid.json"), FAKE_CONTEXT)

    assert result == {"batchItemFailures": []}


@resp_mock.activate
def test_422_routes_to_batch_item_failures(mocker):
    mocker.patch("billing_consumer.handler.get_config", return_value=CONFIG)
    resp_mock.add(resp_mock.POST, EVENTS_URL, status=422, body="Bad request")

    from billing_consumer.handler import handler
    result = handler(make_sqs_event("invoice_paid.json"), FAKE_CONTEXT)

    assert len(result["batchItemFailures"]) == 1


@resp_mock.activate
def test_payload_preserves_raw_stripe_event(mocker):
    """billing_consumer must not modify the payload — no re-fetch, snapshot goes as-is."""
    mocker.patch("billing_consumer.handler.get_config", return_value=CONFIG)
    resp_mock.add(resp_mock.POST, EVENTS_URL, status=200)

    from billing_consumer.handler import handler
    handler(make_sqs_event("invoice_paid.json"), FAKE_CONTEXT)

    sent_payload = json.loads(json.loads(resp_mock.calls[0].request.body)["payload"])
    assert sent_payload["data"]["object"]["id"] == "in_test_001"
    assert sent_payload["data"]["object"]["amount_paid"] == 4999