# DB.md — Verifying Database Updates via Postman + MySQL Workbench / DBeaver

This file shows, for every API, the exact Postman request to send and the exact SQL
query to run afterward (in **MySQL Workbench** or **DBeaver** — both work, pick either)
to *see* the row that got created/changed.
It also includes a plain-English "how to think about this" section for interview prep.

The project now runs on a local MySQL database (not H2) — see `application.properties`:
- DB name: `bookmyshow`
- Host: `127.0.0.1:3306`
- User: `root`

---

## 1. How to connect a GUI client to this database (do this once)

Make sure MySQL server is running (`brew services start mysql` if needed) and the
Spring Boot app has started at least once — it auto-creates all tables via
`spring.jpa.hibernate.ddl-auto=update`. Then connect using either tool below.

### Option A — MySQL Workbench

1. Open **MySQL Workbench**
2. On the home screen, click the **`+`** next to "MySQL Connections"
3. Fill in:
   - **Connection Name:** anything, e.g. `BookMyShow Local`
   - **Hostname:** `127.0.0.1`
   - **Port:** `3306`
   - **Username:** `root`
4. Click **Test Connection** → enter the root password when prompted → **OK**
5. Double-click the new connection to open it
6. In the left **Schemas** panel, click `bookmyshow` to expand it, then **Tables**

### Option B — DBeaver

1. Open **DBeaver** → **New Database Connection** → choose **MySQL**
2. Fill in:
   - **Host:** `localhost`
   - **Port:** `3306`
   - **Database:** `bookmyshow`
   - **Username:** `root`
   - **Password:** `Dipika@321`
3. **Test Connection** (let DBeaver download the MySQL driver if prompted) → **Finish**
4. In the left tree, expand the connection → `bookmyshow` → **Tables**
5. To run queries: right-click the connection → **SQL Editor** → **New SQL Script**,
   then type a query and run it with the ▶ (execute) button or `Cmd+Enter`

Either tool should show all 9 tables: `users`, `movies`, `theatres`, `screens`,
`seats`, `shows`, `show_seats`, `bookings`, `payments`.

✅ Unlike the old H2 setup, this MySQL database is **persistent** — data survives
app restarts (`ddl-auto=update` only adds/changes schema, it never drops data).
You no longer need to keep the app running just to inspect rows — just keep
your DB client connected.

---

## 2. The general workflow (repeat for every API)

```
1. Send Postman request
2. Look at the Postman response (note any id returned, e.g. userId, movieId)
3. Go to MySQL Workbench (already connected)
4. Open a new SQL tab (or reuse one) and run a SELECT query on the relevant table
5. Click the ⚡ (execute) button or press Cmd+Enter to run it
6. Confirm the new/changed row matches what you sent
```

---

## 3. Auth APIs

### 3.1 Register

**Postman:**
```
POST http://localhost:8082/api/auth/register
Content-Type: application/json

{
  "name": "Test User",
  "email": "testuser@example.com",
  "password": "password123",
  "phone": "9876543210"
}
```

**Response:** `200 OK`
```json
{ "token": "...", "tokenType": "Bearer", "userId": 1, "name": "Test User", "email": "testuser@example.com", "role": "USER", "expiresIn": 86400000 }
```

**Check in MySQL Workbench:**
```sql
SELECT * FROM users;
```
You should see one row: `id=1`, `email=testuser@example.com`, `password` shown as a
long BCrypt hash (never plain text — that's the point of hashing), `role=USER`.

### 3.2 Login

**Postman:**
```
POST http://localhost:8082/api/auth/login
Content-Type: application/json

{
  "email": "testuser@example.com",
  "password": "password123"
}
```

**Check in MySQL Workbench:** no new row — login only *reads* the `users` table to verify the
password, then issues a new JWT. Nothing is updated.
```sql
SELECT id, email, role FROM users WHERE email = 'testuser@example.com';
```

> Copy the `token` from this response — you'll need it as a Bearer token for every
> protected request below (ADMIN or USER routes).

---

## 4. Movie APIs (ADMIN write, public read)

### 4.1 Create Movie (ADMIN)

**Postman:**
```
POST http://localhost:8082/api/movies
Authorization: Bearer <admin-jwt-token>
Content-Type: application/json

{
  "title": "Inception",
  "description": "A mind-bending heist thriller",
  "durationMinutes": 148,
  "genre": "SCIENCE_FICTION",
  "language": "English",
  "releaseDate": "2010-07-16",
  "rating": 8.8,
  "posterUrl": "https://example.com/inception.jpg"
}
```

**Check in MySQL Workbench:**
```sql
SELECT * FROM movies;
```

### 4.2 List/Get Movies (public, no auth)
```
GET http://localhost:8082/api/movies
GET http://localhost:8082/api/movies/1
```
No DB change — pure `SELECT`. Useful to confirm what you inserted above is readable.

---

## 5. Theatre & Screen APIs (ADMIN write)

### 5.1 Create Theatre

**Postman:**
```
POST http://localhost:8082/api/theatres
Authorization: Bearer <admin-jwt-token>
Content-Type: application/json

{
  "name": "PVR Forum Mall",
  "address": "Koramangala",
  "city": "Bangalore",
  "pincode": "560095"
}
```

**Check in MySQL Workbench:**
```sql
SELECT * FROM theatres;
```

### 5.2 Add Screen to Theatre

**Postman:**
```
POST http://localhost:8082/api/theatres/1/screens
Authorization: Bearer <admin-jwt-token>
Content-Type: application/json

{
  "name": "Screen 1",
  "totalSeats": 100,
  "screenType": "IMAX"
}
```

**Check in MySQL Workbench:**
```sql
SELECT * FROM screens WHERE theatre_id = 1;
SELECT * FROM seats WHERE screen_id = 1;   -- seats are usually auto-generated here too
```

---

## 6. Show APIs (ADMIN write)

### 6.1 Create Show

**Postman:**
```
POST http://localhost:8082/api/shows
Authorization: Bearer <admin-jwt-token>
Content-Type: application/json

{
  "movieId": 1,
  "screenId": 1,
  "showTime": "2026-07-01T18:30:00",
  "basePrice": 250.0
}
```

**Check in MySQL Workbench:**
```sql
SELECT * FROM shows;
SELECT * FROM show_seats WHERE show_id = 1;   -- one row per seat, all status=AVAILABLE
```
This is the API that fans out the most rows — every seat on that screen gets a
corresponding `show_seats` row so each show has its own independent seat map.

### 6.2 Get Seats for a Show
```
GET http://localhost:8082/api/shows/1/seats
```
No DB change — confirms the `show_seats` rows you just inserted are visible.

---

## 7. Booking APIs (USER, authenticated)

### 7.1 Create Booking

**Postman:**
```
POST http://localhost:8082/api/bookings
Authorization: Bearer <user-jwt-token>
Content-Type: application/json

{
  "showId": 1,
  "showSeatIds": [1, 2],
  "paymentMethod": "UPI"
}
```

**Check in MySQL Workbench (run all three to see the full picture):**
```sql
SELECT * FROM bookings;                          -- new row, status=CONFIRMED (or PENDING)
SELECT * FROM show_seats WHERE id IN (1,2);       -- status changed AVAILABLE -> BOOKED
SELECT * FROM payments WHERE booking_id = 1;      -- new payment row, status=SUCCESS
```

This single Postman call touches **three tables** — that's the part worth saying
out loud in an interview: one HTTP request can trigger a multi-table transaction.

### 7.2 Cancel Booking

**Postman:**
```
PUT http://localhost:8082/api/bookings/1/cancel
Authorization: Bearer <user-jwt-token>
```

**Check in MySQL Workbench:**
```sql
SELECT status FROM bookings WHERE id = 1;        -- now CANCELLED
SELECT status FROM show_seats WHERE booking_id = 1; -- back to AVAILABLE
```

---

## 8. Quick-reference: API → Table(s) updated

| API                             | Tables touched                                          |
|----------------------------------|----------------------------------------------------------|
| POST /api/auth/register          | `users` (insert)                                         |
| POST /api/auth/login             | none (read only)                                          |
| POST /api/movies                 | `movies` (insert)                                         |
| POST /api/theatres                | `theatres` (insert)                                       |
| POST /api/theatres/{id}/screens  | `screens`, `seats` (insert)                              |
| POST /api/shows                  | `shows`, `show_seats` (insert, one per seat)             |
| POST /api/bookings                | `bookings`, `show_seats` (update), `payments` (insert)    |
| PUT /api/bookings/{id}/cancel    | `bookings` (update), `show_seats` (update)                |
| All other GET endpoints | none — read only |

---

## 9. Interview Prep — "How do I think about which controller to build?" (explained like a layman)

Think of the app as a **real cinema business**, and ask: *"Who needs to do what,
and what 'thing' are they acting on?"*

1. **Find the nouns first** — these become your tables/entities and, usually, one
   controller each: a *Movie*, a *Theatre*, a *Screen*, a *Show* (a screening of a
   movie at a specific time), a *Seat*, a *Booking*, a *Payment*, a *User*.
   Rule of thumb: **one controller per noun that has its own lifecycle**
   (create/read/update/delete independently of others).

2. **Then find the verbs/actions people take on each noun:**
   - Admin: "I want to add a new movie to the system" → `POST /api/movies`
   - Admin: "I want to schedule a screening" → `POST /api/shows`
   - Customer: "I want to see what's playing" → `GET /api/movies`, `GET /api/shows`
   - Customer: "I want to book 2 seats" → `POST /api/bookings`
   - Customer: "I changed my mind" → `PUT /api/bookings/{id}/cancel`

3. **Decide who is allowed to do it** — this is where `public` / `USER` / `ADMIN`
   comes from. Ask: *"Does this change something everyone sees (the catalog), or
   something that belongs to one person (their booking)?"*
   - Changes the shared catalog (movies, shows, theatres) → only **ADMIN**
   - Changes only "my own" data (my booking) → any logged-in **USER**
   - Just looking, not changing anything → **public**

4. **Group related actions under the same controller** — don't make a controller
   per HTTP verb, make one per noun. `MovieController` owns *all* movie actions
   (list, get, create, update, delete) — that's why there's one file, not five.

5. **Notice dependency order** — this is the part interviewers love to probe:
   you can't create a `Show` without a `Movie` and a `Screen` existing first, and
   you can't create a `Booking` without a `Show` (and its seats) existing. That
   ordering is *why* the project has Movie → Theatre/Screen → Show → Booking as a
   natural build-up, and it's a good story to tell: "I built it bottom-up, starting
   with the entities that have no dependencies (User, Movie, Theatre), then the
   ones that depend on them (Screen, Show), then the ones that depend on those
   (Booking, Payment)."

6. **One thing to call out as a strength in an interview:** the `ShowSeat` table.
   A naive design would just have `Seat` with a `booked` boolean — but that breaks
   the moment the same physical seat is reused across multiple shows in a day.
   `ShowSeat` exists specifically so *each show* has its *own* independent seat
   availability — that's a real system-design insight, not just CRUD.

7. **Concurrency point worth mentioning:** what stops two people from booking the
   same seat for the same show at the same time? Answer: seat status transitions
   (`AVAILABLE → LOCKED → BOOKED`) plus `@Version` optimistic locking on `Show`.
   That's the kind of detail that signals you understand more than basic CRUD.