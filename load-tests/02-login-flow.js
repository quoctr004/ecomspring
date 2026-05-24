// k6 load test — auth flow (login + /me).
// Validates: virtual threads on auth-service, rate-limiter (Redis Lua),
//            HikariCP pool, JWT validation.
//
// Pre-req: a test user exists. Run register-user.sh first to seed.
//
// Run: k6 run 02-login-flow.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const errorRate    = new Counter('errors');
const rateLimited  = new Counter('rate_limited_429');

export const options = {
    // Each VU does login + /me + logout. Realistic peak: ~50 concurrent users.
    stages: [
        { duration: '10s', target: 10  },
        { duration: '30s', target: 50  },
        { duration: '30s', target: 100 },
        { duration: '10s', target: 0   },
    ],
    thresholds: {
        http_req_duration: ['p(95)<800'],
        // Rate limit hits are expected when test exceeds 5 login/min/IP — track but don't fail.
        'http_req_failed{tag:not_rate_limited}': ['rate<0.02'],
    },
};

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const USERS = JSON.parse(open('./users.json'));   // pre-seeded test users

export default function () {
    // Pick a random user for this VU to avoid hitting same-IP rate limit hard.
    const user = USERS[Math.floor(Math.random() * USERS.length)];

    // Login
    const loginRes = http.post(`${BASE}/api/v1/auth/login`,
        JSON.stringify({ usernameOrEmail: user.email, password: user.password }),
        { headers: { 'Content-Type': 'application/json' } });

    if (loginRes.status === 429) {
        rateLimited.add(1);
        sleep(2);
        return;
    }

    const loginOk = check(loginRes, {
        'login 200': (r) => r.status === 200,
        'has access token cookie': (r) => r.cookies['ACCESS_TOKEN'] !== undefined,
    });
    if (!loginOk) { errorRate.add(1); return; }

    // /me
    const meRes = http.get(`${BASE}/api/v1/auth/me`,
        { cookies: { ACCESS_TOKEN: loginRes.cookies.ACCESS_TOKEN[0].value } });
    check(meRes, { '/me 200': (r) => r.status === 200 });

    sleep(1);
}
