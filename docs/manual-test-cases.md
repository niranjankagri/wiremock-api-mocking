# Manual test cases

The test cases this project automates, written out so a tester can read, review or extend them without reading the
Java. Every case has an ID, and the JUnit method that covers it starts with the same ID
(`tc122_everyAttemptCarriesTheSameIdempotencyKey`). A failure in the report leads straight to the case below.

| ID range | Area | Automated in |
|---|---|---|
| TC-101 to TC-103 | Payment requests | `PaymentRequestTest` |
| TC-111 to TC-115 | Payment responses | `PaymentResponseTest` |
| TC-121 to TC-127 | Payment retries | `PaymentRetryTest` |
| TC-131 to TC-134 | Payment network faults | `PaymentFaultTest` |
| TC-141 to TC-146 | Shipping quotes | `ShippingQuoteTest` |
| TC-151 to TC-156 | Checkout flow | `CheckoutFlowTest` |
| TC-161 to TC-162 | JSON mapping files | `MappingFilesTest` |
| TC-171 to TC-172 | Record and replay | `RecordAndReplayTest` |

## How to run one case

The client is a library with no screen, so a case is run through its test:

```
mvnw.cmd test -Dtest=PaymentRetryTest#tc122*
```

The result is in `target/surefire-reports`. To see what WireMock received, set a breakpoint after the client call and
open `http://localhost:<port>/__admin/requests`, with the port from `PAYMENT_API.getPort()`.

Default values unless a case says otherwise: 3 attempts, waits of 200 ms and 400 ms, at most 30 s, a 1-second timeout,
and a flat shipping rate of 9.95.

## Payment requests

| ID | Provider set up to | Action | Expected |
|---|---|---|---|
| TC-101 | Accept well-formed charges only | Charge order ORD-7 | Request has the `Authorization`, `Content-Type` and `Idempotency-Key` headers; the key is `ORD-7` |
| TC-102 | Accept well-formed charges | Charge 10.00 EUR | Body is exactly `orderId`, `amountCents`, `currency`, `cardToken` |
| TC-103 | Accept well-formed charges | Charge a card | URL is `/v1/payments` with no query string: the card token isn't in it |

## Payment responses

| ID | Provider set up to | Action | Expected |
|---|---|---|---|
| TC-111 | Capture, building the id from the request | Charge ORD-7 | Captured, payment id `pay_ORD-7` |
| TC-112 | Answer `402 insufficient_funds` | Charge | Declined with that reason; 1 request |
| TC-113 | Answer `401` | Charge | `PaymentRejectedException` naming HTTP 401; 1 request |
| TC-114 | Answer `400`, `403`, `404`, `409`, `422` (one run each) | Charge | `PaymentRejectedException`; 1 request |
| TC-115 | Answer `201` with: not JSON, `{}`, no status, status `pending` (one run each) | Charge | Unknown: neither paid nor failed |

## Payment retries

| ID | Provider set up to | Action | Expected |
|---|---|---|---|
| TC-121 | `503`, `503`, then capture | Charge | Captured; 3 requests |
| TC-122 | `503`, `503`, then capture | Charge ORD-7 | All 3 requests have the key `ORD-7` |
| TC-123 | Always `503` | Charge | Unknown, problem `HTTP 503`; 3 requests |
| TC-124 | Always `503` | Charge | Waits of 200 ms, then 400 ms; no wait after the last attempt |
| TC-125 | `429` with `Retry-After: 7`, then capture | Charge | Captured; one wait of 7 s |
| TC-126 | `429` with `Retry-After: 86400`, then capture | Charge | One wait of 30 s, the cap |
| TC-127 | `429` with a date in `Retry-After`, then capture | Charge | One wait of 200 ms, the normal wait |

## Payment network faults

| ID | Provider set up to | Action | Expected |
|---|---|---|---|
| TC-131 | Break the connection on every request, with each WireMock fault (one run each) | Charge | Unknown; 3 requests |
| TC-132 | Break the connection once, with each fault, then capture | Charge | Captured; 2 requests with the same key |
| TC-133 | Answer after 2 s | Charge | Unknown, problem `HttpTimeoutException`; 2 waits |
| TC-134 | Answer after 300 ms | Charge | Captured; no retry |

The four faults are: empty response, malformed response chunk, random data then close, and connection reset by peer.

## Shipping quotes

| ID | Carrier set up to | Action | Expected |
|---|---|---|---|
| TC-141 | Quote 6.50 for postcode 10115, 1200 g only | Ask for that quote | 6.50, not estimated |
| TC-142 | Quote 7.00 for postcode `1011 AB`, 1200 g only | Ask for that quote | 7.00; raw URL has `1011%20AB` |
| TC-143 | Answer `204`, `400`, `404`, `429`, `500`, `503` (one run each) | Ask for a quote | Flat rate, estimated; 1 request |
| TC-144 | Break the connection, with each fault (one run each) | Ask for a quote | Flat rate, estimated |
| TC-145 | Answer `200` with: not JSON, `{}`, a text price, a negative price (one run each) | Ask for a quote | Flat rate, estimated |
| TC-146 | Answer after 2 s | Ask for a quote | Flat rate, estimated |

## Checkout flow

| ID | APIs set up to | Action | Expected |
|---|---|---|---|
| TC-151 | Carrier quotes 6.50; provider captures | Check out 49.99 of goods | Paid; 56.49 charged, in the result and in the request |
| TC-152 | Carrier answers `503`; provider captures | Check out 49.99 of goods | Paid; flat rate added; shipping marked as estimated |
| TC-153 | Carrier quotes; provider declines `card_expired` | Check out | Declined with that reason; no payment id |
| TC-154 | Carrier quotes; provider always `503` | Check out | Payment unknown, not declined |
| TC-155 | Nothing | Check out an order of 0.00 | `IllegalArgumentException`; neither API called |
| TC-156 | Provider answers a repeated key with the existing payment | Check out the same order twice | Same payment id; both requests have the same key |

## JSON mapping files

| ID | Server set up from | Action | Expected |
|---|---|---|---|
| TC-161 | `src/test/resources/wiremock` | Check out ORD-42 | Paid as `pay_ORD-42`; shipping 4.95 from `__files` |
| TC-162 | The same files | Check out with `tok_declined` | Declined `do_not_honor`: the priority 1 mapping beats priority 5 |

## Record and replay

| ID | Setup | Action | Expected |
|---|---|---|---|
| TC-171 | Record one charge through WireMock, then stop the provider | Send the same charge | 1 stub recorded; same answer without the provider |
| TC-172 | Record one charge | Send another amount | `404` from WireMock: no recorded answer for a different request |
