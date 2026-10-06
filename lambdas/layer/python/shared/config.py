import os
import boto3
from functools import lru_cache

_SSM_PREFIX = "/collaboratory"


@lru_cache(maxsize=None)
def get_config() -> dict:
    """
    Read secrets from SSM Parameter Store once per Lambda container lifecycle.
    lru_cache means the SSM calls happen on the first invocation only.
    Tests clear the cache via conftest.py's reset_config_cache fixture.
    """
    env = os.environ["ENVIRONMENT"]
    ssm = boto3.client("ssm")

    def _get(name: str) -> str:
        return ssm.get_parameter(
            Name=f"{_SSM_PREFIX}/{env}/{name}", WithDecryption=True
        )["Parameter"]["Value"]

    return {
        "internal_api_url": _get("internal-api-url"),
        "internal_api_secret": _get("internal-api-secret"),
        "stripe_restricted_key": _get("stripe-restricted-key"),
    }
