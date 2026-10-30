# Owner Orchestrator — public Android app build

This is a **separate, publishable Android client** to view and control a self-hosted task queue.
No private server code, Aegis source, USA-1 commercial code, secrets, token, or personal data
is included in this repository.

Features: secure token storage using Android Keystore, HTTPS-only controller API,
view queue, submit/cancel with confirmation, open ordinary ChatGPT by user action.

## GitHub Actions
GitHub-hosted `ubuntu-24.04` standard runners build the Android debug APK and run
source-level security checks on pushes to `main` and manual dispatch.
Runs on **public repositories only**. No release keys, model API credentials or
personal access tokens are needed. Run is restricted to an Android application
build, not an external service or general-purpose compute pool.

After a successful run, open the workflow run and download the
`owner-orchestrator-debug-apk` artifact, which contains `app-debug.apk`.
This is only **debug-signed**, not an owner-signed production build.

## Runtime
Enter your own HTTPS controller URL and operator token **inside the app**.
Do not enter secrets in GitHub Actions, workflow inputs or source files.
Without a live controller URL, app connection checks will fail gracefully;
you can still install it and review its interface.
The app does not scrape the ChatGPT browser UI or consume OAuth tokens from ChatGPT.

## Security
The public code does not grant access to private GitHub repositories or six
build/test servers. The operator token is generated and stored outside GitHub;
the APK has no baked-in credentials. Keep all project-specific workflows in
their private authoritative repositories. MIT-compatible third-party licensing
and production signing require a separate release review.

## Verified scope
The structure checks can run without Android SDK. Real Android compilation
and end-to-end APK installation must be confirmed by the GitHub Actions run
and physical Android device, respectively. No compiled APK is included here.
