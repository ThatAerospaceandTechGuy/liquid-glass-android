# The download site

One page and one APK. `worker.js` is two lines: every request is handed to the
Workers assets binding, which serves `public/` with the right content types
(`.apk` → `application/vnd.android.package-archive`).

```
site/
  worker.js              export default { fetch }  →  env.ASSETS.fetch(request)
  deploy.json            Freebuff Sites manifest (entrypoint + assets)
  wrangler.jsonc         Cloudflare Workers config for `npx wrangler deploy`
  public/
    index.html           the page
    SHA256SUMS.txt       rewritten by every build
    liquid-glass-launcher.apk   build output — gitignored, not committed
```

`public/index.html` reads the size from the APK's `Content-Length` and the hash
from `SHA256SUMS.txt` at load time, so a new build never leaves the page quoting
an old checksum.

## Built and deployed by CI

`.github/workflows/build.yml` runs on every push to `main`, on `v*` tags, on
pull requests, and on demand:

1. JDK 17 + Android SDK 35, then `gradle wrapper --gradle-version 8.9` — the
   wrapper JAR is binary and is not committed.
2. `./gradlew :app:assembleRelease` (R8 full mode, resource shrinking).
3. Signs the APK, runs `apksigner verify`, copies it into `public/` and rewrites
   `SHA256SUMS.txt`.
4. Uploads the APK and the whole `site/` folder as workflow artifacts.
5. On a `v*` tag, attaches the APK to that tag's GitHub release.
6. On `main`, runs `npx wrangler deploy` in this folder when
   `CLOUDFLARE_API_TOKEN` is set.

### Secrets

All optional — the workflow still builds and packages without them.

| Secret | Purpose |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | Base64 of a PKCS12/JKS release keystore. |
| `SIGNING_STORE_PASSWORD` | Keystore password. |
| `SIGNING_KEY_ALIAS` | Key alias inside it. |
| `SIGNING_KEY_PASSWORD` | Key password. |
| `CLOUDFLARE_ACCOUNT_ID` | Account that owns the Worker. |
| `CLOUDFLARE_API_TOKEN` | Token with Workers Scripts:Edit, plus Workers Assets read/write. |

Without the signing secrets the build is signed with a throwaway key generated
on the runner: the page still downloads an installable APK, and the workflow
does **not** attach it to a release (a differently signed build cannot upgrade
an installed one).

Without `CLOUDFLARE_API_TOKEN` the deploy step is skipped with a notice — take
the `liquid-glass-site` artifact and publish it yourself.

```bash
# Cloudflare, with your own account
cd site && npx wrangler@4 deploy

# Freebuff Sites
#   manifest site/deploy.json, built from the repo root
```

## Why the APK is not committed

It is a build product, and a repository that stores one goes stale the moment
the source changes. CI builds it from the same commit it deploys.
