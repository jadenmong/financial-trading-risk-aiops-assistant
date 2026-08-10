"""Optional adapter for public market data only.

It has no account, order, execution, position, risk-limit or trading endpoints.
The reference path is deterministic and network-free; AKShare is opt-in.
"""

from datetime import datetime
from decimal import Decimal
import os
from typing import Literal

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, ConfigDict, Field

app = FastAPI(title="Risk AIOps public market adapter", version="1.0.0")


class Snapshot(BaseModel):
    model_config = ConfigDict(extra="forbid")
    instrument_id: str = Field(pattern=r"^(SSE|SZSE|CFFEX):[A-Z0-9]{2,16}$")
    source: Literal["simulation", "akshare-public"]
    observed_at: datetime
    close: str = Field(pattern=r"^-?(0|[1-9][0-9]*)(\.[0-9]{1,10})?$")
    data_version: str


@app.get("/health/ready")
def ready() -> dict[str, str]:
    return {"status": "UP"}


@app.get("/api/v1/public-market/{venue}/{symbol}", response_model=Snapshot)
def public_market(venue: Literal["SSE", "SZSE", "CFFEX"], symbol: str) -> Snapshot:
    instrument_id = f"{venue}:{symbol}"
    if instrument_id != "SSE:600519":
        raise HTTPException(status_code=404, detail="reference snapshot not found")
    # Decimal is formatted to a string before entering JSON; float is never used.
    return Snapshot(
        instrument_id=instrument_id,
        source="simulation",
        observed_at=datetime.fromisoformat("2026-08-07T07:00:00+00:00"),
        close=f"{Decimal('1431.25'):.10f}",
        data_version="2026-08-07.v1",
    )


def akshare_enabled() -> bool:
    return os.getenv("AKSHARE_ENABLED", "false").lower() == "true"
