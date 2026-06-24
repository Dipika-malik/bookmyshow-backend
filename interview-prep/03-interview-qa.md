# 03 — Likely Interview Questions + How to Answer Them

Answers are written the way you should say them out loud — first the direct
answer, then the "why," because interviewers probe the why.

---

### "Walk me through your project."

"It's a movie ticket booking backend modeled on BookMyShow — Spring Boot,
JWT auth, MySQL via JPA/Hibernate. The domain has a natural dependency chain:
Movies and Theatres are independent; Screens belong to Theatres; Shows tie a
Movie to a Screen at a specific time; Bookings tie a User to a Show and a set
of seats; Payments are 1:1 with Bookings. I built it bottom-up following that
dependency order, with a layered architecture — controller, service,
repository — and DTOs separating the wire format from the database schema."

### "How does authentication work?"

"JWT-based, stateless. On login, AuthenticationManager validates the email +
BCrypt-hashed password via UserDetailsServiceImpl, then I issue a signed JWT
containing just the user's email as the subject claim — no role, no
permissions baked in. On every subsequent request, JwtAuthenticationFilter
re-validates the token's signature/expiry, extracts the email, and re-loads
that user's CURRENT role from the database. So authorization is always based
on live DB state, not stale token claims."

### "Why didn't you put the role inside the JWT?"

"I could have, and it would save a DB lookup per request — that's the
tradeoff. But re-checking the DB means if an admin's access is revoked, it
takes effect immediately on their very next request, instead of waiting for
their token to expire. For this project's scale, correctness mattered more
than the lookup cost."

### "How do you prevent two users from booking the same seat?"

"The repository method that fetches the requested ShowSeat rows uses a
PESSIMISTIC_WRITE lock — `SELECT ... FOR UPDATE` under the hood — inside the
same @Transactional createBooking() method that checks availability and
writes the new Booking. That means if two requests arrive for the same
seats at the same instant, the database itself serializes them: the second
transaction physically blocks on that SELECT until the first one commits or
rolls back. So there's no window where both requests can read 'AVAILABLE'
and then both write 'BOOKED.' Separately, the Show entity has @Version for
optimistic locking — that protects against lost updates if, say, two admin
requests modify the same Show concurrently; a stale write there fails fast
with an OptimisticLockException instead of silently overwriting."

### "Why is there a ShowSeat table instead of just a boolean on Seat?"

"Because a Seat is a physical thing tied to a Screen — it exists independent
of any particular showing. If 'booked' lived directly on Seat, that seat
would be permanently booked across EVERY show on that screen, not just the
one you booked for tonight at 7pm. ShowSeat exists specifically to make seat
availability per-show, not per-seat-forever."

### "What's the role of DTOs? Why not just return the entity?"

"Three reasons: don't leak fields the client shouldn't see (like a password
hash), let the entity's schema evolve independently of the API contract, and
validate input separately from persistence rules — @NotBlank/@Email/@Size
live on the request DTO, not on the entity."

### "How would you scale this / what's missing for production?"

Be honest and specific, this is the question that separates people who only
copy-pasted a tutorial from people who understand it:
- "No real payment gateway integration — Payment is simulated."
- "No seat-lock TIMEOUT — if a user starts booking and abandons it mid-flow,
  there's nothing to auto-expire a LOCKED seat back to AVAILABLE. I'd add
  a scheduled job or a TTL via Redis for that."
- "Single DB instance, no read replicas — fine for this scale, not for
  BookMyShow's real traffic."
- "No rate limiting on auth endpoints — vulnerable to brute force."
- "ddl-auto=update is fine for dev, but a real project needs Flyway/Liquibase
  migrations so schema changes are versioned and reviewable."
- "No admin-promotion endpoint by design (security), but that also means
  there's no way to bootstrap the FIRST admin except a manual DB insert or a
  one-time seed script — I'd add a seed script for that in a real deploy."

### "What was the hardest part to get right?"

Good honest answer based on what you actually did: "Getting MySQL running
locally and wired into the app — diagnosing why the server wasn't starting,
then making sure the dependency switch from H2 to the MySQL connector in
pom.xml lined up with the datasource config in application.properties.
It's a good reminder that 'the code compiles' and 'the app actually connects
to its database' are two different checkpoints."

### "Explain optimistic locking like I'm not a programmer."

"Imagine two people editing the same Google Doc page at once, but instead of
merging changes live, each save says 'I'm saving version 3.' If someone else
already saved version 3 in the meantime, your save gets rejected — you have
to refresh and try again. @Version does that for a database row: each
update checks 'is this still the version I read?' If not, it fails loudly
instead of silently overwriting someone else's change."

### "What HTTP status codes does your API use and why?"

- 200 — successful GET/PUT/POST returning data
- 400 — validation failure (@Valid on a request DTO failed)
- 401/403 — missing/invalid token, or valid token but wrong role
- 404 — ResourceNotFoundException (e.g. movie/show id doesn't exist)
- 409-ish — SeatNotAvailableException (seat already taken)
All centralized in `exception/GlobalExceptionHandler.java` via
`@ExceptionHandler` methods, so individual services just throw a typed
exception and don't worry about HTTP semantics.

### "If I gave you 30 more minutes, what would you add?"

Pick ONE and go deep rather than listing five shallow ideas:
- "A scheduled job to auto-release LOCKED seats after N minutes."
- "Pagination on GET /api/movies and GET /api/shows — right now they
  return everything."
- "Refresh tokens — current JWT just expires after 24h with no renewal flow."

---

## Things to physically DO before the interview (not just read)

1. Run the full flow in `01-postman-full-flow.md` at least twice from a
   cold start (restart MySQL, restart the app) so you've felt every step.
2. Deliberately trigger every error case once: wrong password, expired/
   malformed token, booking an already-booked seat, customer token hitting
   an admin route. Know what each one returns and why.
3. Open `02-code-flow-explained.md` next to the actual source files and
   trace Trace B line by line in the real `BookingService.java` — confirm
   it matches (logic may differ slightly from this summary; defer to the
   actual code if anything's off).