# NextHelp

A support-ticket Android app built with Kotlin and Jetpack Compose. Users file
and track support requests; agents and admins triage, respond to, and resolve
them — all in real time via Firebase.

## Features

- **Auth** — email/password + Google Sign-In, password reset, session restore
- **Tickets** — create, browse, search, filter by status/priority, paginated history
- **Real-time sync** — Firestore snapshot listeners keep lists, details, and
  comments live without manual refresh
- **Comments** — per-ticket comment threads stored in a `comments` subcollection
  (scales past the 1 MiB document limit of embedded arrays)
- **Notifications** — derived in-app feed with unread badge and per-type
  preferences, plus FCM push notifications (token synced to `fcmTokens/{token}`
  in Firestore for backend delivery)
- **Roles** — regular users see their own tickets; support agents/managers/admins
  see everything and can update status
- **Adaptive UI** — Material 3, bottom bar on phones, navigation rail on expanded
  screens, motion and skeleton loading states

## Tech stack

| Layer      | Tools                                                        |
|------------|--------------------------------------------------------------|
| UI         | Jetpack Compose, Material 3, adaptive layouts                |
| DI         | Hilt                                                         |
| Async      | Coroutines + Flow                                            |
| Backend    | Firebase Auth, Cloud Firestore, Cloud Messaging              |
| Storage    | DataStore (preferences)                                      |
| Testing    | JUnit4, kotlinx-coroutines-test                              |

## Project structure

```
app/src/main/java/com/example/nexthelp/
├── core/            # Shared utilities, UI components, theming helpers
├── data/            # Repositories, session, preferences, notifications, FCM
├── di/              # Hilt modules
├── domain/          # Models + repository interfaces
└── presentation/    # Screens, ViewModels, navigation (per feature)
```

Clean-ish architecture: `presentation` depends on `domain`; `data` implements
domain interfaces and talks to Firebase.

## Getting started

1. Open the project in Android Studio.
2. Provide secrets in `local.properties` (never committed):

   ```properties
   nexthelp.firebase.apiKey=...
   nexthelp.firebase.applicationId=...
   nexthelp.firebase.projectId=...
   nexthelp.firebase.storageBucket=...
   nexthelp.adminEmails=a@example.com,b@example.com

   # Google Sign-In (OAuth 2.0 *Web* client ID from Google Cloud console)
   nexthelp.webClientId=...

   # Debug-only convenience admin login
   nexthelp.dev.adminEmail=...
   nexthelp.dev.adminPassword=...
   ```

   Alternatively drop in a standard `google-services.json` and re-enable the
   `google-services` plugin; the app falls back to manual init from BuildConfig
   when it is absent.

3. For Google Sign-In, also create an **Android OAuth client** in the same
   GCP project with the app's `applicationId` and your debug/release SHA-1
   (`./gradlew signingReport`), then enable Google as a sign-in provider in
   the Firebase console.
4. Run: `./gradlew :app:installDebug`

## Firebase notes

- **Composite index**: non-agent queries combine `whereEqualTo(creatorId)` with
  `orderBy(createdAt desc)` — create that composite index if the console links one.
- **Collection group index** for `comments` is auto-created for single-field
  queries (`timestamp`).
- **Push notifications**: `functions/` hosts Firestore triggers that deliver
  pushes when tickets are created/assigned/reassigned or change status, and when
  comments arrive. Deploy with `firebase deploy --only functions`. The client
  stamps `updatedBy` on mutations and `authorId` on comments so senders don't
  get notified about their own actions; invalid FCM tokens are pruned after a
  failed send.
- **Firestore rules** must allow:
  - ticket read/create scoped by `creatorId` vs. agent role
  - `tickets/{id}/comments` subcollection read/write for viewers of the ticket
  - `fcmTokens` write restricted to the token owner's signed-in user id

## Testing & CI

```bash
./gradlew :app:testDebugUnitTest   # JVM unit tests (ViewModels, notification logic)
./gradlew :app:assembleDebug       # Compile check
```

GitHub Actions workflow (`.github/workflows/android-ci.yml`) runs unit tests and
a debug build on every push/PR to `main`.

## Roadmap ideas

- Agent assignment flow (`assignedAgentId` field exists; no UI yet)
- Ticket attachments via Firebase Storage
- Crashlytics + analytics
- Server-side search (Algolia/Typesense extension) once client-side filtering
  stops scaling
