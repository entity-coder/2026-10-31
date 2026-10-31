# RestroBill Android — restaurant table billing on mobile

An Android port of [RestroBill](https://github.com/entity-coder/2026-10-23)
(the Python desktop app from 2026-10-23), for restaurants, cafes, and food
stalls that want table billing on a phone or tablet instead of a PC.

## What it does

- **Tables (T1–T10):** a floor view showing which tables are occupied and
  each table's running total. Tap a free table to start an order; tap an
  occupied table to open it. Occupancy is *derived* from open orders —
  there is no separate occupied flag to drift out of sync.
- **Menu:** add, edit, search, and deactivate dishes (name, category,
  veg/non-veg, price). Deactivation is a soft delete: old bills and running
  orders keep working, because they store snapshot names and prices.
- **Live ordering:** add dishes to a table (tap + for one portion; adding
  the same dish again grows its quantity instead of duplicating the row),
  adjust quantities with +/−, edit quantities and kitchen notes
  (e.g. "less spicy") per line, or remove a line.
- **KOT (kitchen order ticket):** sends a ticket to the kitchen as a PDF
  (print via the system print dialog, or share e.g. via WhatsApp). Only
  lines not yet sent are printed, so the kitchen never cooks a dish twice;
  if nothing is new, the app offers a full reprint instead of printing an
  empty ticket. Sent lines show a "Sent to kitchen ✓" badge.
- **Billing:** bill numbers RB-0001, RB-0002, …; percent or flat discount
  (percent clamped to 0–100, flat capped at the subtotal, so a discount can
  never make a bill negative), then GST on the *discounted* subtotal
  (default 5%, configurable in Settings, split CGST/SGST with an odd paise
  going to CGST). Payment modes: Cash, UPI, Card, Bank Transfer. Billing
  closes the order and frees the table; the bill PDF can be printed or
  shared.
- **Merge / split tables:** merge moves every line of one table's order
  into another open table's order; split moves selected lines to a new
  order on a free table (you must pick some, but not all, lines).
- **Day summary:** today's bills, revenue, top 5 dishes, table-wise totals,
  and item-wise sales — all computed from stored bills, never from mutable
  order lines, so the summary is a record of what was actually charged.
- **Settings:** restaurant name (printed on bills and KOTs) and GST percent.
- **Starter menu:** a 12-item menu (Paneer Tikka, Chicken 65, Butter
  Chicken, biryanis, naan, chai, Gulab Jamun, …) is seeded on first launch
  so a new install is usable immediately.

All money is integer paise (`Long`) throughout — no `Double` in any billing
calculation, so totals are exact. The app is fully offline: Room database,
no network, no account, no AI features (billing math must be deterministic
and auditable, so no AI angle was forced in).

## How to open and run in Android Studio

This project was **not compiled or tested on a device** in the environment
where it was built (no Android SDK there). To run it:

1. Open Android Studio → **Open** → select this project folder.
2. Let Gradle sync (JDK 17, Gradle 8.7, Android Gradle Plugin 8.2.2).
   The `kapt` plugin version is declared explicitly and exactly once
   (`1.9.22`, matching the Kotlin version) in `app/build.gradle.kts` —
   CI builds fail if kapt is applied without a version.
3. Connect a phone (USB debugging) or start an emulator (API 24+).
4. Run ▶ — the app launches on the Tables screen.

## Direct APK download (after the first build)

Once the GitHub Actions workflow below has run at least once, install
directly without Android Studio from:

`https://github.com/entity-coder/2026-10-31/releases/download/apk-latest/RestroBill-v1.0.apk`

That link only works after the `apk-latest` release actually exists — it
does not exist until the workflow file is added (see below) and a build
completes. Android will show an "unknown developer" prompt, which is
normal for APKs outside the Play Store.

> **Note for the repo owner:** the `.github/workflows/build-apk.yml`
> workflow (which builds a debug APK on every push and publishes it to the
> rolling `apk-latest` release) could not be pushed with the code, because
> this GitHub integration is blocked from writing `.github/workflows/`.
> Add it once via the GitHub web UI (open the repo → Add file → Create new
> file → name it `.github/workflows/build-apk.yml` — the leading dot in
> `.github` is mandatory, otherwise Actions silently ignores it → paste the
> workflow content → Commit changes), and untick "Enable release
> immutability" in repo Settings so the rolling release can update.

## Tech

Kotlin, Android Studio project (Gradle Kotlin DSL), Room (5 tables:
`menu_items`, `orders`, `order_lines`, `bills`, `bill_lines`), view
binding, `Flow` + coroutines, Material 3, `PdfDocument` for bill/KOT PDFs
with `PrintManager` printing and `FileProvider` sharing. Min SDK 24,
target/compile SDK 34, version 1.0.

## Project layout

```
app/src/main/java/com/restrobill/app/
  data/  MenuItem.kt, Order.kt (orders, lines, bills, bill lines),
         RestroDao.kt, RestroDatabase.kt, MoneyMath.kt (pure billing
         maths: discount caps, GST split, bill numbers), Format.kt,
         ShopPrefs.kt (restaurant name + GST %)
  ui/    MainActivity.kt (bottom nav + starter-menu seed),
         TablesFragment.kt, MenuFragment.kt, SummaryFragment.kt,
         OrderActivity.kt (ordering, KOT, billing, merge, split),
         SettingsActivity.kt, Adapters.kt, PdfHelper.kt,
         DocBuilders.kt (bill/KOT PDF text)
```

## Limitations (honest)

- Fixed ten tables (T1–T10); table count is not configurable yet.
- No stock/inventory tracking (the PC version has none either for
  restaurant items) and no per-table waiter assignment.
- An order that is split down to zero lines stays open and keeps its
  table occupied until dishes are added or it is merged away.
- No cloud sync or multi-device sharing; data lives in the phone's Room
  database only.
- Not compiled or device-tested in the build environment — open in Android
  Studio and run it there (see above).

## Interview notes

**What is this project?** An Android port of a desktop restaurant billing
app: a waiter runs tables from a phone — start orders, send kitchen
tickets, merge/split tables, bill with discount and GST, and see the day's
sales — all offline.

**Why Room with five tables?** Orders, order lines, bills, and bill lines
are kept separate on purpose. Order lines are mutable (quantities and
notes change while the table is running), but bills are a financial
record: when a table is billed, its lines are copied into `bill_lines`
snapshots, so later menu edits or order changes can never rewrite what a
customer was actually charged. The day summary reads bills only, which is
why it stays trustworthy.

**Why is occupancy derived, not stored?** A stored "occupied" boolean is
a second source of truth that can disagree with the orders table (e.g.
after a crash mid-update). Deriving it — a table is occupied exactly when
it has an OPEN order — makes disagreement impossible by construction.

**Why integer paise instead of Double?** Money in floating point drifts
(the PC version documents a 1-paise rounding edge case from binary
floats). Paise as `Long` makes every total exact, and the only rounding
is a single, deliberate step: GST rounded to the nearest paise, with an
odd paise assigned to CGST so CGST + SGST always equals the GST total.

**Why discount before GST?** That is the tax rule the PC app follows and
the usual restaurant practice: GST is charged on what the customer
actually pays, not on the pre-discount price. The order is enforced in
one pure function (`computeBill`) so it cannot vary between screens.

**Why does a KOT print only new lines?** A kitchen ticket is a work
order, not a receipt. Re-sending dishes the kitchen already has causes
duplicate cooking and wasted food. Tracking `kotPrinted` per line makes
"send KOT" idempotent, and the explicit reprint path covers the case
where a ticket was lost.

**Why deactivate dishes instead of deleting them?** Deleting a dish
would leave old bills pointing at nothing (or force a cascade that erases
history). Deactivation hides the dish from the ordering picker while
every record that references it stays intact.

**Trade-offs / limitations:** fixed T1–T10 tables (simpler floor model,
but a 12-table restaurant must wait for a configurable table count);
no stock tracking and no sync (offline-first was the priority, and a
single-device shop does not need either yet); PDFs are plain-text
layouts rather than designed invoices (keeps the app dependency-free,
but a thermal-printer layout would be nicer); not compiled in the build
environment, so the first Android Studio build is the real verification
step.

**How I'd extend it:** configurable table count and table names; a
thermal (80mm) bill layout; waiter login with per-waiter sales; KOT
auto-print to a paired Bluetooth kitchen printer instead of
print-or-share; and cloud backup/sync so a second device at the counter
sees the same tables.
