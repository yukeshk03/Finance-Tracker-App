# How to Build Your APK on GitHub (No Android Studio Needed)

## What you need
- A free GitHub account (github.com)
- Your office laptop with a browser
- The project zip file

---

## PART 1 — Create your GitHub repository

**Step 1.** Go to https://github.com and sign up / sign in

**Step 2.** Click the green **"New"** button (top left)

**Step 3.** Fill in:
- Repository name: `finance-tracker`
- Visibility: **Private** (your code stays private)
- Leave everything else as default
- Click **"Create repository"**

---

## PART 2 — Upload your project files

**Step 4.** On your new empty repo page, click **"uploading an existing file"** link

**Step 5.** Drag and drop ALL the project files/folders:
```
.github/           ← IMPORTANT: this folder contains the build instructions
android/           ← Android native code
public/            ← App icons and manifest
src/               ← React app source code
capacitor.config.ts
index.html
package.json
package-lock.json
tsconfig.json
vite.config.ts
```
> NOTE: Do NOT upload `node_modules/` or `dist/` folders — they are too large

**Step 6.** Scroll down, write a commit message: `Initial upload`

**Step 7.** Click **"Commit changes"**

---

## PART 3 — Watch GitHub build your APK

**Step 8.** Click the **"Actions"** tab at the top of your repo

**Step 9.** You will see **"Build Finance Tracker APK"** running with a yellow spinner

**Step 10.** Click on it to watch the live build log (takes 8–12 minutes)

**Step 11.** When it turns GREEN ✓ — your APK is ready!

---

## PART 4 — Download and install

**Step 12.** Click on the completed build run

**Step 13.** Scroll down to **"Artifacts"** section

**Step 14.** Click **"finance-tracker-debug-apk"** to download a zip

**Step 15.** Extract the zip — you get `app-debug.apk`

**Step 16.** Send it to your phone:
- WhatsApp it to yourself, OR
- Upload to Google Drive, OR
- Email it to yourself

**Step 17.** On your Android phone:
- Settings → Security → Install unknown apps → Allow
- Tap the APK → Install

---

## PART 5 — Grant permissions (CRITICAL for SMS detection)

After installing, do this ONCE:

**For SMS reading:**
1. Settings → Apps → Finance Tracker → Permissions → SMS → Allow

**For Notification Listener (banking app alerts):**
1. Settings → Apps & Notifications → Special App Access → Notification Access
2. Find **Finance Tracker** → Enable it

**For background processing:**
1. Settings → Apps → Finance Tracker → Battery → Unrestricted

These three permissions are what make automatic SMS detection work.

---

## PART 6 — Rebuild after any change

Whenever you want to update the app:

**Option A (browser):** Edit files directly on GitHub → commit → Actions rebuilds automatically

**Option B (upload):** Upload new files → commit → Actions rebuilds automatically

The APK is ready in ~10 minutes every time.

---

## If the build fails

Click on the red ✗ run → click the failing step → read the error message.

Common fixes:
- `gradlew: Permission denied` → already fixed in the workflow
- `SDK not found` → already handled by setup-android action
- `npm error` → check package.json has no syntax errors

---

## How SMS detection works in the APK

```
Phone receives SMS from MD-HDFCBK
         ↓
Android delivers it to SmsReceiver.java
         ↓
TransactionParser.java checks: is this a bank SMS?
         ↓  YES
Extracts: amount, credit/debit, merchant, bank name
         ↓
Saves to SharedPreferences (local phone storage)
         ↓
Fires notification: "+Rs.3000 — UPI Transfer | Tap to categorize"
         ↓
User taps notification → app opens → SMS Inbox tab opens
         ↓
MainActivity.java reads SharedPreferences → passes to React app
         ↓
SMS appears in Inbox as PENDING
         ↓
User confirms → enters ledger
```

This works even when:
✓ App is closed
✓ Phone was restarted
✓ SMS comes from HDFC, SBI, GPay, PhonePe, Paytm, or any banking app notification
