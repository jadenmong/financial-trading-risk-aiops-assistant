from fastapi.testclient import TestClient

from app.main import app


def test_only_public_market_surface_is_exposed() -> None:
    client = TestClient(app)
    result = client.get("/api/v1/public-market/SSE/600519")
    assert result.status_code == 200
    assert result.json()["close"] == "1431.2500000000"
    assert client.post("/api/v1/orders", json={}).status_code == 404
