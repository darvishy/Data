# Nexus HUD — Multi-Spectrum Identity Telemetry Console

**Nexus HUD** is an Android application built with Kotlin and Jetpack Compose featuring a Multicolor HUD interface, biometric fingerprint authentication + master cipher security, multi-threaded CPU + OpenGL ES GPU CSV database ingestion with pause/resume controls, and visible high-speed SQLite FTS4 full-text search across millions of records.

- **Developer:** This application was developed by **Nullemperor**.
- **Telegram Channel:** [http://t.me/Nullemperor](http://t.me/Nullemperor)

---

## 📱 Direct APK Download Link

Once the GitHub Actions build and release workflow finishes, the newest `app-debug.apk` is always available via this permanent direct download URL:

👉 **[https://github.com/darvishy/Data/releases/latest/download/app-debug.apk](https://github.com/darvishy/Data/releases/latest/download/app-debug.apk)**

> **Note:** Allow approximately **7–9 minutes** after pushing changes for the GitHub Actions build and GitHub Release step to finish before opening the link.

---

## ⚙️ How to Use the GitHub Actions APK Pipeline

This repository includes an automated CI/CD workflow at `.github/workflows/build-apk.yml` configured with **Temurin Java 21**, **Gradle 9.3.1**, and **Android Gradle Plugin (AGP) 9.1.1**.

### 1. Automatic Builds on Push
Every time you push or sync changes to the **`main`** branch (for example, from Google AI Studio or via `git push origin main`), GitHub Actions automatically:
1. Sets up Java 21, Android SDK licenses, and Gradle 9.3.1.
2. Restores the persistent debug keystore from `debug.keystore.base64` so APK updates install cleanly over previous builds.
3. Builds and verifies `app/build/outputs/apk/debug/app-debug.apk`.
4. Uploads a backup workflow artifact named **`app-debug-apk`**.
5. Publishes a uniquely tagged GitHub Release (`debug-apk-build-<run_number>-<attempt>`) marked as **Latest** containing `app-debug.apk`.

### 2. Running a Build Manually (`workflow_dispatch`)
You can also trigger a fresh APK build manually at any time without pushing code:
1. Open the repository on GitHub: **[https://github.com/darvishy/Data](https://github.com/darvishy/Data)**
2. Click the **Actions** tab at the top.
3. In the left sidebar, select **Build Debug APK and Publish Release**.
4. Click the **Run workflow** dropdown button on the right, keep branch **`main`** selected, and click **Run workflow**.

---

## 🔍 How to View Build Status

1. Go to the **[Actions tab](https://github.com/darvishy/Data/actions)** of the repository.
2. Check the latest workflow run in the list:
   - 🟡 **Yellow spinner (`In progress` / `Queued`)**: The APK is currently compiling. Wait until it finishes.
   - 🟢 **Green checkmark (`Success`)**: The build and GitHub Release completed successfully.
   - 🔴 **Red cross (`Failure`)**: Click the workflow run and expand the failed step to inspect the Gradle or environment log output.

---

## 📦 Where to Find the APK Once the Process Completes

Once a workflow run shows a green checkmark, you can download the compiled APK in **three places**:

1. **Permanent Direct Download URL (Fastest — paste into Chrome on Android):**
   ```text
   https://github.com/darvishy/Data/releases/latest/download/app-debug.apk
   ```
2. **GitHub Releases Page:**
   - Visit **[https://github.com/darvishy/Data/releases](https://github.com/darvishy/Data/releases)**
   - Under the release badged **Latest** (`Debug APK Build #<run_number>`), expand **Assets** and click **`app-debug.apk`**.
3. **GitHub Actions Summary & Backup Artifact:**
   - Open any completed workflow run under **[Actions](https://github.com/darvishy/Data/actions)**.
   - Scroll down to the **Job Summary** to view the **DIRECT APK DOWNLOAD** link, or scroll to the **Artifacts** section at the bottom to download the **`app-debug-apk`** backup archive.

---

## 🗂️ Supported HUD Modules & CSV Schemas

The main grid provides 6 dedicated HUD database nodes:
`BankMeli` • `BankMelat` • `BankSaderat` • `Citizen` • `Irancell` • `Telegram`

- **BankMeli CSV Format (5 Columns):**
  `NATIONAL_CODE,CARD_NO,FULL_NAME,BIRTH_DATE,MOBILE`
- **BankMelat CSV Format (13 Columns):**
  `NATIONAL_CODE,ACCOUNT_NUMBER,FULL_NAME,FATHER_NAME,ID_NUMBER,BIRTH_DATE,CITY_NAME,PROVINCE_NAME,BIRTH_CITY,BIRTH_PROVINCE,ADDRESS,CARD_NO,MOBILE`
- **BankSaderat CSV Format (12 Columns):**
  `AccountNumber,DefineationNumber,FullName,CardNumber,Password_Hint,user_email_address,Passw,user_phone_number,Member,Username,DefineNum_Old,ID_Branch`
- **Citizen CSV Format (7 Columns):**
  `FULLNAME,BIRTHDAY,National ID,Postal Code,Phone,City,Address`
- **Telegram CSV Format (5 Columns, Semicolon-Delimited):**
  `account_id;phone;username;first_name;last_name`
