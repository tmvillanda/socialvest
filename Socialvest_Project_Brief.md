# SOCIALVEST --- Agent Project Brief

**Project:** Socialvest\
**Product category:** Social Investment & Financial Learning Platform\
**Platform:** Android\
**Primary language:** Kotlin\
**Project context:** UAS Pemrograman Mobile\
**Document purpose:** Give an AI coding agent or software-development
agent a clear, consistent understanding of what Socialvest is, what must
be built, how the modules connect, and what must remain out of scope.

------------------------------------------------------------------------

## 1. Product overview

Socialvest is an Android application that combines three core
experiences in one product:

1.  **Investment Management** --- users manually record investment
    assets and transactions, then view portfolio summaries and
    gains/losses.
2.  **Social Community** --- users publish posts, browse a community
    feed, like and comment on posts, and follow other investors.
3.  **Learning Center** --- users browse investment courses, watch
    learning videos, and track course/module progress.

The product idea is: **Invest Together, Learn Together, Grow Together.**

Socialvest is intended to make investment tracking, peer interaction,
and financial education available in one mobile application. It is not
intended to execute real investment trades.

### Product positioning

Socialvest is a **social investment tracking and financial learning
platform**, not: - a brokerage or securities trading application; - a
crypto exchange; - a payment or wallet application; - an automated
investment adviser; - a platform that guarantees investment returns.

Investment data is entered by the user or supplied through an explicitly
configured data source. Do not imply that manually entered prices are
live market prices.

------------------------------------------------------------------------

## 2. Primary goals

Build a working Android MVP that demonstrates:

-   account registration, login, and logout;
-   user profile management;
-   manual investment asset and transaction management;
-   portfolio value and profit/loss calculations;
-   a community feed with posts, likes, and comments;
-   course catalogue, course details, video playback, and learning
    progress;
-   coherent navigation across the three main product areas;
-   Firebase-backed data persistence and access control.

The three core areas --- **Invest, Social, Learn** --- must be
functional and connected. Do not spend time on advanced features before
these work reliably.

## 3. Target users and roles

### User / Investor

-   Creates an account and manages their profile.
-   Records their own investment assets and transactions.
-   Views their own portfolio.
-   Publishes community posts, likes and comments, follows other users.
-   Browses courses and tracks learning progress.

### Admin

-   Reviews reported content and handles moderation.
-   Manages user status and course content.
-   Has privileged access that ordinary users must not be able to grant
    themselves.

### Instructor / Course manager

-   Creates or manages assigned course content and modules, if this role
    is implemented.
-   For a constrained UAS MVP, course data may instead be seeded or
    managed by an administrator through Firebase Console.

**MVP simplification:** It is acceptable to implement only the User role
in the Android client and manage course/admin data through Firebase
Console. Do not build a full admin application unless explicitly
requested.

------------------------------------------------------------------------

## 4. Scope and priorities

### Must-have MVP

  -----------------------------------------------------------------------
  Area                                Required capability
  ----------------------------------- -----------------------------------
  Authentication                      Register, login, logout, session
                                      handling

  Profile                             View and edit basic profile

  Home                                Overview and navigation to core
                                      modules

  Investment                          Create, read, update, delete assets

  Transactions                        Record buy/sell transactions
                                      manually

  Portfolio                           Calculate and display holdings,
                                      value, and profit/loss

  Social                              Feed, create post, like, comment

  Learning                            Course catalogue, course detail,
                                      video playback

  Progress                            Record module/course completion or
                                      last watched position

  Security                            Firebase rules prevent cross-user
                                      access to private financial data
  -----------------------------------------------------------------------

### Optional if time permits

-   Follow/unfollow investors.
-   Push notifications.
-   Bookmark courses.
-   Report posts/comments and basic moderation workflow.
-   Course quiz.
-   Portfolio chart.
-   Search and category filters.

### Explicitly out of scope for MVP

-   Real stock/crypto trading or order execution.
-   Brokerage or exchange account connection.
-   Deposits, withdrawals, payments, or digital wallet.
-   Automated buy/sell recommendations.
-   Claims of guaranteed returns.
-   Real-time market data unless a legitimate provider and API are
    deliberately integrated.
-   Private messaging or live streaming.
-   Paid course checkout and payment processing.

When a feature is not implemented, show a clear unavailable/coming-later
state rather than a fake working control.

------------------------------------------------------------------------

## 5. Main navigation and screens

Use a five-item bottom navigation:

1.  **Home**
2.  **Invest**
3.  **Social**
4.  **Learn**
5.  **Profile**

### Suggested screen map

-   Splash / launch
-   Login
-   Register
-   Home dashboard
-   Investment portfolio list
-   Add/edit asset
-   Asset detail
-   Transaction history
-   Add transaction
-   Social feed
-   Create/edit post
-   Post detail with comments
-   Other user's public profile
-   Course catalogue
-   Course detail
-   Video lesson player
-   Learning progress
-   Own profile
-   Settings / privacy
-   Notifications (optional)

### Home dashboard content

-   Greeting and profile image/name.
-   Portfolio summary (only the signed-in user's own data).
-   Profit/loss summary.
-   Recent community posts.
-   Course recommendations or featured courses.
-   Shortcuts to Invest, Social, and Learn.

Do not expose a user's portfolio value to other users by default. Public
sharing must be explicit and limited to information the user chooses to
publish.

------------------------------------------------------------------------

## 6. Core functional requirements

### 6.1 Authentication and profile

-   Register with email/password.
-   Login and logout.
-   Restore authenticated session on app launch.
-   Allow editing display name, username, bio, and profile photo if
    supported.
-   Use Firebase Authentication UID as the canonical user identifier.
-   Never store plaintext passwords in Firestore.

### 6.2 Investment management

Users can manually create and manage assets with fields such as: - asset
name / ticker; - asset category (stock, cryptocurrency, mutual fund,
gold, bond, other); - quantity / units; - average acquisition price or
transaction-derived cost basis; - current price entered manually; -
currency (default IDR); - timestamps.

Users can record transactions: - BUY or SELL; - quantity; - unit
price; - fee (optional); - transaction date; - optional notes.

The app should derive holdings and cost basis from transaction history
where feasible. Do not rely on an editable portfolio total as the sole
source of truth.

Basic calculations: - Current value = current quantity × current unit
price. - Unrealized P/L = current value − remaining cost basis. - Return
(%) = unrealized P/L ÷ remaining cost basis × 100%, when cost basis is
greater than zero.

Clearly distinguish: - **Unrealized P/L:** gain/loss on holdings not yet
sold. - **Realized P/L:** gain/loss from units sold.

For the MVP, a simpler average-cost calculation is acceptable if it is
implemented consistently and documented. Validate that a sell
transaction cannot exceed the user's available quantity. Handle
zero/negative values and division by zero.

Use decimal-safe numeric handling where possible. Avoid floating-point
rounding errors in currency calculations; use integer minor units or an
appropriate decimal representation and format values for display.

### 6.3 Social community

-   Display a paginated feed of published posts.
-   Create text posts; image attachments are optional.
-   Like/unlike posts; prevent duplicate likes by the same user.
-   Add and display comments.
-   Show author name/avatar and post timestamp.
-   Allow navigation to a public profile.
-   Optional follow/unfollow.
-   Optional report content.

Posts should distinguish user opinion/experience from verified
educational content. Do not present community posts as verified
financial advice.

### 6.4 Learning Center

-   Display published courses.
-   Course detail includes title, description, thumbnail, level,
    category, instructor, and modules.
-   Users can start or enroll in a course.
-   Play course videos from authorized URLs or an approved embed/player.
-   Save progress (completed state and/or last playback position).
-   Resume a lesson where technically supported.

For the MVP, courses can be seeded in Firestore and video links can
point to content the project owner has permission to use. Do not
download or re-upload copyrighted videos without permission.

### 6.5 Notifications (optional)

Potential events: - like received; - comment received; - new follower; -
course reminder or announcement.

Only implement push notifications if time and Firebase setup allow it.
In-app notifications can be a simpler first step.

------------------------------------------------------------------------

## 7. Data model --- Firestore

Firestore is document-based, not a relational SQL database. Use document
IDs and reference fields to link records. Adapt the exact schema to the
implementation, but preserve ownership and relationships.

Suggested collections:

### `users/{uid}`

``` json
{
  "uid": "Firebase Auth UID",
  "fullName": "Example User",
  "username": "example",
  "email": "user@example.com",
  "photoUrl": "",
  "bio": "",
  "role": "user",
  "isActive": true,
  "createdAt": "server timestamp"
}
```

### `portfolios/{portfolioId}`

``` json
{
  "userId": "uid",
  "portfolioName": "My Investment",
  "currency": "IDR",
  "createdAt": "server timestamp"
}
```

### `assets/{assetId}`

``` json
{
  "userId": "uid",
  "portfolioId": "portfolioId",
  "assetName": "Example Asset",
  "ticker": "EXAMPLE",
  "category": "stock",
  "quantity": 10,
  "averageBuyPrice": 10000,
  "currentPrice": 10500,
  "currency": "IDR",
  "updatedAt": "server timestamp"
}
```

### `transactions/{transactionId}`

``` json
{
  "userId": "uid",
  "assetId": "assetId",
  "type": "BUY",
  "quantity": 10,
  "pricePerUnit": 10000,
  "fee": 0,
  "transactionDate": "timestamp",
  "createdAt": "server timestamp"
}
```

### `posts/{postId}`

``` json
{
  "userId": "uid",
  "content": "Post text",
  "imageUrl": "",
  "category": "stock",
  "status": "published",
  "likeCount": 0,
  "commentCount": 0,
  "createdAt": "server timestamp"
}
```

### `posts/{postId}/comments/{commentId}`

``` json
{
  "userId": "uid",
  "content": "Comment text",
  "createdAt": "server timestamp"
}
```

### `posts/{postId}/likes/{uid}`

``` json
{
  "userId": "uid",
  "createdAt": "server timestamp"
}
```

Use the authenticated UID as the like document ID to prevent duplicate
likes.

### `follows/{followerId_followingId}`

``` json
{
  "followerId": "uid",
  "followingId": "other uid",
  "createdAt": "server timestamp"
}
```

### `courses/{courseId}`

``` json
{
  "title": "Investment for Beginners",
  "description": "Course description",
  "thumbnailUrl": "",
  "instructorId": "admin uid",
  "category": "beginner",
  "level": "beginner",
  "isPublished": true,
  "createdAt": "server timestamp"
}
```

### `courses/{courseId}/modules/{moduleId}`

``` json
{
  "title": "Introduction to Investment",
  "videoUrl": "authorized video URL",
  "durationSeconds": 600,
  "order": 1
}
```

### `enrollments/{enrollmentId}`

``` json
{
  "userId": "uid",
  "courseId": "courseId",
  "status": "in_progress",
  "enrolledAt": "server timestamp"
}
```

### `progress/{progressId}`

``` json
{
  "userId": "uid",
  "courseId": "courseId",
  "moduleId": "moduleId",
  "lastPositionSeconds": 120,
  "completed": false,
  "updatedAt": "server timestamp"
}
```

### Optional collections

-   `notifications/{notificationId}`
-   `reports/{reportId}`

**Data-model guidance** - Private assets and transactions must be
queried by the authenticated `userId`. - Public profiles should expose
only approved public fields. - Do not store all comments or likes in a
single growing post document. - Use pagination for feeds and course
lists. - Use server timestamps. - Add Firestore composite indexes when
required by queries. - Keep denormalized counters consistent using
trusted transactions or Cloud Functions where appropriate. - Do not
trust client-provided role, owner ID, or moderation status.

------------------------------------------------------------------------

## 8. Technical architecture

### Recommended stack

-   Android Studio
-   Kotlin
-   XML layouts + Material Components (unless the existing project
    already uses Jetpack Compose; do not mix UI systems without a
    reason)
-   MVVM architecture
-   Repository pattern
-   Kotlin Coroutines / Flow where appropriate
-   Firebase Authentication
-   Cloud Firestore
-   Firebase Storage for user-uploaded images, if used
-   Firebase Cloud Messaging (optional)
-   YouTube embed / authorized video provider
-   MPAndroidChart or another maintained chart library (optional)

### Suggested package structure

``` text
com.socialvest.app/
├── data/
│   ├── model/
│   ├── repository/
│   └── datasource/
├── domain/
│   └── usecase/
├── ui/
│   ├── auth/
│   ├── home/
│   ├── investment/
│   ├── social/
│   ├── learning/
│   ├── profile/
│   └── notification/
├── viewmodel/
└── utils/
```

Use a single consistent architecture. Keep Firebase calls inside data
sources/repositories rather than placing database logic directly in
Activities or Fragments.

### General data flow

`Activity/Fragment → ViewModel → Repository → Firebase`

UI should observe state from the ViewModel and render loading, success,
empty, and error states. Avoid blocking the main thread.

------------------------------------------------------------------------

## 9. UI/UX direction

### Brand

-   Name: Socialvest
-   Tagline: "Invest Together, Learn Together, Grow Together."
-   Visual direction: modern fintech, clear, trustworthy, friendly.
-   Suggested palette: navy blue as primary, emerald green as secondary,
    neutral light/dark surfaces.
-   Use green/red for gain/loss only with text or icons as additional
    cues.
-   Support readable typography, touch targets, loading indicators,
    empty states, and error messages.

### Navigation

Bottom navigation: - Home - Invest - Social - Learn - Profile

Maintain consistent navigation and avoid duplicate destinations. The
selected tab should be visually clear.

### UX rules

-   Never show fake market prices or imply prices are live.
-   Clearly label sample/demo data.
-   Confirm destructive actions such as deleting an asset or post.
-   Provide validation messages on forms.
-   Show an empty state when a user has no assets, posts, or courses.
-   Keep private portfolio information hidden from other users by
    default.

------------------------------------------------------------------------

## 10. Security, privacy, and responsible finance

Security is a core requirement, not a final polish step.

-   Use Firebase Authentication for identity.
-   Firestore Security Rules must enforce ownership for private
    portfolios, assets, transactions, progress, and profile edits.
-   Users must not be able to grant themselves admin/instructor
    privileges.
-   Use trusted server-side mechanisms for privileged operations.
-   Validate all user input and restrict file upload types and sizes.
-   Do not store passwords or secret API keys in Firestore or source
    code.
-   Do not ship Firebase Admin credentials in the Android app.
-   Avoid exposing private portfolio data in community feeds.
-   Provide reporting/moderation mechanisms if community content is
    public.
-   Provide account/data deletion handling appropriate to the project
    scope.

### Required disclaimer

Display a clear notice in an appropriate place, such as onboarding,
investment screens, or settings:

> Informasi, diskusi, dan materi di Socialvest disediakan untuk tujuan
> informasi dan edukasi, bukan merupakan rekomendasi atau nasihat
> keuangan untuk membeli maupun menjual instrumen investasi. Keputusan
> investasi merupakan tanggung jawab masing-masing pengguna. Harga yang
> dimasukkan secara manual bukan harga pasar real-time.

------------------------------------------------------------------------

## 11. Testing and acceptance criteria

Use functional / black-box testing and test at least the following:

1.  A user can register, log in, and log out.
2.  A logged-in user can create, edit, view, and delete their own asset.
3.  A user can record buy/sell transactions and see consistent holdings.
4.  Portfolio value and P/L calculations are correct for known test
    cases.
5.  A user cannot read or modify another user's private investment
    records.
6.  A user can publish a post.
7.  A user can like and unlike a post without duplicate likes.
8.  A user can add and view comments.
9.  A user can browse course catalogue and course details.
10. A user can play an authorized course video.
11. Learning progress is saved and can be retrieved.
12. Bottom navigation opens the correct module.
13. Loading, empty, validation, and error states are handled.
14. App does not crash during the primary demo flow.

### Demo scenario

1.  Register or log in.
2.  Open Home.
3.  Add an investment asset and transaction.
4.  Show the portfolio calculation.
5.  Create a community post.
6.  Like and comment using a second test account, if available.
7.  Open a course and play a video.
8.  Show saved learning progress.
9.  Open Profile and Settings.

------------------------------------------------------------------------

## 12. Development workflow for the coding agent

Follow this order unless the project repository already contains a
working implementation that should be preserved:

1.  **Inspect the existing project first.** Identify whether it uses XML
    or Compose, current package name, Gradle setup, Firebase
    configuration, and existing screens. Do not overwrite working code
    blindly.
2.  **Report the current project structure and propose a small
    implementation plan.**
3.  **Establish app theme, navigation, and reusable UI components.**
4.  **Implement authentication and session handling.**
5.  **Implement Firestore models, repositories, and security rules.**
6.  **Implement investment CRUD and calculations.**
7.  **Implement social feed, posts, likes, and comments.**
8.  **Implement course catalogue, video playback, and progress.**
9.  **Integrate the modules in the Home dashboard and bottom
    navigation.**
10. **Test all acceptance criteria and fix critical issues.**
11. **Document Firebase setup, required indexes/rules, demo accounts or
    seed data, and how to run the app.**

### Agent behavior requirements

-   Do not invent Firebase project IDs, API keys, package names, or
    credentials.
-   If required configuration is missing, identify the exact file or
    console setting needed.
-   Prefer incremental changes and compile/test after each major module.
-   Preserve existing working code and project conventions.
-   Do not add unrelated features.
-   Do not use fake backend success responses for features presented as
    functional.
-   If a feature cannot be completed because a credential, API, or
    service is unavailable, explain the limitation and provide a safe
    placeholder state.
-   Keep implementation consistent with this brief. If a change in scope
    is necessary, explain why before making broad architectural changes.

------------------------------------------------------------------------

## 13. Definition of done

The Socialvest MVP is considered ready for UAS demonstration when:

-   the Android project builds and launches;
-   authentication works;
-   the five navigation destinations are accessible;
-   investment records persist and calculations pass test cases;
-   social posts, likes, and comments persist;
-   courses and video lessons can be browsed and played;
-   learning progress persists;
-   private user data is protected by Firestore rules;
-   the primary demo flow works without crashes;
-   setup and known limitations are documented.

**Final product principle:** deliver a small but genuinely working
Socialvest experience. The Investment, Social, and Learning modules must
each work and feel like parts of one coherent application.
