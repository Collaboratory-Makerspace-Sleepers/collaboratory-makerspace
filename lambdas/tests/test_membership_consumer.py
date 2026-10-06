import json
import pytest
import responses as resp_mock

from tests.conftest import FAKE_CONTEXT, make_sqs_event, make_sqs_record

BASE_URL = "http://localhost:8080"
EVENTS_URL = f"{BASE_URL}/api/internal/billing/events"

CONFIG = {
    "internal_api_url": BASE_URL,
    "internal_api_secret": "test-secret",
    "stripe_restricted_key": "rk_test_xxx",
}

REFRESHED_SUB = {
    "id": "sub_test_monthly_001",
    "object": "subscription",
    "status": "active",
    "customer": "cus_test_001",
    "current_period_end": 1761271200,
}


def _mock_stripe_retrieve(mocker, sub_dict=None):
    sub = sub_dict or REFRESHED_SUB
    mock_sub = mocker.MagicMock()
    mock_sub.__str__ = lambda self: json.dumps(sub)
    mocker.patch("stripe.Subscription.retrieve", return_value=mock_sub)
    return mock_sub


@resp_mock.activate
def test_subscription_created_is_processed(mocker):
    mocker.patch("membership_consumer.handler.get_config", return_value=CONFIG)
    _mock_stripe_retrieve(mocker)
    resp_mock.add(resp_mock.POST, EVENTS_URL, status=200)

    from membership_consumer.handler import handler
    result = handler(make_sqs_event("subscription_created.json"), FAKE_CONTEXT)

    assert result == {"batchItemFailures": []}
    assert len(resp_mock.calls) == 1
    body = json.loads(resp_mock.calls[0].request.body)
    assert body["type"] == "customer.subscription.created"
    assert body["source"] == "EVENTBRIDGE"


@resp_mock.activate
def test_subscription_updated_payload_contains_refreshed_data(mocker):
    """The payload sent to Spring must use the re-fetched subscription, not the stale snapshot."""
    mocker.patch("membership_consumer.handler.get_config", return_value=CONFIG)
    refreshed = {**REFRESHED_SUB, "status": "past_due"}
    _mock_stripe_retrieve(mocker, refreshed)
    resp_mock.add(resp_mock.POST, EVENTS_URL, status=200)

    import stripe
    from membership_consumer.handler import handler
    handler(make_sqs_event("subscription_updated.json"), FAKE_CONTEXT)

    stripe.Subscription.retrieve.assert_called_once_with("sub_test_monthly_001")
    sent_payload = json.loads(json.loads(resp_mock.calls[0].request.body)["payload"])
    assert sent_payload["data"]["object"]["status"] == "past_due"


@resp_mock.activate
def test_billing_consumer_does_not_call_stripe(mocker):
    """invoice.paid goes to billing_consumer which must NOT re-fetch from Stripe."""
    mocker.patch("billing_consumer.handler.get_config", return_value=CONFIG)
    stripe_mock = mocker.patch("stripe.Subscription.retrieve")
    resp_mock.add(resp_mock.POST, EVENTS_URL, status=200)

    from billing_consumer.handler import handler
    handler(make_sqs_event("invoice_paid.json"), FAKE_CONTEXT)

    stripe_mock.assert_not_called()


@resp_mock.activate
def test_409_treated_as_success_no_batch_failure(mocker):
    """409 must not produce a batchItemFailure — that would cause an infinite retry loop."""
    mocker.patch("membership_consumer.handler.get_config", return_value=CONFIG)
    _mock_stripe_retrieve(mocker)
    resp_mock.add(resp_mock.POST, EVENTS_URL, status=409)

    from membership_consumer.handler import handler
    result = handler(make_sqs_event("subscription_created.json"), FAKE_CONTEXT)

    assert result == {"batchItemFailures": []}


@resp_mock.activate
def test_422_adds_record_to_batch_item_failures(mocker):
    """422 = permanent failure. Record goes to batchItemFailures → DLQ after retries."""
    mocker.patch("membership_consumer.handler.get_config", return_value=CONFIG)
    _mock_stripe_retrieve(mocker)
    resp_mock.add(resp_mock.POST, EVENTS_URL, status=422, body="Unknown event type")

    from membership_consumer.handler import handler
    result = handler(make_sqs_event("subscription_created.json"), FAKE_CONTEXT)

    assert len(result["batchItemFailures"]) == 1
    assert result["batchItemFailures"][0]["itemIdentifier"] == "msg-0"


@resp_mock.activate
def test_partial_batch_failure_one_bad_in_three(mocker):
    """One 422 in a batch of three must not cause the other two to be redelivered."""
    mocker.patch("membership_consumer.handler.get_config", return_value=CONFIG)
    _mock_stripe_retrieve(mocker)
    resp_mock.add(resp_mock.POST, EVENTS_URL, status=200)  # msg-0 succeeds
    resp_mock.add(resp_mock.POST, EVENTS_URL, status=422)  # msg-1 fails permanently
    resp_mock.add(resp_mock.POST, EVENTS_URL, status=200)  # msg-2 succeeds

    from membership_consumer.handler import handler
    result = handler(
        make_sqs_event(
            "subscription_created.json",
            "subscription_updated.json",
            "subscription_deleted.json",
        ),
        FAKE_CONTEXT,
    )

    assert len(result["batchItemFailures"]) == 1
    assert result["batchItemFailures"][0]["itemIdentifier"] == "msg-1"


def test_wrong_livemode_is_skipped_not_failed(mocker):
    """A sandbox event arriving in the prod function must be silently dropped, not failed."""
    import os
    mocker.patch.dict(os.environ, {"EXPECTED_LIVEMODE": "true"})
    mocker.patch("membership_consumer.handler.get_config", return_value=CONFIG)
    stripe_mock = mocker.patch("stripe.Subscription.retrieve")
    api_mock = mocker.patch("membership_consumer.handler.post_event")

    # Reload module to pick up patched env var
    import importlib
    import membership_consumer.handler as h
    importlib.reload(h)
    mocker.patch("membership_consumer.handler.get_config", return_value=CONFIG)

    # subscription_created.json has livemode: false, but EXPECTED_LIVEMODE is true
    result = h.handler(make_sqs_event("subscription_created.json"), FAKE_CONTEXT)

    stripe_mock.assert_not_called()
    api_mock.assert_not_called()
    assert result == {"batchItemFailures": []}