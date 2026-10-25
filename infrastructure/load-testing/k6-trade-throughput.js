import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    constant_request_rate: {
      executor: 'constant-arrival-rate',
      rate: 17, // 17 reqs/s ~ 1000 RPS/minute
      timeUnit: '1s', // 17 requests per second
      duration: '1m', // run for 1 minute
      preAllocatedVUs: 20, // max VUs allocated upfront
      maxVUs: 50,
    },
  },
  thresholds: {
    // 95% of requests must complete below 200ms
    http_req_duration: ['p(95)<200'],
    // Ensure failure rate is below 0.1%
    http_req_failed: ['rate<0.001'],
  },
};

// Setup runs once before the load test begins.
// Acquires a signed HS256 JWT directly from the API Gateway's own /api/auth/login endpoint.
// No Keycloak or external identity provider required.
export function setup() {
  const url = 'http://host.docker.internal:8080/api/auth/login';
  const payload = JSON.stringify({ username: 'trader', password: 'trader123' });
  const params = { headers: { 'Content-Type': 'application/json' } };

  const res = http.post(url, payload, params);
  if (res.status !== 200) {
    throw new Error(`Failed to acquire JWT from API Gateway. Status: ${res.status}, Body: ${res.body}`);
  }

  return { token: res.json('token') };
}

// The default function runs repeatedly 17 times a second.
export default function (data) {
  const url = 'http://host.docker.internal:8080/api/v1/trades';
  
  // Randomizing buyer and seller identifiers to avoid extreme DB deadlocking and simulate real-world scatter
  const buyerId = 'LOAD-BUYER-' + Math.floor(Math.random() * 100 + 1);
  const sellerId = 'LOAD-SELLER-' + Math.floor(Math.random() * 100 + 1);

  const payload = JSON.stringify({
    instrumentId: 'AAPL',
    buyerAccountId: buyerId,
    sellerAccountId: sellerId,
    quantity: Math.floor(Math.random() * 50 + 1), // small quantities ranging from 1 to 50
    price: 150.75
  });

  const params = {
    headers: {
      'Authorization': `Bearer ${data.token}`,
      'Content-Type': 'application/json',
    },
  };

  const res = http.post(url, payload, params);

  // Validate that the server responded with 202 Accepted (trade accepted for async proc)
  check(res, {
    'status is 202': (r) => r.status === 202,
  });
}
