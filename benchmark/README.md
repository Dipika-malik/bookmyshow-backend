# Cache Benchmark

Measures the movie read endpoints before and after Redis caching, on a realistic dataset.

## Dataset (`seed.py`)

5,000 movies · 10 cities · 200 theatres · 600 screens · 20,000 scheduled shows (150 "now showing" movies).

```bash
mysql -u root -p -e "CREATE DATABASE bookmyshow_bench"
# start the app once against bookmyshow_bench so Hibernate creates the tables, then:
python3 benchmark/seed.py seed.sql
mysql -u root -p bookmyshow_bench < seed.sql
```

## Run (`bench.py`)

Start the app on `localhost:8082` against `bookmyshow_bench`, then:

```bash
python3 benchmark/bench.py results.json
```

Per endpoint: 30 warm-up requests, 300 sequential requests for p50 / p95 latency,
then 16 concurrent clients × 100 requests for throughput.

## Results

MacBook (Apple Silicon), MySQL 9.3 and Redis 7 on the same machine.
"Before" is commit `cc6ca53` (no Redis); "after" is the Redis cache-aside version.

| Endpoint | Before p50 / p95 | After p50 / p95 | Before → after throughput |
|---|---|---|---|
| `GET /api/movies?city=Mumbai` (48 KB) | 29.8 / 31.4 ms | **1.55 / 3.64 ms** | 66 → **2,397 req/s** |
| `GET /api/movies?city=Mumbai&title=dark` | 25.5 / 26.3 ms | **0.69 / 1.31 ms** | 65 → **5,492 req/s** |
| `GET /api/movies/4990` | 0.41 / 0.54 ms | 0.59 / 1.04 ms | 7,412 → 5,104 req/s |
| `GET /api/movies` (all 5,000, 1.6 MB) | 15.5 / 17.7 ms | 15.3 / 18.2 ms | 130 → 107 req/s |

## Takeaways

- **The cache pays off where the query is expensive.** City listing joins movies → shows → screens → theatres
  and de-duplicates (`DISTINCT`) across 20k shows; serving it from Redis is ~19× faster at p50 with ~36× the throughput.
- **It does not help a primary-key lookup.** `findById` on an indexed PK is already sub-millisecond, so a Redis
  round trip plus JSON deserialization costs about the same as hitting MySQL.
- **It does not help a huge payload.** For the full 1.6 MB catalogue, time is dominated by JSON (de)serialization,
  which happens either way. The real fix there is pagination, not caching.
- Both stores are local here, so a cache miss is cheaper than it would be against a remote database (e.g. RDS);
  the gap for expensive queries is typically larger in production.
