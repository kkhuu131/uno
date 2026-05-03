# UNO — notes for agents

## API routes (`/api`)

- **POST `/games`** — Create game; returns `gameId`.
- **GET `/games/{gameId}`** — Snapshot: `players` (hands as `CardView` lists; `handIndex` matches array index), `topDiscard`, `activeColor`, `currentPlayerIndex`, `status` (`IN_PROGRESS` | `FINISHED`), `winnerPlayerIndex` / `winnerName` when done.
- **POST `/games/{gameId}/play`** — Body: `playerIndex`, `handIndex`, optional `chosenColor` for wilds. **409** if game already finished. **400** + `ErrorResponse` for rule violations.
- **POST `/games/{gameId}/draw`** — Body: `playerIndex`. Returns `drawnCard` + `game` snapshot. **409** when finished; **400** for deck/state errors caught in controller.

## Known gotchas

- **Spring Boot 4:** `@WebMvcTest` lives in `org.springframework.boot.webmvc.test.autoconfigure`, not `...web.servlet`. Use `@MockitoBean` for mocks in slice tests.
- **Slice tests** that load `WebConfig` need `@Import` of `GameSnapshotMapper` and `ApiExceptionHandler` where relevant, plus `@EnableConfigurationProperties(CorsProperties.class)`.
- **`TestGames.register`** (tests only) reflects into `GameSessionService`’s map so we don’t expose a production hook for injecting games.
- **CORS** is configured for `/api/**` via `app.cors.allowed-origins` in `application.yaml` (defaults include Vite/React dev ports).
