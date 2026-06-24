# 02 — How a Request Actually Flows Through This Code

Use this to answer "walk me through what happens when I hit this endpoint."
Two concrete traces below: a protected write (create movie) and the booking
flow (the most complex one). Read these end to end once, then re-read while
looking at the actual files.

---

## Trace A: POST /api/movies (an authenticated write)

```
1. Postman sends:
   POST /api/movies
   Authorization: Bearer <jwt>
   Body: { title, description, ... }

2. Request hits Spring's filter chain BEFORE it reaches any controller.
   -> JwtAuthenticationFilter (security/JwtAuthenticationFilter.java) runs:
      a. Reads the "Authorization" header
      b. Strips "Bearer " prefix to get the raw token
      c. JwtTokenProvider.validateToken(token) checks signature + expiry
      d. JwtTokenProvider.getEmailFromToken(token) extracts the email
      e. Calls UserDetailsServiceImpl.loadUserByUsername(email)
         -> this hits the DB: SELECT * FROM users WHERE email = ?
         -> builds authorities: ["ROLE_ADMIN"] or ["ROLE_USER"]
      f. Sets this into Spring Security's SecurityContext for this request

3. SecurityConfig (config/SecurityConfig.java) checks the rule for this
   path. ⚠️ VERIFIED IN THE ACTUAL CODE — this is NOT role-gated:
     .requestMatchers(HttpMethod.POST, "/api/movies/**").authenticated()
   Spring Security only checks "is there a valid, authenticated principal
   at all" — it does NOT check for ROLE_ADMIN specifically. The code
   comment even admits this: "Write operations require authentication
   (ADMIN role can be enforced in production)". There is no @PreAuthorize
   anywhere in the controllers or services either — I checked.
   PRACTICAL EFFECT: right now, ANY logged-in user — USER or ADMIN — can
   create/update/delete movies, theatres, and shows. Only the booking
   endpoints are truly meant to be "any user," but movies/theatres/shows
   were *intended* to be admin-only per the design comments — that intent
   was never wired up to an actual role check.
   (Separately, this is still WHY a DB role change takes effect on your
   very next request without a new login — step 2e above re-reads the DB
   fresh on every single request, regardless of what's being checked.)

4. Request reaches MovieController.createMovie(MovieRequest).
   -> @Valid triggers Bean Validation on the request body first
      (e.g. @NotBlank on title). If invalid -> 400, never reaches your code.

5. Controller calls movieService.createMovie(request) — controllers in this
   project do NOT contain business logic, they just delegate.

6. MovieService:
   a. Maps MovieRequest (DTO) -> Movie (entity)
   b. Calls movieRepository.save(movie)
   c. Hibernate generates: INSERT INTO movies (title, ...) VALUES (...)
   d. Maps the saved Movie (now has an id) -> MovieResponse (DTO)

7. Controller returns MovieResponse -> Spring's Jackson converter
   serializes it to JSON -> sent back to Postman as the HTTP response body.
```

**Key principle to say in an interview:** "Entities never leave the service
layer directly — every controller returns a DTO. That decouples the wire
format from the database schema, so I can change a column name without
breaking API consumers, and I never accidentally leak fields like the
password hash."

**If asked "is this secure?":** be upfront about the gap above rather than
let them find it. "I noticed while testing that role-based access isn't
actually enforced for the admin-intended endpoints — SecurityConfig checks
`.authenticated()` instead of `.hasRole('ADMIN')`, and there's no
`@PreAuthorize` anywhere as a backstop. The Role/USER/ADMIN model and the
DB-level role storage are fully built, but the authorization rule that
should consume it was never wired up. I'd fix it by changing those
`requestMatchers` to `.hasRole("ADMIN")` and adding a quick test that
asserts a USER token gets 403 on those routes." This is a genuinely good
answer — it shows you read the actual security config instead of trusting
a comment, and you can name the exact one-line fix.

---

## Trace B: POST /api/bookings (the most complex write in the app)

This one is worth knowing cold — it's the single endpoint that touches the
most tables, runs inside one transaction, and has a real concurrency guard.

### Step 0 — Before the controller: the security filter chain

Same `JwtAuthenticationFilter` flow as Trace A. `SecurityConfig` declares:
```java
.requestMatchers("/api/bookings/**").authenticated()
```
Here `.authenticated()` is actually the *correct* rule by design — booking
is meant for any logged-in customer, not just admins, so there's no gap
on this particular endpoint (unlike Trace A's movies/theatres/shows).

### Step 1 — Controller: BookingController.createBooking()

```java
@PostMapping
public ResponseEntity<BookingResponse> createBooking(
        @AuthenticationPrincipal UserDetails userDetails,
        @Valid @RequestBody BookingRequest request) {
    Long userId = getUserId(userDetails);
    return ResponseEntity.status(HttpStatus.CREATED)
            .body(bookingService.createBooking(userId, request));
}
```

Two things happen before any business logic:
- `@Valid` runs Bean Validation on `BookingRequest` (e.g. `showSeatIds`
  must be non-empty). Fails -> 400, your code never runs.
- `@AuthenticationPrincipal UserDetails userDetails` — Spring Security
  injects the *currently authenticated* user directly as a parameter.
  Nothing here manually reads headers or decodes tokens — the filter
  already did that work upstream.

Then the private helper:
```java
private Long getUserId(UserDetails userDetails) {
    User user = userRepository.findByEmail(userDetails.getUsername())
            .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
    return user.getId();
}
```
`userDetails.getUsername()` is actually the user's **email** — that's what
this app uses as Spring Security's "username" concept end to end (it's
also the JWT's subject claim). So this does one more small DB lookup just
to translate "email from the token" into the numeric `User.id` the rest of
the system works with internally.

### Step 2 — Service: BookingService.createBooking(userId, request)

The whole method is `@Transactional` — every write below either ALL
commits together or ALL rolls back together.

**a. Fetch User and Show**
```java
User user = userRepository.findById(userId).orElseThrow(...);
Show show = showRepository.findById(request.getShowId()).orElseThrow(...);
```
Either missing -> `ResourceNotFoundException` -> `GlobalExceptionHandler`
-> **404**.

**b. Lock the requested seats — the most important line in this method**
```java
List<ShowSeat> showSeats = showSeatRepository.findByShowIdAndIdInWithLock(
        request.getShowId(), request.getShowSeatIds());
```
The repository method behind this is annotated:
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT ss FROM ShowSeat ss WHERE ss.show.id = :showId AND ss.id IN :seatIds")
```
`PESSIMISTIC_WRITE` makes Hibernate emit `SELECT ... FOR UPDATE` against
MySQL. That physically locks those exact rows for the rest of this
transaction. If a second request arrives right now asking for the *same*
seat IDs, MySQL forces that second `SELECT ... FOR UPDATE` to **block and
wait** — it literally cannot read the current status of those rows until
this transaction finishes. That's what makes the race window zero, not
just small.

**c. Sanity check on seat IDs**
```java
if (showSeats.size() != request.getShowSeatIds().size()) {
    throw new SeatNotAvailableException("One or more seats not found for this show");
}
```
Catches a `showSeatId` that doesn't belong to this show at all.

**d. Check availability (only now, after the lock is held)**
```java
List<ShowSeat> unavailable = showSeats.stream()
        .filter(ss -> ss.getStatus() != ShowSeatStatus.AVAILABLE).toList();
if (!unavailable.isEmpty()) {
    throw new SeatNotAvailableException("Seats already booked: " + takenSeats);
}
```
Because of the lock in step b, this read is guaranteed fresh — nothing
else could have changed it underneath you. `SeatNotAvailableException` is
mapped by `GlobalExceptionHandler` to `HttpStatus.CONFLICT` -> **409**,
and the message names the exact seat numbers that are taken.

**e. Compute price + reference**
```java
double totalAmount = showSeats.stream().mapToDouble(ShowSeat::getPrice).sum();
String bookingRef = generateBookingReference();
```
`generateBookingReference()` loops `"BMS-" + UUID.substring(0,6)` and
checks `bookingRepository.existsByBookingReference(ref)` until it finds
one that's unused — a simple collision-retry pattern.

**f. Create the Booking (status=PENDING)**
```java
Booking booking = Booking.builder()
        .bookingReference(bookingRef).user(user).show(show)
        .status(BookingStatus.PENDING)
        .totalAmount(totalAmount).numberOfSeats(showSeats.size())
        .build();
bookingRepository.save(booking);
```

**g. Flip the locked seats straight to BOOKED**
```java
showSeats.forEach(ss -> { ss.setStatus(ShowSeatStatus.BOOKED); ss.setBooking(booking); });
showSeatRepository.saveAll(showSeats);
```
Note: there's **no intermediate LOCKED status transition** in this
implementation — seats go directly AVAILABLE -> BOOKED inside this one
transaction. The DB row lock from step b is what's doing the "locking"
job conceptually; the `ShowSeatStatus.LOCKED` enum value exists in the
codebase but this method doesn't actually pass through it.

**h. Simulated payment**
```java
Payment payment = Payment.builder()
        .booking(booking).amount(totalAmount).paymentMethod(request.getPaymentMethod())
        .status(PaymentStatus.SUCCESS).transactionId(UUID.randomUUID().toString())
        .paidAt(LocalDateTime.now()).build();
paymentRepository.save(payment);
```
No real gateway call — hardcoded to SUCCESS. The obvious "in a real system
I'd integrate a payment gateway here, probably with a webhook callback
instead of a synchronous hardcoded success" talking point.

**i. Confirm the booking**
```java
booking.setStatus(BookingStatus.CONFIRMED);
bookingRepository.save(booking);
```

### Step 3 — Commit

The `@Transactional` proxy commits steps f-i together as one DB
transaction. Only at commit does the `PESSIMISTIC_WRITE` lock on those
ShowSeat rows release. Any request that was blocked waiting at step b now
wakes up, re-reads, correctly sees `BOOKED`, and fails cleanly at step d
instead of double-booking.

### Step 4 — Build the response

```java
return toResponse(booking, payment, showSeats);
```
`toResponse()` walks `booking.getShow().getScreen().getTheatre()` to pull
movie title, theatre name, screen name, etc. into the `BookingResponse`
DTO. This is lazy-loaded JPA navigation happening *inside* the still-open
transaction (the method is `@Transactional`, so the Hibernate session
hasn't closed yet) — if this mapping happened outside the transaction,
you'd hit a `LazyInitializationException`.

### Step 5 — Back through the controller

`ResponseEntity.status(HttpStatus.CREATED)` -> **201**, Jackson serializes
the DTO to JSON, Postman receives it.

**Key principle to say in an interview:** "I use a PESSIMISTIC_WRITE row
lock (`SELECT ... FOR UPDATE`) when fetching the requested ShowSeat rows,
inside the same @Transactional method that checks availability and writes
the booking. That closes the race window completely — a second concurrent
request for the same seats physically can't even read those rows until the
first transaction finishes, so there's no 'both passed the check' scenario.
Show separately has @Version for optimistic locking, guarding against lost
updates if multiple admin actions touch the same Show concurrently — so it's
pessimistic locking for the high-contention seat-booking path, and
optimistic locking for the lower-contention Show-update path."

**Follow-up they might ask: "what if the payment step fails?"** "Because
everything is in one @Transactional method, an exception thrown after the
seats are flipped to BOOKED — say, the payment call fails — rolls back the
ENTIRE transaction, including the seat status change. The seats revert to
AVAILABLE automatically; nothing is left in a half-booked state. That's
the main reason this is one big transactional method instead of several
separate service calls."

---

## The Layers, In One Sentence Each

- **Controller** — translates HTTP <-> Java method calls. No business logic.
- **DTO (request)** — defines + validates exactly what the client may send.
- **Service** — owns the business rules (uniqueness checks, seat locking,
  status transitions, transactions).
- **Repository** — thin interface, Spring Data JPA generates the SQL.
- **Entity/Model** — maps 1:1 (mostly) to a database table via JPA annotations.
- **DTO (response)** — defines exactly what the client gets back; never the
  raw entity.
- **Security filter** — runs before any controller, decides who you are and
  what you're allowed to do, on every single request, from the database.

## One Diagram Worth Drawing on a Whiteboard

```
Postman
  |
  v
JwtAuthenticationFilter  --(checks DB for current role)-->  MySQL.users
  |
  v
SecurityConfig authenticated()/role check (403 if it fails — note: most
routes here only check "is logged in," not "is ADMIN," see Trace A above)
  |
  v
Controller  -- (DTO in) -->  Service  -- (entity) -->  Repository  -->  MySQL
  |                             |
  |                       business rules / transactions / exceptions
  v
Controller  <-- (DTO out) --  Service
  |
  v
Postman (JSON response)
```