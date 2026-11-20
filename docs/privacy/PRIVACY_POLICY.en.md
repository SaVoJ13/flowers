# PRIVACY POLICY — FLOWERS

> **Status: DRAFT for release 1.0 (TASK-021-B), not published.** Published by the Owner. Before publishing the Owner: (1) sets the effective date; (2) hosts the text on a public page and enters the URL in Play Console; (3) confirms it matches the shipped build. Ukrainian version: `PRIVACY_POLICY.md` (content must stay in sync). Translated by the developer; not reviewed by a native English speaker.

**App:** Flowers (Android, `com.savoj.flowers`) — houseplant care.
**Last updated:** [publication date — set by the Owner]
**Contact for privacy questions and deletion requests:** appvsdeveloper@gmail.com
**Age limit:** the app is intended for users aged **18 and over**.

---

## 1. In short
- Your plants, care journal and reminders are stored **only on your phone**. We have no accounts and do not collect your name, e-mail, phone number or location.
- The app uses the internet in **two cases**: (a) when you ask it to create a care card with AI (the plant name is sent to Google); (b) when you tap "Report this AI answer" (the report is stored in our database).
- There are no ads, analytics or tracking.

## 2. What is stored on your device
Plants, watering dates and events, notes, settings, saved care cards (including AI-created ones). This data is not sent to us. You can delete it by removing a plant/entry in the app, clearing app data in Android settings, or uninstalling the app.
**Android backup.** The app does not disable Android Auto Backup. If it is enabled on your phone, the system may store a copy of app data in your Google account under Google's rules; we have no access to it. You can turn it off in Android settings.
**Photos and camera.** The app does not request access to the camera or gallery.
**Notifications.** The app can show local reminders (`POST_NOTIFICATIONS` permission); they are created on the device, without a server.

## 3. Creating a care card with AI (Google Gemini)
When you search for a plant that is not in the catalog and start generation, the app sends **the plant name you typed** to Google services (Firebase AI Logic / Gemini Developer API) and receives the card text.
- **Do not enter personal data** in the name field.
- We use the service's **free tier**. Under Google's terms for the free tier, **Google may use requests and responses to improve its products, and human reviewers may read them**. See the Gemini API terms and Google's privacy policy.
- To prevent abuse, Firebase App Check (Play Integrity) sends Google an **app/device integrity attestation token**; Firebase SDKs also create technical installation identifiers (Firebase installation ID). We do not link them to you.
- AI output is **advice, not a guarantee** and may be inaccurate. Please verify it, especially for pet and child safety. For AI-created cards the app does not state whether a plant is safe or toxic.
- The created card is stored only locally on your phone.
- Without internet, generation is unavailable; everything else works offline.

## 4. Reporting an AI answer
The "Report this AI answer" button sends to our **Cloud Firestore (Google, region eur3 — Europe)** database:
- the text of the card you report and the plant name;
- the reason you chose and your **optional comment** (do not put personal data in it);
- the app version, time of the report and the automatic deletion date.
There are no accounts, and your device or account identifiers are **not attached** to the report. Only the developer can see reports, in the Firebase console. Purpose: fixing mistakes and improving the safety of answers. **Retention: 12 months**, after which the report is to be deleted.
Because a report is not linked to you, we can find and delete a specific one only if you give its exact text and approximate time.

## 5. What we do not do
We do not show ads, use analytics or Crashlytics, sell data or share it with third parties other than Google as the provider of the services above. General crash and performance data is collected by Google Play itself (Android vitals) under Google's own rules.

## 6. Your rights and requests
Contact **appvsdeveloper@gmail.com** to request access to, correction or deletion of data (including reports under section 4) or with questions about this policy. We will reply within a reasonable time (target: within 30 days). If you are in the EU/EEA you may also contact your data protection authority.

## 7. Security
Data sent to Google travels over HTTPS. Access to the reports database is limited to the developer; the database rules let the app only create reports in a strictly defined format, but not read, change or delete them.

## 8. Changes
An updated policy will be published at the same link with a new date. Significant changes (for example new kinds of data) will also be shown in the app.
