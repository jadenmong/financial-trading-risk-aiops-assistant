import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: { deterministic: { executor: 'constant-arrival-rate', rate: 100, timeUnit: '1s', duration: '10m', preAllocatedVUs: 100, maxVUs: 300 } },
  thresholds: { http_req_duration: ['p(95)<500'], http_req_failed: ['rate<0.001'] },
};
export default function () {
  const response = http.post(`${__ENV.RISK_CORE_URL || 'http://localhost:8080'}/internal/v1/position-risk/query`, JSON.stringify({ accountId:'ACC_ALPHA_01' }), { headers: { 'content-type':'application/json', authorization:`Bearer ${__ENV.ACCESS_TOKEN || 'reference-token'}` } });
  check(response, { '200 and structured': (result) => result.status === 200 && result.json('schemaVersion') === '1.0' && result.json('ok') === true });
}
