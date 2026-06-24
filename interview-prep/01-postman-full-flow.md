# 01 — Full Postman Flow (Practice This End-to-End)

Goal: run the *entire* booking lifecycle once, in order, so you can demo it live
in an interview and narrate what's happening at each step. Each step lists what
to send, what to expect, and what to say out loud.

Your current state: you registered a user, promoted it to ADMIN in MySQL, and
successfully created a movie. Pick up from Step 4 below (theatre), or redo
from Step 1 with a second user if you want a clean USER vs ADMIN demo.

Server runs on port **8082** (check `application.properties` — it's changed
before, so confirm before you record/demo).

---

## Step 1 — Register an ADMIN-to-be user

```
POST http://localhost:8082/api/auth/register
Content-Type: application/json

{
  "name": "Admin User",
  "email": "admin@bookmyshow.com",
  "password": "admin123",
  "phone": "9876543210"
}
```
Save the `token` from the response as `{{adminToken}}` if using Postman variables.

**Say in interview:** "Registration auto-logs the user in — it returns a JWT
immediately so there's no separate login call needed right after signup."

Promote to admin in MySQL/DBeaver:
```sql
UPDATE users SET role = 'ADMIN' WHERE email = 'admin@bookmyshow.com';
```

---

## Step 2 — Register a normal customer (for the booking demo later)

```
POST http://localhost:8082/api/auth/register
Content-Type: application/json

{
  "name": "Customer One",
  "email": "customer@bookmyshow.com",
  "password": "customer123",
  "phone": "9876500000"
}
```
Save this token as `{{userToken}}`. This one stays role=USER — don't promote it.

**Say in interview:** "I'm using two accounts to demonstrate role-based access
control — one admin for catalog management, one regular user for booking."

---

## Step 3 — Create a Movie (ADMIN)

```
POST http://localhost:8082/api/movies
Authorization: Bearer {{adminToken}}
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
Note the returned `id` → this is your `movieId` for later steps.

**Try this too, to show the security boundary works:** repeat this exact
request with `{{userToken}}` instead — it should be REJECTED (403). This is
the single best thing to demo live: "watch this fail when I use a non-admin
token, then succeed when I switch to the admin token."

---

## Step 4 — Create a Theatre (ADMIN)

```
POST http://localhost:8082/api/theatres
Authorization: Bearer {{adminToken}}
Content-Type: application/json

{
  "name": "PVR Forum Mall",
  "address": "Koramangala",
  "city": "Bangalore",
  "pincode": "560095"
}
```
Note the returned `id` → `theatreId`.

---

## Step 5 — Add a Screen to that Theatre (ADMIN)

```
POST http://localhost:8082/api/theatres/{theatreId}/screens
Authorization: Bearer {{adminToken}}
Content-Type: application/json

{
  "name": "Screen 1",
  "screenType": "IMAX",
  "seatLayout": [
    { "rowName": "A", "seatsInRow": 5, "seatType": "REGULAR" },
    { "rowName": "B", "seatsInRow": 5, "seatType": "PREMIUM" }
  ]
}
```
Note: `seatLayout` is required — you define the seats row-by-row instead of
just giving a total count. This creates 10 seats: A1-A5 (REGULAR), B1-B5
(PREMIUM). Keep it small like this for the demo — easier to show the full
seat map without scrolling through 100 rows.
Note the returned screen `id` → `screenId`.

**Say in interview:** "Creating a screen takes a row-by-row seat layout —
each row can have its own seat type and count — and the service generates
the actual physical Seat rows (e.g. A1, A2, A3...) from that layout. The
client never has to know the seat IDs in advance."

Check in DBeaver:
```sql
SELECT * FROM seats WHERE screen_id = <screenId>;
```

---

## Step 6 — Create a Show (ADMIN)

```
POST http://localhost:8082/api/shows
Authorization: Bearer {{adminToken}}
Content-Type: application/json

{
  "movieId": <movieId>,
  "screenId": <screenId>,
  "showTime": "2026-07-01T18:30:00",
  "basePrice": 250.0
}
```

**Say in interview:** "This is the most interesting write operation in the
whole API — creating a Show doesn't just insert one row. The service fans
out and creates one ShowSeat row per physical seat on that screen, each
starting as AVAILABLE. That's what makes per-show seat maps independent —
the same physical seat can be AVAILABLE for one show and BOOKED for another."

### What "fans out" actually means, worked through with movieId=1, screenId=1

`ShowService.createShow()` does this, in order:

1. **Looks up Screen #1's seats.** If you created Screen 1 with a
   `seatLayout` like `A1-A5 (REGULAR)` and `B1-B5 (PREMIUM)`, then
   `seatRepository.findByScreenId(1)` returns those **10 physical Seat
   rows** — these already existed in the `seats` table before this show
   was even created, permanently tied to Screen 1.

2. **Creates exactly one Show row** in `shows`: `id=1, movieId=1,
   screenId=1, basePrice=250.0, status=SCHEDULED`.

3. **Loops over all 10 seats and creates 10 ShowSeat rows** — one per
   seat — pricing each using the multiplier table
   (`REGULAR×1.0, PREMIUM×1.5, VIP×2.0`):

   | Seat  | seatType | multiplier | price (basePrice × multiplier) | status    |
   |-------|----------|------------|--------------------------------|-----------|
   | A1    | REGULAR  | 1.0        | 250.0                          | AVAILABLE |
   | A2-A5 | REGULAR  | 1.0        | 250.0                          | AVAILABLE |
   | B1    | PREMIUM  | 1.5        | 375.0                          | AVAILABLE |
   | B2-B5 | PREMIUM  | 1.5        | 375.0                          | AVAILABLE |

   All 10 inserted in one `showSeatRepository.saveAll(showSeats)` call,
   each linked back to `show_id=1` AND to its own specific `seat_id`.

**Why this matters — the "independent seat map" point:** Seat A1 is one
physical row in `seats`, permanently tied to Screen 1, and it carries NO
booking status of its own. If you create a *second* show on Screen 1
(say tomorrow's 9pm screening), `createShow()` runs again and generates
**10 brand-new ShowSeat rows** for that second show — a fresh A1, fresh
B1, etc., all starting AVAILABLE again, with their own `show_id`. So:
- Physical seat A1 → exists once, forever, in `seats`.
- ShowSeat(A1, show #1) → its own independent AVAILABLE/BOOKED status.
- ShowSeat(A1, show #2 on the same screen) → its own independent status.

That's the entire reason `ShowSeat` is a separate table instead of a
`booked` boolean directly on `Seat` — A1 can be `BOOKED` for your 6:30pm
show and `AVAILABLE` for the 9pm show on the exact same physical chair,
because each show gets its own copy of seat-availability state.

Check:
```sql
SELECT * FROM shows;
SELECT * FROM show_seats WHERE show_id = <showId>;
```
You should see 10 rows (matching totalSeats), half priced 250.0 (REGULAR)
and half priced 375.0 (PREMIUM), all status=AVAILABLE.

---

## Step 7 — View the Seat Map (public)

```
GET http://localhost:8082/api/shows/{showId}/seats
```
No token needed. Note 2 `showSeatId`s you want to book, e.g. `1` and `2`.

---

## Step 8 — Create a Booking (USER — use the customer token!)

```
POST http://localhost:8082/api/bookings
Authorization: Bearer {{userToken}}
Content-Type: application/json

{
  "showId": <showId>,
  "showSeatIds": [1, 2],
  "paymentMethod": "UPI"
}
```

**Say in interview:** "This single call is a multi-table transaction — it
creates a Booking row, flips those two ShowSeat rows from AVAILABLE to
BOOKED, and creates a Payment row, all inside one @Transactional method.
If anything fails partway, the whole thing rolls back — no half-booked
seats."

Check:
```sql
SELECT * FROM bookings;
SELECT * FROM show_seats WHERE id IN (1,2);
SELECT * FROM payments WHERE booking_id = <bookingId>;
```

**Try the failure case too (great interview demo):** repeat the exact same
booking request again with the same showSeatIds — it should fail because
those seats are no longer AVAILABLE. This proves the double-booking guard
works.

---

## Step 9 — View My Bookings (USER)

```
GET http://localhost:8082/api/bookings
Authorization: Bearer {{userToken}}
```

**Try this too:** call it with `{{adminToken}}` instead — admin sees an empty
list (or their own bookings only, not the customer's), proving bookings are
scoped per-user, not a flat shared list.

---

## Step 10 — Cancel the Booking (USER)

```
PUT http://localhost:8082/api/bookings/{bookingId}/cancel
Authorization: Bearer {{userToken}}
```

Check:
```sql
SELECT status FROM bookings WHERE id = <bookingId>;        -- CANCELLED
SELECT status FROM show_seats WHERE booking_id = <bookingId>; -- back to AVAILABLE
```

**Say in interview:** "Cancelling releases the seats back to AVAILABLE so
someone else can book them — that's the seat lifecycle closing the loop."

---

## Suggested demo order if asked to "walk through the system live"

1. Register two users (admin + customer) — explain JWT + auto-login
2. Try creating a movie with the customer token → show it fail (403)
3. Create movie/theatre/screen/show with admin token → show it succeed
4. View seats publicly (no token) → explain public vs protected split
5. Book seats as customer → show the 3-table side effect in DBeaver
6. Try booking the same seats again → show it fail (concurrency guard)
7. Cancel the booking → show seats return to AVAILABLE

This script hits: auth, RBAC, public vs protected, multi-table transactions,
and concurrency safety — the five things most interviewers want to see you
explain with a live example instead of just reciting definitions.