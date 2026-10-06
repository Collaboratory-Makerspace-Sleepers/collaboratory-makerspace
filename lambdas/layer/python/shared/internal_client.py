import logging
import requests

logger = logging.getLogger(__name__)

# One session per Lambda container — keeps the TCP connection alive across invocations.
_session = requests.Session()


class PermanentFailure(Exception):
    """
    Raised on HTTP 422 from the internal API.

    A 422 means the payload is malformed and retrying will never succeed.
    BatchProcessor marks this record as a batchItemFailure; SQS retries it
    up to maxReceiveCount times and then routes it to the DLQ.
    Do NOT suppress this exception — silent success on a bad message loses data.
    """


def post_event(base_url: str, secret: str, payload: dict) -> None:
    """
    POST a StripeEventCommand to POST /api/internal/billing/events.

    Status-code contract (stripe-implementation.md §1.5):
      200 → processed successfully
      409 → duplicate, already processed — delete the message (treat as success)
      422 → permanent failure — raise PermanentFailure, do not retry in this function
      5xx / timeout → raise so SQS delivers the retry
    """
    resp = _session.post(
        f"{base_url}/api/internal/billing/events",
        json=payload,
        headers={"X-Internal-Secret": secret},
        timeout=10,
    )

    if resp.status_code == 200:
        return

    if resp.status_code == 409:
        # 409 means "already processed" — the message is safe to delete.
        # Treating this as a failure would cause an infinite retry loop.
        logger.info(
            "duplicate_event event_id=%s — already processed, deleting message",
            payload.get("eventId"),
        )
        return

    if resp.status_code == 422:
        raise PermanentFailure(
            f"422 for event {payload.get('eventId')}: {resp.text}"
        )

    resp.raise_for_status()  # 5xx → HTTPError → SQS retries the message