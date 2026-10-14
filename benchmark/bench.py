import http.client, time, statistics, threading, sys, json
EPS = [("city listing", "/api/movies?city=Mumbai"),
       ("city + title search", "/api/movies?city=Mumbai&title=dark"),
       ("movie detail", "/api/movies/4990"),
       ("full catalogue (5k)", "/api/movies")]
def get(conn, path):
    t = time.perf_counter(); conn.request("GET", path); r = conn.getresponse(); body = r.read()
    assert r.status == 200, (path, r.status); return (time.perf_counter() - t) * 1000, len(body)
res = {}
for name, path in EPS:
    c = http.client.HTTPConnection("localhost", 8082)
    for _ in range(30): get(c, path)                      # warm-up (JIT, connection pool, cache fill)
    lat = []; size = 0
    for _ in range(300):
        ms, size = get(c, path); lat.append(ms)
    lat.sort()
    # throughput: 16 concurrent clients x 100 requests
    done = []
    def worker():
        cc = http.client.HTTPConnection("localhost", 8082)
        for _ in range(100): get(cc, path)
        done.append(1)
    th = [threading.Thread(target=worker) for _ in range(16)]
    t0 = time.perf_counter(); [t.start() for t in th]; [t.join() for t in th]; el = time.perf_counter() - t0
    res[name] = dict(p50=round(statistics.median(lat),2), p95=round(lat[int(len(lat)*0.95)],2), rps=round(1600/el), kb=round(size/1024,1))
    print(f"{name:22} p50 {res[name]['p50']:7.2f}ms  p95 {res[name]['p95']:7.2f}ms  {res[name]['rps']:6d} req/s  ({res[name]['kb']} KB)")
json.dump(res, open(sys.argv[1], "w"))
