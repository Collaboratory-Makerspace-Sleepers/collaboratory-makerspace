import json
from datetime import datetime, timezone

from shared.envelope import StripeEventCommand, parse_sqs_record
from tests.conftest import make_sqs_record


def test_parse_subscription_created():
    record = make_sqs_record("subscription_created.json")
    cmd = parse_sqs_record(record)

    assert cmd.event_id == "evt_sub_created_001"
    assert cmd.type == "customer.subscription.created"
    assert cmd.api_version == "2024-06-20"
    assert cmd.livemode is False
    assert cmd.source == "EVENTBRIDGE"
    assert "sub_test_monthly_001" in cmd.payload


def test_occurred_at_derived_from_stripe_created_timestamp():
    record = make_sqs_record("subscription_created.json")
    cmd = parse_sqs_record(record)

    expected = datetime.fromtimestamp(1758592800, tz=timezone.utc).isoformat()
    assert cmd.occurred_at == expected


def test_parse_invoice_paid():
    record = make_sqs_record("invoice_paid.json")
    cmd = parse_sqs_record(record)

    assert cmd.event_id == "evt_invoice_paid_001"
    assert cmd.type == "invoice.paid"
    assert cmd.livemode is False


def test_payload_is_full_stripe_event_json():
    record = make_sqs_record("checkout_session_completed.json")
    cmd = parse_sqs_record(record)

    payload = json.loads(cmd.payload)
    assert payload["id"] == "evt_checkout_completed_001"
    assert payload["data"]["object"]["id"] == "cs_test_001"


def test_with_payload_returns_new_command_with_same_metadata():
    record = make_sqs_record("subscription_created.json")
    cmd = parse_sqs_record(record)
    new_payload = json.dumps({"id": "sub_new", "status": "active"})

    updated = cmd.with_payload(new_payload)

    assert updated.payload == new_payload
    assert updated.event_id == cmd.event_id
    assert updated.type == cmd.type
    assert updated.livemode == cmd.livemode


def test_to_api_dict_uses_camel_case_keys():
    record = make_sqs_record("subscription_created.json")
    cmd = parse_sqs_record(record)
    d = cmd.to_api_dict()

    assert set(d.keys()) == {"eventId", "type", "apiVersion", "livemode", "occurredAt", "source", "payload"}
    assert d["eventId"] == cmd.event_id
    assert d["occurredAt"] == cmd.occurred_at


def test_livemode_true_parsed_correctly():
    record = make_sqs_record("subscription_created.json")
    # Patch livemode to true in the body
    import json as _json
    envelope = _json.loads(record["body"])
    envelope["detail"]["livemode"] = True
    record["body"] = _json.dumps(envelope)

    cmd = parse_sqs_record(record)
    assert cmd.livemode is True