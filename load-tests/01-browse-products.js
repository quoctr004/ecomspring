// k6 load test — public catalog browse (no auth required).
// Validates: HikariCP pool + virtual threads on catalog-service.
//
// Run: k6 run 01-browse-products.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';

const errorRate  = new Counter('errors');
const latencyP95 = new Trend('latency_p95');

export const options = {
    // Ramp pattern: warm up → peak → cool down.
    stages: [
        { duration: '15s', target: 20  },   // warm-up
        { duration: '30s', target: 100 },   // ramp to 100 VUs
        { duration: '60s', target: 200 },   // sustained 200 VUs
        { duration: '15s', target: 0   },   // cool-down
    ],
    thresholds: {
        // Production SLO: p95 < 500ms, error rate < 1%
        http_req_duration: ['p(95)<500'],
        http_req_failed:   ['rate<0.01'],
    },
};

const BASE = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
    // Mix of common reads — what a typical browsing user does.
    const responses = http.batch([
        ['GET', `${BASE}/api/v1/products?page=0&size=10`],
        ['GET', `${BASE}/api/v1/categories`],
        ['GET', `${BASE}/api/v1/banners/active`],
    ]);

    responses.forEach((r, i) => {
        const ok = check(r, {
            'status 200': (res) => res.status === 200,
            'body has data': (res) => res.body && res.body.length > 0,
        });
        if (!ok) errorRate.add(1);
        latencyP95.add(r.timings.duration);
    });

    sleep(0.5);   // user think time
}
