# Play Store Release Guide

## Package and App ID

- Application ID: `com.goriant.jidelite`
- Namespace: `com.goriant.jidelite`
- Test Application ID: `com.goriant.jidelite.test`

## 1) Create Upload Keystore (one-time)

```powershell
keytool -genkeypair -v `
  -keystore C:\secure\goriant-upload.jks `
  -alias upload `
  -keyalg RSA -keysize 2048 -validity 10000
```

Keep this keystore and passwords safe. Do not commit the keystore to git.

## 2) Configure Signing Secrets

1. Copy `keystore.properties.example` to `keystore.properties`.
2. Fill in real values:

```properties
storeFile=C:/secure/goriant-upload.jks
storePassword=your_store_password
keyAlias=upload
keyPassword=your_key_password
```

Alternative: use environment variables instead of file:

- `ANDROID_UPLOAD_STORE_FILE`
- `ANDROID_UPLOAD_STORE_PASSWORD`
- `ANDROID_UPLOAD_KEY_ALIAS`
- `ANDROID_UPLOAD_KEY_PASSWORD`

## 3) Bump Version For Each Upload

Edit [`app/build.gradle`](../app/build.gradle):

- Increase `versionCode` (must be greater than last uploaded build).
- Update `versionName` (for user-facing release label).

## 4) Build Release Bundle (.aab)

```powershell
.\gradlew.bat clean bundleRelease
```

Output file:

- `app/build/outputs/bundle/release/app-release.aab`
- If signing secrets are missing, this `.aab` is unsigned and cannot be uploaded to Play Console.

## 5) Validate Before Upload

```powershell
.\gradlew.bat testDebugUnitTest connectedDebugAndroidTest
```

## 6) Upload To Play Console

1. Create app with package name `com.goriant.jidelite`.
2. Go to `Release > Production` (or internal testing first).
3. Upload `app-release.aab`.
4. Fill release notes, content rating, privacy policy, data safety, and screenshots.
5. Roll out.
