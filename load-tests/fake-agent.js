import http from 'k6/http';
import { check } from 'k6';

export const options = { scenarios: { agents: { executor:'constant-vus', vus:20, duration:'10m' } }, thresholds: { http_req_duration:['p(95)<30000'], http_req_failed:['rate<0.001'] } };
export default function () {
  const key = `k6-${__VU}-${__ITER}`;
  const response = http.post(`${__ENV.RISK_CORE_URL || 'http://localhost:8080'}/api/v1/diagnoses`, JSON.stringify({ accountId:'ACC_ALPHA_01', tradeDate:'2026-08-07' }), { headers:{ 'content-type':'application/json', 'Idempotency-Key':key, authorization:`Bearer ${__ENV.ACCESS_TOKEN || 'reference-token'}` } });
  check(response, { accepted: (result) => result.status === 202 });
}
