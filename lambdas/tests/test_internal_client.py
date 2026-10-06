import pytest
import responses as resp_mock
import requests

from shared.internal_client import PermanentFailure, post_event, _session

BASE_URL = "http://localhost:8080"
SECRET = "test-secret"
PAYLOAD = {"eventId": "evt_test_001", "type": "invoice.paid"}


@resp_mock.activate
def test_200_returns_normally():
    resp_mock.add(resp_mock.POST, f"{BASE_URL}/api/internal/billing/events", status=200)
    post_event(BASE_URL, SECRET, PAYLOAD)  # should not raise


@resp_mock.activate
def test_409_treated_as_success():
    """409 = duplicate event. Must NOT raise — treating it as failure causes an infinite retry loop."""
    resp_mock.add(resp_mock.POST, f"{BASE_URL}/api/internal/billing/events", status=409)
    post_event(BASE_URL, SECRET, PAYLOAD)  # should not raise


@resp_mock.activate
def test_422_raises_permanent_failure():
    resp_mock.add(
        resp_mock.POST,
        f"{BASE_URL}/api/internal/billing/events",
        status=422,
        body="Unrecognized event type",
    )
    with pytest.raises(PermanentFailure):
        post_event(BASE_URL, SECRET, PAYLOAD)


@resp_mock.activate
def test_500_raises_http_error_for_sqs_retry():
    resp_mock.add(resp_mock.POST, f"{BASE_URL}/api/internal/billing/events", status=500)
    with pytest.raises(requests.HTTPError):
        post_event(BASE_URL, SECRET, PAYLOAD)


@resp_mock.activate
def test_secret_header_is_sent():
    resp_mock.add(resp_mock.POST, f"{BASE_URL}/api/internal/billing/events", status=200)
    post_event(BASE_URL, SECRET, PAYLOAD)

    sent = resp_mock.calls[0].request
    assert sent.headers.get("X-Internal-Secret") == SECRET


@resp_mock.activate
def test_payload_sent_as_json():
    resp_mock.add(resp_mock.POST, f"{BASE_URL}/api/internal/billing/events", status=200)
    post_event(BASE_URL, SECRET, PAYLOAD)

    import json
    sent_body = json.loads(resp_mock.calls[0].request.body)
    assert sent_body["eventId"] == "evt_test_001"