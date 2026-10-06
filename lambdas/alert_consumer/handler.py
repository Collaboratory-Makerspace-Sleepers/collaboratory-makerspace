import os

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
        logger.warning(
            "livemode_mismatch",
            event_id=cmd.event_id,
            event_livemode=cmd.livemode,
            expected=_EXPECTED_LIVEMODE,
        )
        return

    try:
        post_event(
            config["internal_api_url"],
            config["internal_api_secret"],
            cmd.to_api_dict(),
        )
    except PermanentFailure:
        logger.exception("permanent_failure", event_id=cmd.event_id)
        raise


@logger.inject_lambda_context
@tracer.capture_lambda_handler
def handler(event: dict, context: LambdaContext) -> dict:
    return process_partial_response(
        event=event,
        record_handler=_process_record,
        processor=processor,
        context=context,
    )