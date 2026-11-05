# Framework design

Why the project is built the way it is. The README says what it does and how to run it.

## 1. Goals

1. Show how to test code that depends on third-party APIs, including the failures a real sandbox won't produce on
   demand: dropped connections, timeouts, rate limits, broken answers.
2. Keep the tests honest: real clients, real HTTP on a local port, and a run that fails when the client sends
   something the stub wasn't written for.
3. Keep the tests fast and independent: no Docker, no accounts, no network, and no real waiting except in the
   timeout tests.

## 2. The parts

| Part | Role | Tools |
|---|---|---|
| `CheckoutService` | The one entry point: asks for a shipping price, then charges goods plus shipping | Plain Java |
| `PaymentClient` | Calls the payment provider: retries, idempotency key, reads the answer | JDK `HttpClient` |
| `ShippingClient` | Calls the carrier: one attempt, falls back to a flat rate | JDK `HttpClient` |
| `RetryPolicy`, `Sleeper` | How often and how long apart to try; how to wait | Plain Java |
| `JsonBodies` | Reads and writes JSON for both clients | Jackson |
| `stubs/` | What each API does next and what it received | WireMock Java API |
| `support/` | Test data, the real clients pointed at WireMock, server settings | JUnit 6 |
| `src/test/resources/wiremock` | Stubs as JSON files, for `MappingFilesTest` | WireMock mapping format |

## 3. The app is small on purpose

It has exactly the features that make API mocking interesting: an API where a retry can do harm (a payment), an API
where a failure can be absorbed (a shipping price), and a flow that joins them. There is no web layer and no database,
because neither would add a single mocking idea. The APIs are invented and modelled on common payment and carrier
APIs, so nothing here claims to match one vendor.

## 4. The decisions the tests protect

**A payment has three outcomes, not two.** After a timeout or a broken connection, the client can't know whether the
provider took the money. `PAYMENT_UNKNOWN` says exactly that. Calling it "failed" would invite the customer to pay
again, and that is the bug most of `PaymentFaultTest` and `PaymentRetryTest` exists to prevent.

**One idempotency key per order, not per attempt.** The key is the order number, and the request is built once, before
the retry loop. A repeated attempt, or a repeated checkout, then reaches the provider as the same payment.

**Retry only what can change.** `429` and `5xx` can have a different answer next time. Other `4xx` answers can't, so
they stop at once: a wrong API key becomes a loud `PaymentRejectedException`, because it's a configuration fault and not
a customer's problem.

**The provider doesn't control the wait completely.** `Retry-After` is respected, up to the policy's 30 seconds. The
date form of the header falls back to the normal wait.

**Shipping degrades, payment doesn't.** A wrong shipping price is cheap to correct and a lost sale is not, so the
carrier gets one attempt and any failure gives the flat rate, marked as estimated. A negative price counts as a failure
too, because it would lower the total charged.

## 5. Test environment

- **Two servers, free ports.** `MockedApisTest` registers one `WireMockExtension` per API with `dynamicPort()`, so
  parallel builds can't clash and a stub for one API can't answer a call to the other.
- **One server per test class.** The extensions are static: starting a server per test would dominate the run time.
  WireMock resets the stubs and the request log before each test, so tests stay independent.
- **Strict by default.** `MockServers` turns on `failOnUnmatchedRequests`, and the stubs match well-formed requests
  only. A client that loses a header or calls the wrong path fails the test, even if its assertions would pass.
- **No sleeping.** `Sleeper` is an interface, and `RecordingSleeper` writes the waits down. Retry tests run in
  milliseconds and can assert the waits themselves (`tc124` to `tc127`).
- **Real timeouts stay real.** `tc133`, `tc134` and `tc146` use WireMock delays against a 1-second timeout. One second
  leaves room for a slow CI runner, and those tests account for most of the suite's time.

## 6. Stub objects

Tests never call WireMock directly. `provider.unavailableThenCaptures(2)` says what the test is about, and the
scenario states behind it stay in `PaymentProviderStub`. This is the same idea as a page object: when the API changes,
one class changes. The only WireMock type tests see is `Fault`, as a parameter.

`RecordAndReplayTest` is the exception: recording is the subject of that test, so its WireMock calls are the point.

The paths and header names appear in both the client and the stub, on purpose. A shared constant would let a typo pass
on both sides.

## 7. Java stubs or JSON files

Both are used. Java stubs fit tests that vary behaviour per test, and they get refactoring support from the IDE. JSON
mappings suit stable stubs that non-developers maintain, and they keep large bodies out of the code. `MappingFilesTest`
covers that style, with stub priority and a body in `__files`.

## 8. Record and replay

`RecordAndReplayTest` uses a second WireMock as the "real" provider, so the test needs no network. The recording
matches on the request body, so a new amount gets a `404` and not an old answer (`tc172`).

Two things to know when recording a real API: review the stubs for secrets and personal data before committing them,
and remember that a recording is a snapshot that goes stale. A response with no text `Content-Type` is written to
`__files` as a binary body, which is how this suite once filled `src/test/resources` on every run.

## 9. How I know the tests can fail

I broke the client on purpose in eight ways and checked that each break failed the suite: a new idempotency key per
request, no `Authorization` header, `503` not retried, an unreadable body counted as paid, no cap on `Retry-After`,
negative shipping prices accepted, a space sent as `+`, and an unknown payment shown as declined.

One break survived at first. WireMock decodes `+` and `%20` to the same query value, so `tc142` passed with the wrong
encoding. It now also asserts the raw URL.

## 10. Limits

- A mock only knows what it was told, so these tests can't notice the real API changing. Contract tests cover that:
  see [pact-contract-testing](https://github.com/niranjankagri/pact-contract-testing).
- `PAYMENT_UNKNOWN` is where this client stops. A real system would follow up, for example by asking the provider for
  the payment's state, and that isn't built here.
- The tests use HTTP/1.1 on localhost, so TLS, proxies and real network timing aren't covered.
