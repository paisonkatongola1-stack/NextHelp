# NextHelp — System Design

Last updated: 2026-09-08

NextHelp is a helpdesk/ticketing platform with two clients — an **Android app**
(Kotlin, Jetpack Compose) and a **web PWA** (vanilla JS + Firebase SDK) — backed
entirely by **Firebase** (Auth, Firestore, Cloud Messaging, Cloud Functions,
Hosting). There is no custom server; Firebase is the backend.

---

## 1. High-level architecture

```
┌─────────────────────┐        ┌──────────────────────────────┐
│  Android app        │        │  Web PWA (Firebase Hosting)  │
│  Compose + Hilt     │        │  public/ (app.js, sw.js)     │
└─────────┬───────────┘        └──────────────┬───────────────┘
          │           Firebase SDKs           │
          └──────────────┬────────────────────┘
                         ▼
┌─────────────────────────────────────────────────────────┐
│                    Firebase (nexthelp-af2c5)            │
│                                                         │
│  Auth          Email/password + Google (federated IdP)  │
│  Firestore     users, tickets, comments, fcmTokens      │
│  Functions     push-notification triggers (Node 20)     │
│  Messaging     FCM multicast to user device tokens      │
│  Hosting       web PWA + APK download page              │
└─────────────────────────────────────────────────────────┘
```

Both clients talk **directly to Firestore** with security rules as the
authorization layer. Cloud Functions exist only for side effects (push
notifications) that clients must not be trusted to perform.

---

## 2. Android client

Package: `com.example.nexthelp` · single-module Gradle app (`app/`).

### 2.1 Layering

```
presentation/   Screens (Compose), ViewModels, navigation, notifications UI
domain/         models (User, Ticket), repository interfaces
data/           Firestore-backed repository impls, session, FCM, uploaders
core/           util (Resource, AppConfig, TimeFormat, ImageCompressor), ui components
di/             Hilt modules (AppModule)
ui/             Material theme
```

Dependencies: Hilt DI, Credentials Manager + Google ID, Firestore (KTX),
FCM, Coil, Coroutines/Flow. Tests: JUnit4 + Turbine-style flows
(`TicketViewModelTest`, `NotificationFactoryTest`, `TimeFormatTest`).

### 2.2 Feature classes (existing)

| Feature | Key classes |
|---|---|
| **Auth** | `AuthRepository`/`AuthRepositoryImpl`, `AuthViewModel`, `LoginScreen`, `RegisterScreen`, `ForgotPasswordScreen`, `SessionManager` |
| **Google Sign-In** | Credential Manager (`GetGoogleIdOption`) in `AuthViewModel.loginWithGoogle()`, gated by `AppConfig.isGoogleSignInConfigured` |
| **Tickets** | `TicketRepository`/`TicketRepositoryImpl` (paged queries, `startAfter` cursor), `TicketViewModel`, `TicketsScreen`, `CreateTicketScreen`, `TicketDetailsScreen`, `TicketCard` |
| **Comments** | Subcollection streams under `tickets/{id}/comments`, inline images via data URLs |
| **Roles & admin** | `UserRole` (USER, SUPPORT_AGENT, SUPPORT_MANAGER, ADMIN), `AdminHome`, `AppConfig.adminEmails` provisioning |
| **Profile** | `ProfileScreen`, `UserPreferencesRepository`, `ProfileImageUploader` (compressed data URLs, no Storage) |
| **Notifications** | `NextHelpMessagingService` (FCM), `NotificationsController`, `NotificationsScreen`/`ViewModel`, `NotificationFactory` |
| **Navigation** | `NavGraph`, `NextHelpNavBar`, `Screen` (sealed routes + bottom tabs) |
| **Attachments** | `TicketAttachmentUploader` + `ImageCompressor` → Firestore data URLs (Firebase Storage intentionally removed) |
| **Platform config** | `AppConfig.fromBuildConfig()` — secrets injected from `local.properties` at build time |

### 2.3 Screen/route map

Splash → Login/Register/ForgotPassword → (role) →
- USER: Home dashboard, My Tickets, Create Ticket, Notifications, Profile
- Agent/Manager/Admin: Inbox, All Tickets (assignment + status actions), Admin console

---

## 3. Web client (PWA)

Files in `public/` served by Firebase Hosting (site `nexthelp-af2c5`):

| File | Role |
|---|---|
| `index.html` + `app.js` (~1.8k lines) | Hash-routed SPA: auth, dashboard, ticket list/create/detail, inbox, admin, profile |
| `sw.js` | Service worker: offline app shell, network-first for Firebase APIs, skip-cache for Google endpoints |
| `manifest.webmanifest` | Installable PWA |
| `download.html` | APK distribution page |
| `firebase-config.js` | Runtime Firebase config |

Notable flows:
- **Google sign-in**: `signInWithPopup` first; falls back to
  `signInWithRedirect` for standalone PWAs, in-app browsers, and popup-block
  errors; `getRedirectResult` completes the flow on return.
- **Reactivity**: single `state` object + `scheduleRender()`; Firestore
  `onSnapshot` streams for tickets and comments.
- **Update UX**: SW `updatefound` → prompt → reload on accept.

---

## 4. Data model (Firestore)

```
users/{uid}
  id, fullName, email, role, profileImageUrl?, coverImageUrl?,
  bio, phoneNumber, location, createdAt

tickets/{ticketId}
  id, ticketNumber, creatorId,
  requesterName/Phone/Email/Location,
  subject, description, category,
  priority (LOW|MEDIUM|HIGH|CRITICAL),
  status (OPEN|ASSIGNED|IN_PROGRESS|WAITING_FOR_USER|RESOLVED|CLOSED|REOPENED),
  assignedAgentId?, assignedAgentName?, teamId?,
  createdAt, updatedAt, resolvedAt?, closedAt?
  └── comments/{commentId}
        id, authorId?, authorName, content, imageUrl?, timestamp

fcmTokens/{token}
  token, userId, (platform meta)
```

Composite index: `tickets(creatorId ASC, createdAt DESC)` for "my tickets"
pagination. Collection-group index on `comments.timestamp` for activity feeds.
Attachments and avatars are stored as **compressed data URLs inside
documents** (project deliberately dropped Firebase Storage).

---

## 5. Security model

`firestore.rules` (v2) — all access requires `request.auth`:

- **users**: readable by any signed-in user; owner can create/update own doc
  but **cannot change role or email**; deletes impossible from clients.
- **tickets**: read own (or agent+); create only with own `creatorId`;
  update restricted by field whitelist (`status`, `assignedAgentId/Name`,
  timestamps, `updatedBy`) — creators and agents both; delete agent-only.
- **comments**: create with own `authorId`; edit/delete by author or agents;
  collection-group fallback read for feeds.
- **fcmTokens**: a user may only manage tokens carrying their own `userId`.
- Roles are provisioned server-side/admin-only; clients can never escalate.

Backend notifications run in Cloud Functions with admin credentials, so
delivery does not depend on client trust.

---

## 6. Notification pipeline

```
ticket written (create/assign/status)  ┐
comment created                        ┘
        │ Cloud Functions (functions/index.js)
        │  onTicketWritten / onCommentCreated
        ▼
recipients = {creator, assignedAgent} − actor
        ▼
fcmTokens where userId in recipients → multicast sendEachForMulticast
        ▼
dead tokens (unregistered/invalid) pruned
        ▼
Android: NextHelpMessagingService → NotificationFactory → NotificationsScreen
```

---

## 7. Authentication design

### Android (Credential Manager)
1. `AppConfig.webClientId` (web OAuth client, type 3) from `local.properties`
   → BuildConfig.
2. `GetGoogleIdOption.setServerClientId(webClientId)` → system account picker.
3. ID token → `repository.loginWithGoogle(idToken)` → Firebase Auth.
4. Requires the **app to be registered in Firebase with matching SHA-1/256**
   (package `com.example.nexthelp`) — this was the original failure: the
   project had no Android app registered, so Credential Manager always
   returned `DEVELOPER_ERROR`/no credentials. Fixed 2026-09-08 by registering
   the app and both signing certs.

### Web (Firebase JS SDK)
Popup → redirect fallback matrix (standalone/in-app/mobile) →
`ensureProfile` upsert into `users`. Authorized domains: `localhost`,
`*.firebaseapp.com`, `*.web.app`.

---

## 8. Testing

| Suite | Scope | Run |
|---|---|---|
| `testDebugUnitTest` (25 tests) | ViewModel flows, notification factory, time format | `./gradlew testDebugUnitTest` |
| `assembleDebug` / `assembleRelease` | Full compile + signing config | `./gradlew assemble…` |
| Functions lint | `node --check functions/index.js` | `npm run lint` in `functions/` |
| Instrumented (`AppNavigationTest`) | Compose navigation on device | `./gradlew connectedAndroidTest` (needs device) |

---

## 9. Known limitations & proposed feature classes (roadmap)

| Area | Gap | Proposal |
|---|---|---|
| Attachments | Data URLs inflate document size (1 MB doc cap) | Cloud Storage + signed URLs, or Cloud Function upload endpoint |
| Search | No full-text ticket search | Algolia/Typesense extension synced by Functions |
| SLA & escalation | No deadlines | `dueAt` field + scheduled Function escalation, priority→SLA policy class |
| Knowledge base | None | `articles` collection + editor UI + public read rules |
| Satisfaction | No CSAT | `ratings` subcollection on resolve; survey prompt in UI |
| Analytics | Console-only | Dashboards from aggregated counters (`stats` docs updated by Functions) |
| Email channel | FCM only (Android) | Web Push subscription for PWA; email via extension |
| Multi-tenancy | Single team | `teamId` exists on Ticket — add teams collection + rules scoping |
| Roles management | Admin-email list in config | Admin console role editor with audit log collection |
| Offline | PWA shell only | Firestore local persistence on Android (already available) + queued mutations |

---

## 10. Operational notes

- Secrets (`webClientId`, Firebase values, admin emails, dev admin login)
  live in `local.properties` + `secrets/`, injected as BuildConfig fields.
  Never hardcode.
- Release signing: `keystore/nexthelp-release.jks` via `keystore.properties`.
- Deploy web: `firebase deploy --only hosting`; functions:
  `firebase deploy --only functions`; rules/indexes: `firebase deploy --only firestore`.
- `google-services.json` is generated from the Firebase project — the app ID
  `1:599443092163:android:00cab176e13f075ed46876` is the real registered app
  (replaces the earlier fabricated config).
