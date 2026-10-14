import random, datetime
random.seed(42)
genres = "ACTION COMEDY DRAMA THRILLER HORROR ROMANCE SCIENCE_FICTION ANIMATION DOCUMENTARY BIOGRAPHY".split()
langs = ["Hindi","English","Tamil","Telugu","Kannada","Malayalam","Marathi","Bengali"]
cities = ["Mumbai","Delhi","Bangalore","Hyderabad","Chennai","Pune","Kolkata","Ahmedabad","Jaipur","Kochi"]
words = "Dark Last Silent Golden Broken Hidden Eternal Wild Lost Rising Red Blue Night City Storm River Shadow Fire Kingdom Dream Echo Legacy Mission Code Journey Hero Ghost Empire Signal Horizon".split()
out = []
def batch(table, cols, rows, n=1000):
    for i in range(0, len(rows), n):
        vals = ",".join("(" + ",".join(v for v in r) + ")" for r in rows[i:i+n])
        out.append(f"INSERT INTO {table} ({cols}) VALUES {vals};")
q = lambda s: "'" + s.replace("'", "''") + "'"
N_MOVIES, NOW_SHOWING = 5000, 150
movies = []
for i in range(1, N_MOVIES + 1):
    t = f"{random.choice(words)} {random.choice(words)} {i}"
    d = datetime.date(2015,1,1) + datetime.timedelta(days=random.randint(0, 4300))
    movies.append((str(i), q(t), q(f"Synopsis for {t}. " * 3), str(random.randint(90,180)), q(random.choice(genres)),
                   q(random.choice(langs)), q(d.isoformat()), str(round(random.uniform(4,9.5),1)), q(f"https://img.example.test/{i}.jpg")))
batch("movies", "id,title,description,duration_minutes,genre,language,release_date,rating,poster_url", movies)
theatres, screens = [], []
tid = sid = 0
for c in cities:
    for k in range(20):
        tid += 1
        theatres.append((str(tid), q(f"Cinema {tid} {c}"), q(f"{k+1} Main Road, {c}"), q(c), q("400001")))
        for s in range(3):
            sid += 1
            screens.append((str(sid), q(f"Screen {s+1}"), "120", q("IMAX" if s == 0 else "2D"), str(tid)))
batch("theatres", "id,name,address,city,pincode", theatres)
batch("screens", "id,name,total_seats,screen_type,theatre_id", screens)
shows = []
base = datetime.datetime(2026,11,1,9,0)
for i in range(1, 20001):
    m = random.randint(N_MOVIES - NOW_SHOWING + 1, N_MOVIES)   # recent releases are "now showing"
    st = base + datetime.timedelta(days=random.randint(0,13), hours=random.choice([0,3,6,9,12]))
    shows.append((str(i), str(m), str(random.randint(1, sid)), q(st.isoformat(sep=' ')), str(random.choice([180,220,250,300])), q("SCHEDULED"), "0"))
batch("shows", "id,movie_id,screen_id,show_time,base_price,status,version", shows)
open(__import__("sys").argv[1], "w").write("SET autocommit=0;\n" + "\n".join(out) + "\nCOMMIT;\n")
print(f"movies={len(movies)} theatres={len(theatres)} screens={len(screens)} shows={len(shows)}")
