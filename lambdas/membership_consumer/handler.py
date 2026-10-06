import json
import logging
import os

import stripe
from aws_lambda_powertools import Logger, Tracer
from aws_lambda_powertools.utilities.batch import (
    BatchProcessor,
    EventType,
    process_partial_response,
)
from aws_lambda_powertools.utilities.typing import LambdaContext

from shared.config import get_config
from shared.envelope import parse_sqs_record
from shared.internal_client import PermanentFailure, post_event

logger = Logger()
tracer = Tracer()
processor = BatchProcessor(event_type=EventType.SQS, raise_on_entire_batch_failure=False)

_EXPECTED_LIVEMODE = os.environ.get("EXPECTED_LIVEMODE", "false").lower() == "true"


def _process_record(record: dict) -> None:
    config = get_config()
    cmd = parse_sqs_record(record)

    if cmd.livemode != _EXPECTED_LIVEMODE:
        # Cross-wired bus (prod function receiving sandbox event or vice versa).
        # Log as a warning — it signals config drift — but do not fail the record.
        logger.warning(
            "livemode_mismatch",
            event_id=cmd.event_id,
            event_livemode=cmd.livemode,
            expected=_EXPECTED_LIVEMODE,
        )
        return

    # Re-fetch the subscription so we always apply current state, not the
    # potentially stale snapshot in the event envelope. Stripe does not guarantee
    # event ordering, so the embedded snapshot may be older than what we have stored.
    # billing_consumer intentionally skips this — invoice events are immutable records.
    cmd = _refresh_subscription(cmd, config["stripe_restricted_key"])

    try:
        post_event(
            config["internal_api_url"],
            config["internal_api_secret"],
            cmd.to_api_dict(),
        )
    except PermanentFailure:
        logger.exception("permanent_failure", event_id=cmd.event_id)
        raise  # BatchProcessor → batchItemFailures → DLQ after maxReceiveCount


def _refresh_subscription(cmd, restricted_key: str):
    """
    Replace the event payload's data.object with a fresh fetch from the Stripe API.
    Uses the restricted (read-only) key, not the full application key.
    Returns cmd unchanged if the object is not a subscription.
    """
    event_dict = json.loads(cmd.payload)
    sub_id = event_dict.get("data", {}).get("object", {}).get("id", "")

    if not sub_id.startswith("sub_"):
        return cmd

    stripe.api_key = restricted_key
    sub = stripe.Subscription.retrieve(sub_id)

    # StripeObject.__str__ returns the JSON representation of the object.
    event_dict["data"]["object"] = json.loads(str(sub))

    return cmd.with_payload(json.dumps(event_dict))


@logger.inject_lambda_context
@tracer.capture_lambda_handler
def handler(event: dict, context: LambdaContext) -> dict:
    return process_partial_response(
        event=event,
        record_handler=_process_record,
        processor=processor,
        context=context,
    )