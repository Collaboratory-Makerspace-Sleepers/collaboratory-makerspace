import json
from dataclasses import dataclass
from datetime import datetime, timezone


@dataclass(frozen=True)
class StripeEventCommand:
    event_id: str
    type: str
    api_version: str
    livemode: bool
    occurred_at: str  # ISO 8601, compatible with Java ZonedDateTime
    source: str
    payload: str      # raw Stripe event JSON

    def to_api_dict(self) -> dict:
        """Serialize to the shape Spring's StripeEventCommand record expects (camelCase)."""
        return {
            "eventId": self.event_id,
            "type": self.type,
            "apiVersion": self.api_version,
            "livemode": self.livemode,
            "occurredAt": self.occurred_at,
            "source": self.source,
            "payload": self.payload,
        }

    def with_payload(self, new_payload: str) -> "StripeEventCommand":
        """Return a copy with a different payload (used after subscription re-fetch)."""
        return StripeEventCommand(
            event_id=self.event_id,
            type=self.type,
            api_version=self.api_version,
            livemode=self.livemode,
            occurred_at=self.occurred_at,
            source=self.source,
            payload=new_payload,
        )


def parse_sqs_record(record: dict) -> StripeEventCommand:
    """
    Parse a single SQS record containing an EventBridge envelope.

    EventBridge wraps the Stripe event under 'detail':
        SQS body → EventBridge envelope → detail → Stripe event

    The Stripe event is nested one level deeper than every webhook tutorial assumes.
    """
    eb_envelope = json.loads(record["body"])
    stripe_event = eb_envelope["detail"]

    event_id = stripe_event["id"]
    event_type = stripe_event["type"]
    api_version = stripe_event.get("api_version", "")
    livemode = bool(stripe_event.get("livemode", False))

    created = stripe_event.get("created")
    if created:
        occurred_at = datetime.fromtimestamp(created, tz=timezone.utc).isoformat()
    else:
        occurred_at = eb_envelope.get("time", datetime.now(tz=timezone.utc).isoformat())

    return StripeEventCommand(
        event_id=event_id,
        type=event_type,
        api_version=api_version,
        livemode=livemode,
        occurred_at=occurred_at,
        source="EVENTBRIDGE",
        payload=json.dumps(stripe_event),
    )
