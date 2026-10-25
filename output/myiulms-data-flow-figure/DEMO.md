# MyIULMS interactive data-flow demo

This standalone interactive explainer is a visual “Before you sign in” guide for
students and other public visitors. It answers where sign-in details go, what stays
on the phone, and how the reviewed app connects to the IULMS portal before explaining
the app → portal → app data flow. The portal rows are invented examples, not student
records.

## Trust guide and source-backed behavior

The page leads with **Your password goes straight to the IULMS portal** and
**MyIULMS has no separate server in between.** A simple phone-to-portal route and
support text state that the registration number and password are sent over HTTPS to
`iulms.edu.pk`. Four compact answer cards are closed initially; each summary offers
visible “Open answer” / “Close answer” text and a disclosure chevron.

In the reviewed app source, sign-in starts with `GET /`, posts the registration
number and password to `POST /login/index.php` on `https://iulms.edu.pk`, and makes a
follow-up `GET /` after successful authentication to read the student name. Academic
requests also use the portal host. There is no separate MyIULMS server between the
app and portal. The app uses GitHub only to check public app-update information.

While the app is active, the portal session cookie and sign-in details needed for
session re-login are held in memory. After successful sign-in, choosing **Remember
me** makes the app attempt to save the login in encrypted Android storage. Storage
operations can fail. Logging out clears the active portal session and in-memory
reconnect details, but does not clear a previously saved Remember me login. That
saved login is cleared after a later successful sign-in with Remember me turned off.
The encrypted credential preferences are excluded from Android backup and device
transfer rules.

These statements describe the reviewed app source; they do not establish the
behavior of every published build or runtime.

The interactive page is a static illustration. It sends no request, uses empty,
redacted sign-in fields, and displays invented sample data. The routes and sequence
reflect the reviewed app source; the illustration does not perform a real sign-in or
load live portal data. MyIULMS is an independent, unofficial student app and is not
affiliated with Iqra University. `AI_HANDOFF.md` remains private/local-only and is
not public demo material.

## Source basis

The request flow and trust statements are supported by:

| Demo behavior | App source |
| --- | --- |
| Portal host, HTTPS requests, in-memory cookies and reconnect credentials, logout | `app/src/main/java/com/example/myiulms/Iulms.kt` |
| Successful sign-in, Remember me save/clear behavior, logout | `app/src/main/java/com/example/myiulms/MainViewModel.kt` |
| Sign-in form and section navigation | `app/src/main/java/com/example/myiulms/MainActivity.kt` |
| Public app-update metadata request | `app/src/main/java/com/example/myiulms/UpdateChecker.kt` |
| Exclusion of encrypted credentials from backup/device transfer | `app/src/main/res/xml/backup_rules.xml`, `app/src/main/res/xml/data_extraction_rules.xml` |
| Weekly schedule | `GET /sic/Schedule.php` and `GET /sic/examschedule.php` |
| Attendance | `GET /sic/StudentAttendance.php` |
| Result | `GET /sic/examresult.php` |
| Transcript | `GET /sic/Transcript.php`, then `POST /sic/SICDataService.php` |
| Vouchers | `GET /sic/Vouchers.php`; PDF printing separately uses `POST /sic/PrintVoucher.php` |
| Sign-in illustration | `GET /`, `POST /login/index.php`, then `GET /` after successful authentication |

The app checks the sign-in POST reply for `login/logout.php`, then requests the home
page again to parse the student name. In the six-step walkthrough, Step 5 is that
follow-up `GET /` (Step 1 introduces the demo; Steps 2–4 show the initial home-page
request, sign-in POST, and successful authentication; Step 6 shows the profile
response).
Transcript rows use the app's course title, code, credit-hour, GPA, and grade fields;
marks appear only in the separate Result example. Attendance percentages follow the
app's integer truncation (14 of 16 is 87%).

## Interaction and accessibility behavior

- The header makes the HTTPS route and absence of a separate MyIULMS server the
  primary message. The FAQ uses native `<details>/<summary>` controls; all answers
  start closed and may remain open independently. The entire summary row is a
  keyboard- and touch-operable disclosure control.
- On narrow screens, the reading order is trust summary, FAQ, journey, phone preview,
  then portal. On desktop the FAQ is compactly arranged above the three-panel demo.
- Within the interactive stage, mobile DOM and reading order are journey, phone
  preview, then portal.
- Reduced-motion mode disables animation and advances playback in discrete half-second
  steps. The loading indicator becomes static, and shimmer and moving packets stop.
- Portal data rows are hidden from assistive technology until the response step.
  At that step a single status message announces that the portal response arrived.
- The portal disclosure exposes its expanded state; opening or closing it does not
  change the simulated request flow.

## Files and regeneration

`demo.mjs` is the source for both generated pages. Node.js is required to regenerate:

```powershell
node demo.mjs
node demo.mjs --light
```

The dark and light HTML pages are self-contained. Their logo is embedded as a data URI;
they load no local runtime assets, external scripts, stylesheets, fonts, or network
resources.

## Verification

The maintained browser checks require Chrome or Edge and Node.js. Run these commands
from this directory to check script parsing, browser console/layout/accessibility
warnings, and all five section previews at the required viewports:

```powershell
node scripts\probe-error.mjs myiulms-flow-demo.html
node scripts\probe-error.mjs myiulms-flow-demo-light.html
node --experimental-websocket scripts\audit-visual.mjs myiulms-flow-demo.html --width 1366 --height 768 --tabs
node --experimental-websocket scripts\audit-visual.mjs myiulms-flow-demo.html --width 1366 --height 600 --tabs
node --experimental-websocket scripts\audit-visual.mjs myiulms-flow-demo.html --width 390 --height 844 --tabs
node --experimental-websocket scripts\audit-visual.mjs myiulms-flow-demo.html --width 360 --height 800 --tabs
node --experimental-websocket scripts\audit-visual.mjs myiulms-flow-demo.html --width 320 --height 568 --tabs
node --experimental-websocket scripts\audit-visual.mjs myiulms-flow-demo-light.html --width 1366 --height 768 --tabs
node --experimental-websocket scripts\audit-visual.mjs myiulms-flow-demo-light.html --width 1366 --height 600 --tabs
node --experimental-websocket scripts\audit-visual.mjs myiulms-flow-demo-light.html --width 390 --height 844 --tabs
node --experimental-websocket scripts\audit-visual.mjs myiulms-flow-demo-light.html --width 360 --height 800 --tabs
node --experimental-websocket scripts\audit-visual.mjs myiulms-flow-demo-light.html --width 320 --height 568 --tabs
```

For a short manual keyboard, touch, and screen-reader smoke test:

1. Open either HTML page. Confirm all four FAQ answers start closed and each summary
   shows “Open answer”. Tab through the summary rows; confirm visible focus, toggle
   with Enter and Space, and verify the label changes to “Close answer”. Open more
   than one answer and confirm both remain open. On touch, tap each row.
2. With NVDA or VoiceOver, check that each FAQ question and expanded/collapsed state
   is announced, and that answer text follows its summary.
3. Continue to the phone tab list and confirm the selected tab has a visible focus
   indicator and its full accessible name, including **Result**. Use Left/Right
   Arrow, Home, and End. Confirm selection changes, focus remains intentional, and
   the tab panel is named by its tab. Enter and Space activate tabs.
4. Activate the theme link. Confirm the other theme opens in the same tab and
   preserves the selected section, flow, and step.
5. In the portal disclosure, check that invented response rows are absent before the
   response step and appear after the concise response announcement. Check the
   disclosure's expanded/collapsed state.
6. Choose Signing in and play through the follow-up `GET /`. Confirm the illustrated
   form uses empty fields and the portal excerpt contains invented data.
7. With NVDA or VoiceOver, check the heading hierarchy, mobile reading order, tab
   list and selected state, panel label, and response announcement.

Automated browser checks do not replace testing with an actual screen reader or
physical touch input. `AI_HANDOFF.md` remains private/local-only and must not be
included in public demo material.
