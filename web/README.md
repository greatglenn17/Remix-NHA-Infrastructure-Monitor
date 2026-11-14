# NHA Monitor Web

A web version of the NHA Monitor app. It reads the same Firestore data as the phones
(`projects` and `records` on `nha-monitor-bulacan-2026`). Its record writes are logging, editing and deleting issues (Progress → Issues → Log issue / Edit), adding and editing a billing (Payments → Add billing / Edit), adding and editing a developer request or a letter to the contractor
(Documents → Developer Requests / Letters to Contractor), adding an order and updating its status, adding a related file and its scanned copy (Documents), uploading a subdivision plan, drawing a lot on it, naming or removing a lot, setting a plan's scale, turning or deleting it, picking the revision in use, and updating a lot's progress and billing (Progress → Lots), adding, editing and deleting a weekly report (Progress → Weekly) and a weekly report's 24-hour weather (Progress → Weather → Edit weather), uploading, replacing and deleting an abstract of the actual work (Actual Accomplishment),
for the people the app lets edit: approved Engineer Admins and Field Engineers assigned to the project, and the Super
Admin. It also adds a project (**New project** on the dashboard, for the Super Admin and Engineer Admins) and edits one (**Edit** on the Project profile card, and **Signatories** on a weekly or monthly report's print page, for the people who can edit the project). A new issue is written as the phones upload one (revision 1, logged by the signed-in name); an edit as the phones
write a change (revision up by one, a new edit time, every other field kept) together with an audit log entry with "Website" as the device, so phones take it like another phone's change.
A phone with its own unsent edit of the same issue keeps its version. The Cloud Function
`notifySuperAdminsOnAudit` pushes that audit entry to the Super Admin's phones while the project is on-going, as it does for app edits. Everything else
is read only.

- Sign-in uses the same email and password as the app.
- **Create account** on the sign-in screen registers the way the app's sign-up does: a Firebase Auth account, then
  its `users/{uid}` profile (name, position, office, and Field Engineer or Viewer). `syncUserCustomClaims` sets the
  role and alerts the Super Admin, the account waits for approval before it can edit, and the page goes back to
  sign in like the app. Trying again with the same email and password finishes a profile that never saved.
- **Profile** (in the header) is the app's "Update Profile & Position": full name, position and office, all required.
  It updates only those fields of your existing `users/{uid}` profile (an account without one sets it up in the app
  first); printed forms here use the new values at once, and phones pick them up when they next sync the profile.
- **Add billing** is the app's Add Billing / Disbursement Voucher form: name, DV No., date, period covered, what a housing
  project bills for (units × amount per lot or unit, the amount filled in from the latest billing of that type) or the gross,
  and retention. Share and balance are saved from the gross with the current contract cost, as the app saves them, with the
  app's "Payment Creation" audit entry. The scanned DV can be attached in the form (see Attachments below).
  **Land development** is billed by the amount accomplished: its form takes the gross and the amount per lot (started
  from the contract amount per lot), and its lots are the gross over that amount (`L.billedByAmount`, `L.billedUnits`;
  saved with `units` 0). The other housing types stay whole lots or units × amount.
- **What is billed never passes the work done** (`L.billingAheadErrors`, the phone's `BillingAbstract.aheadErrors`): a
  new billing with a housing type, or an edit that raises one, is refused when the billed share of its part
  (Land Acquisition, Land Development, Housing Construction: its billings over the part's contract amount) or of all
  parts (over the contract cost) would be higher than the actual, at 2 decimals; equal is allowed. The actual is the
  latest uploaded abstract's (Actual Accomplishment: its parts folded to the three lines, a line without a part at 0,
  and its overall) once the project has one, and the message names it ("of the abstract as of Mar 14, 2025", then
  "Upload the newer abstract first, or lower this billing."); a project without an abstract keeps the latest weekly
  report's. A part's contract amount is the bar chart's in force, else the Scope of Works' (`L.partAmounts`). Billings
  without a type (an advance payment) are not counted, and a billing already saved can still be corrected.
- **The next abstract can be made in the app instead of uploaded**: **New abstract** on the Actual Accomplishment
  tab (`#/p/<id>/actual/new`), once the project has an abstract. The works, quantities, units, unit costs and amounts
  are those of the latest abstract dated before the picked "As of" date, and its TO DATE is the new PREVIOUS. The
  engineer types only each pay item's THIS PERIOD quantity; the amounts, percentages, to date, balance, equivalent
  units, subtotals and grand total follow the sheet's formulas (`L.nextAbstractRows`, the phone's
  `ProjectAbstracts.nextRows`), except that TO DATE % is the to date amount over the row's amount (the sheet adds
  the rounded percentages), so a finished line reads 100.00%. It is filed like an upload, without a file. The latest date picked again is made
  again from the one before it, starting from what was typed for it. An abstract whose subtotals do not add up from
  its items cannot be continued: the next one is uploaded.
- **The billing abstract is printed before the billing, from the Actual Accomplishment tab**, not from a billing: the
  contractor attaches it to the billing it asks for. **Print billing abstract** on the tab, for anyone who can open
  it, opens `#/p/<id>/print/apa-<as-of date>`: the abstract shown there in the district's billing format
  (`P.billingAbstractHtml`, the phone's `BlueBookForms.billingAbstract`), a landscape A4 sheet with each line (1 land
  acquisition, 2 land development, 3.1 Milestone 1 at 80% and 3.2 Milestone 2 at 20% of housing) as its contract
  quantity, unit cost and amount, what was billed before, in this billing and to date as quantity, amount and share,
  the balance, subtotals and the grand total. It has no equivalent-units columns. Its figures (`L.billingAbstract`,
  the phone's `BillingAbstract.of`):
  - **The part amounts** are the uploaded abstract's own, its parts' contract amounts by accomplishment line
    (`L.abstractLineAmounts`), not the bar chart's or the Scope of Works'. A line without an amount is left out.
  - **Previous** is the gross of the saved billings of that billing type dated on or before the abstract's "As of"
    date (a billing without a date is not counted).
  - **This billing** is typed in the boxes above the sheet and never saved: "Period covered", "Land acquisition
    (lots)", "Land development (amount)", "Milestone 1 (units)" and "Milestone 2 (units)". A line the abstract has no
    amount for has no box. Lots and units become an amount as quantity x (line amount / the project's Number of Units,
    else its saleable lots), at 2 decimals; land development is typed as an amount and starts from the abstract's own
    THIS PERIOD amount for that part (`L.abstractPeriodAmounts`). A blank, unreadable or negative figure is 0. Only
    the sheet redraws as they are typed, and they survive a redraw for an arriving record (kept on the page), but not
    leaving the page.
  - **The heading** says "Period covered: ..." when one is typed, else "As of October 30, 2026". The target is the
    bar chart's plan on the "As of" date, else the project's stored target; its "actual" is what is billed to date.
    Prepared, Checked and Approved by are the monthly report's signatories.
- **Edit** on a billing is the same form with the billing filled in. A changed gross recomputes its share and moves its
  own balance and every later billing's by the difference, as the app's `editedBillings` does, all in one transaction
  with an "Updated billing" audit entry for each billing saved. "Later" is the order shown (by date); the phone uses the
  order added, which differs only for a back-dated billing. The scanned DV stays attached.
- **Delete billing**, in that form, asks first, then marks the record `isDeleted` (one revision up) as a phone's delete
  does. Like the phone (from v2.8.14) it also gives the deleted gross back to every later billing's balance, and
  writes the app's "Payment Deletion" audit entry.
- **New letter** and **Edit** on a letter to the contractor (Documents → Letters to Contractor) are the app's Letter to
  contractor form: its date and subject, the date the contractor received it, the action the contractor took and
  the date of the reply, with the app's "Letter to Contractor" audit entry. An old "Letter to Contractor No. N" document
  has no Edit here: the phone turns it into a letter on its first edit. The receiving copy and the reply letter can be attached in the form; picking one fills its date with today when blank, as the phone does.
- **Edit** on a developer request is the app's Developer request form for one already logged: request type, details and
  the request, received and packaged dates ("Packaged today" fills the last), with the app's "Developer Request" audit
  entry.
- **Add request** (same card) is the app's New developer request form: the request, plus the details of the order it
  asks for (work suspension, continuance, resumption, variation order or time extension). Save adds the request and
  files that order in Documents as Pending, named and worded as the phone does (`L.orderDocument`), with the app's
  "Developer Request" and "Document Upload" audit entries, all in one batch. A continuance names the suspension it
  continues (the highest-numbered one unless another is picked) and starts the day after it and its continuances end;
  the End date follows the days typed; a Partial suspension picks the bar chart items it pauses; a time extension for
  a suspension starts with the days the approved suspensions stopped work.
- **Add** on the order cards (Documents → Suspension / Resumption, Time Extensions, Variation Orders) is the app's Add
  Document form for that kind of order: the same order details as Add request, filed as Pending with the app's
  "Document Upload" audit entry.
- **Update status** on an order is the app's Update status form: Pending, Submitted, Signed, Notarized or Approved, and
  the remarks. A suspension's End date moves its Duration, and a Duration, Effectivity, End, Date Approved or Items
  field dropped from the remarks is put back (`L.keepOrderFields`), so the schedule and the cost keep reading it. As on
  the phone, only the Super Admin and Engineer Admins set or undo Approved and delete an order; a Field Engineer
  updates one that is not yet approved. A changed status gets the app's "Updated document status" audit entry; a
  remarks-only edit and a delete get none, as on the phone. A suspension's form also has **Add continuance of work
  suspension**. The phone has no scan on an order, so none is attached here.
- **Add file** on Documents → Related Files is the app's Add Related Project Document form: one of its document types
  or a title typed in, a reference, the status, remarks and the scanned copy, with the app's "Uploaded project
  document" audit entry. **Attach copy** / **Edit** on a file attaches, replaces or removes its scanned copy, with the
  app's "Uploaded file attachment" audit entry, or deletes the entry (no audit entry, as on the phone). As on the
  phone, a related file's status and remarks cannot be changed after it is added.
- **Upload plan** (Progress → Lots), for the Super Admin and an Engineer Admin who can edit the project (the app's
  MANAGE_SDP_PLANS roles: `L.canManagePlans`), is the app's plan upload (`planForm`, `L.planUpload`): the plan PDF as
  the next revision of the site on screen, which becomes the one in use, or as the first plan of another site under
  the contract, naming the first site when it has no name yet. It writes the `sdp_plans` record with the app's
  "Document Upload" audit entry. A revised plan starts with no lots. The plan must be a PDF.
- **Add lot** (Progress → Lots), for the people who can edit the project, shows the whole plan page: click each corner
  of the lot in order (**Undo corner** takes the last one back), then **Save lot** for the app's lot form (`lotForm`,
  `L.newLot`): use, block, lot number, housing unit, area (worked out from the plan's scale when it has one, else the
  app's 50) and remarks. A road lot or an easement goes by one number. It writes the `sdp_lots` record with the app's
  "Lot Created" audit entry, and the next lot can be drawn straight away; **Done** leaves. Unlike the phone, a block
  and lot another lot already has is refused here too. Corners are placed with a pointer only.
- **Name & use** on a lot's card is the lot's use, block and lot from the app's lot form (`L.lotNameSave`, with the
  app's "Lot Use Changed" and "Lot Renamed" audit entries), and **Remove lot**, which takes the lot off the plan on
  every phone (`L.lotRemoved`, "Lot Deactivated"); its progress and billing history stay in the audit log.
- **The plan's own tools** (Progress → Lots, under the plan's name; put away while a lot is being drawn) are the
  phone's plan header and plan menu (`bindPlanTools`):
  - **Scale**, for the people who can edit the project (`scaleForm`, `L.planScale`): the number printed after
    "SCALE 1:" (10 to 100000). The areas of the lots nobody typed an area for (blank, the add form's 50, or what the
    last scale gave) are worked out from it, with the app's "Project Edit" audit entry. When the PDF cannot be read
    only the scale is saved. Everyone else sees the scale once it is set.
  - **Turn plan**, for the same people (`L.planTurned`): a quarter turn clockwise on every phone and here; the lots
    keep their place on the page, and nothing is logged, as on the phone.
  - **The revision in use** (the menu in place of the plan's name when the site has more than one revision), for the
    `L.canManagePlans` roles (`L.planInUse`): the one picked is switched on and the site's others off, with a
    "Project Edit" audit entry that names the version (the phone's names its local id).
  - **Delete plan**, for the same roles (`deletePlan`, `L.planDeleted`): after a confirmation, the plan and the lots,
    roads, lot progress and inspections under it are marked deleted, as the phone's delete uploads them, with the
    app's "Document Deletion" audit entry. The records under the plan go first, in batches, then the plan, so a delete
    that stops partway leaves the plan listed and Delete plan again finishes it. A site
    with an earlier revision then shows that one.
  - **Print area**, for anyone who can edit the project (`L.planFrame`): the whole page is shown with a dashed box
    around what prints now; click one corner of the plan area, then the opposite corner, or pick **Whole page** or
    **Fit to lots** (no saved frame: the box around the lots). The frame is saved on the plan as the phone saves it
    (`frame`, four page fractions), on a turned plan too, and nothing is logged, as on the phone. The viewer, the
    print and the phone all show the plan cut to it.
  - **Make v_n_ its own site**, for the `L.canManagePlans` roles (`ownSiteForm`, `L.planOwnSite`): the plan on screen,
    uploaded as a revision, becomes version 1, in use, of a new site, and its lots, progress and inspections go with
    it. When the site it leaves has no name yet, that site is named too (on its other plans). One write, with the
    app's "Project Edit" audit entry. The phone's Undo for this is not here: to put a plan back, ask on the phone.
- **Renumber Block _n_** (or **Number lots**, for a lot without a name) on a lot's card is the app's renumber panel
  (`renumCard`, `L.blockNumbers`, `L.lotsRenumbered`): the lot's whole block is picked, and a click on the plan adds
  a lot or takes one out. Type the block as the plan names it and its first lot, pick odd / even rows or
  consecutive, and flip the end or the row it starts from; each lot shows the number it would get, worked out from
  where the lots sit on the plan as it is turned (`BlockNumbering` on the phone, ported line for line). **Save
  numbers** writes the block and lot numbers in one write with the app's one "Lot Geometry Changed" entry, marks a
  found lot as checked against the plan, and leaves the panel open for the next block. It warns about names two lots
  share and about other lots already in the block typed, and does not stop either, as on the phone. The phone's line
  about blocks that still have the app's numbers is left out (the website does not draw their dashed outline).
- **Edit outline** on a lot's card (`L.lotOutline`): the whole page is shown with the lot's corners as dots. Drag a
  corner to move it, click to add a corner after the last, Undo corner takes the last one off, and **Save outline**
  writes the corners onto the same lot, so its name, progress, billing and inspections stay, with a "Lot Geometry
  Changed" audit entry. An area nobody typed is worked out again from the plan's scale. A corner can be dragged while
  a new lot is being drawn too. The phone has no such tool: its Edit geometry starts a new lot.
- **Find lots** in the plan's tools, for the `L.canManagePlans` roles, is the app's lot finder (`findLots`,
  `planScan`, `L.findLots`, `L.lotNames`, `L.foundLots`; `LotDetector` and `PlanLabels` on the phone, ported line for
  line). The plan PDF's first page is drawn about 3200 pixels across in the browser, every closed outline of the
  common lot size is a lot, larger clean rectangles inside the site are open spaces or facilities, and each lot takes
  the block and lot printed on the plan when both can be read. A review says what was found before anything is saved:
  **Add lots**, or **Add lots and larger areas**. Found lots are saved as the phone saves them ("Auto-detected", with
  the area the plan's scale gives), 200 a write, with the app's one "Lot Created" entry. Earlier found lots with no
  progress yet are replaced; lots drawn by hand, renumbered, or with progress stay, and nothing is added on top of
  them. A scanned plan has no clear outlines and finds nothing. **Remove found lots (_n_)**, for anyone who can edit
  the project, takes the found lots without progress off again (`L.foundLotsRemoved`, "Lot Deactivated").
- **Trace with a click**, in the Add lot bar, is the phone's tap-to-add (`L.traceLot`): a click inside a closed outline
  on the plan traces it and opens the lot's form; a click on a lot already there, or where the lines do not close,
  says so. It works on a turned plan too.
- **Update progress** on a lot's card (Progress → Lots) is the app's lot Construction & Billing form without the lot's
  name and use (those are Name & use): the progress band (a road lot's state and its drainage), construction status,
  current activity, remarks, the lot's photos (remove some, add more), and the M1 (80%) and M2 (20%) billing marks
  with their reference and remarks. Save writes the lot's `sdp_lot_progress` record (`L.lotSave`), or adds it when the
  lot has none yet, with the app's "Construction Progress Update" and "Developer Billing Update" audit entries. Those
  entries name the lot by its label; the phone's name it by an id only that phone has. A photo added here has no
  "Block X Lot Y" stamp.
- **Record inspection** on a lot's card is the app's formal inspection (`inspectLot`, `L.lotInspection`): the progress
  band, status, activity, contractor and field remarks. It adds an `sdp_lot_inspections` record (the snapshot, with
  the lot's billing as it stands) and the app's "Inspection Snapshot Recorded" audit entry, and sets the lot's own
  progress to match, its remarks reading "Snapshot recorded: ...". **History & details** lists a lot's inspections,
  latest first, with the change since the one before (`L.lotInspections`). Unlike the phone, the lot's start and
  target dates are kept. The progress and the snapshot are two writes: if the second fails, record it again.
- **Select more lots** on a lot's card is the app's group select: click lots on the plan to add or remove them, then
  **Update progress** gives them all one progress, status, activity and remarks (`groupProgress`,
  `L.lotsProgressSave`; each lot keeps its dates, contractor, photos and billing, and gets its own audit entry), or
  **Set use** gives them one use after a confirmation (`groupUse`, `L.lotsUseSave`, one "Lot Use Changed" entry). A
  lot that already has the values is not written. The lots go 100 a write, so a save that stops partway leaves the
  earlier lots saved; saving again finishes the rest.
- **Attachments**: the billing, developer request and letter forms take a photo or a PDF for the scanned DV, the
  developer's request letter, the receiving copy and the reply letter. On Save the file is uploaded where the phones
  keep attachments, `attachments/<projectUuid>/<id>.jpg|.pdf`, and the record gets its `nhafile:` reference: a PDF as it
  is (under 20 MB), anything else as a JPEG no larger than 2500 px on its longest side, as the phone stores a scan.
  storage.rules already lets an assigned writer add these, and uploads through the Firebase SDK need no bucket CORS
  setting (that is only for reading file bytes). An attached file can be replaced, or removed with the "Remove the
  attached file" box.
- **Files no record holds any more leave the bucket** (`dropFiles`, `L.unusedFiles`). After a delete or an edit is
  saved (`saveEdits`, Delete plan, Delete project), the files the records held before and nothing holds now are
  deleted from Storage: a deleted record's scans and photos, a replaced or removed attachment, a photo taken off a
  weekly report, a deleted plan's PDF and its lots' photos, and every file of a deleted project. A file another
  record or the project's card photo still holds stays; a mention in the audit log does not keep one. This is the
  phone's own cleanup (`Attachments`), which only reaches the files that phone uploaded and waits a day; the website
  does it at once. storage.rules already lets an assigned writer delete a project's files. The delete is not waited
  for: a file that cannot be removed (offline, no access) just stays, and a file uploaded for a record that then
  fails to save still stays too.
- **New weekly report** (Progress → Weekly) is the app's weekly report form without its photo and weather steps:
  the week (7 days from the first day picked, the Saturday of this week to start), the three accomplishment lines,
  manpower, equipment, activities and issues. With an approved bar chart the targets are its plan for the week's last
  day and are not typed; without one they are typed, weighted by the Scope of Works. Save adds the `weekly_reports`
  record (`L.newWeeklyReport`), sets the project's target and actual accomplishment, and writes the
  app's "Report Creation" audit entry. The new report has no photos and no weather log: add them with **Add photos**
  and **Edit weather**. Rows without a name or description are not saved. Unlike the phone, the form keeps no draft.
  A new report's Actual % boxes start from the report of the latest week (`L.carriedActuals`), so a week without a new
  abstract keeps the last actual.
- **A week with an uploaded abstract takes its Actual % from it** (`L.abstractInWeek`, `L.abstractLineActuals`; the
  phone's `ProjectAbstracts.inWeek`): when an abstract's "As of" date is one of the form's 7 days, the three Actual %
  boxes show its parts (0.00 for a line it has no part for), are not typed, and the form says "Actual % is from the
  abstract as of Nov 6, 2026 (Actual Accomplishment tab)." It follows the week picked, for a new report and a saved
  one, and Save stores those figures as the report's. A week without an abstract is typed as before. The report's
  overall is each line by its weight, so it can differ from the sheet's by 0.01 (the sheet adds two rounded figures).
  The form no longer reads an Excel file itself: the abstract is uploaded on the Actual Accomplishment tab.
- **Actual Accomplishment** (the project's third tab, `#/p/<id>/actual[/<as-of date>]`, shown for every project) holds
  the district's Abstract of Physical Accomplishment of the actual work, the "As of" one. **Upload abstract (Excel)**,
  for the people who can edit the project, reads the .xlsx in the browser (`apa-excel.js`, the port of
  `util/AbstractExcel.kt`; change both together): the "As of" date and every row with a description under the
  ITEM NO. header down to the GRAND TOTAL, each as its kind (part, group, item, subtotal, total), item no.,
  description, quantity, unit, unit cost, amount, PREVIOUS / THIS PERIOD / TO DATE as quantity, amount and % (0 to
  100), the balance, and the sheet's last three columns: under the header "EQUIVALENT TO 370 UNITS" (the number
  varies; it is the read's `equivUnits`, 0 on a sheet without that header), each row's PREVIOUS, TO DATE and BALANCE
  in equivalent units (`equiv`, at 2 decimals). Amounts are kept at 2 decimals, quantities at 4, an empty cell as
  null. An item no. Excel stored with a float tail ("2.2000000000000002" for a typed 2.2) is read as the number at 6
  decimals ("2.2"). A part's figures are those of its own subtotal row, as the sheet prints them
  (`L.abstractParts`). The billing format, a file without an
  abstract and an abstract without an "As of" date are refused, each with its own message (`L.abstractProblem`).
  - **The record** is a `project_abstracts` record (`L.newAbstractRecord`, the phone's `ProjectAbstract` row): `asOf`
    (yyyy-MM-dd), `overallPct` (the grand total's % to date; without one the parts' by their amounts), `rowsJson`
    (those rows), `fileName`, `uploadedBy` and `equivUnits` (the N of "EQUIVALENT TO N UNITS"; a record from before
    the column, or from a sheet without it, counts as 0). The file itself is not kept. Every upload stays under its
    "As of" date;
    uploading a date already filed asks first, then replaces that record (`saveEdits`) instead of adding a second.
    The audit entries are the phone's: "Abstract Upload", "Updated abstract" and "Deleted abstract" (`L.abstractAudit`,
    `L.deletedAbstractAudit`).
  - **The tab** opens on the latest abstract; the **As of** menu picks an earlier one. Under the date a small table
    (`L.abstractStanding`, the phone's `ProjectAbstracts.standing`) gives Target, Actual and +/- for Land
    Acquisition, Land Development, Housing Construction and Overall. A line's target is the weekly form's: the
    approved bar chart's plan for that line on the "As of" date ("-" without a chart); the overall target is the
    chart's plan that day, else the project's stored target. The actuals are the abstract's. +/- is the actual less
    the target, each at 2 decimals, with its sign ("+4.66%", "-7.58%", "0.00%"; "-" without a target). The file's
    name and who uploaded it follow, then every pay item in the sheet's columns (`P.abstractItemsHtml`): each part
    folds, groups are headings, subtotals are bold, and the grand total comes last. The table ends at BALANCE: the
    equivalent-units columns are read and kept on the record but not shown, because the abstract's format has none.
    **Print billing abstract** is described under the billings above.
    **Delete**, for the people who can edit, asks first and marks the record deleted.
  - **The uploaded abstract is the project's actual accomplishment** (`L.projectActualAfter`, the phone's
    `ProjectAbstracts.projectActualAfter`): saving or deleting one sets the project's actual, in the same write, to
    the latest abstract's overall (by "As of" date, then the last saved); with none left, to the latest weekly
    report's. While a project has an abstract, a weekly report saved here moves only the project's target. A project
    that never had one works as before.
- **Edit** on a weekly report opens the same form on the saved report (`L.weeklyStart`, `L.editedWeeklyReport`), with
  the app's "Edited Weekly Report" audit entry. The project's target and actual follow a report added or edited here
  only when no other report is for a later week, so a late entry or a fix to an older week does not set the project
  back (the phone's rule, `WeekRange.isLatest`). The week's label is kept unless its first day is changed; then weather logged for days outside the new week
  is dropped. Photos, the rest of the weather, and remarks the form has no box for are left as saved. **Delete
  report** (Super Admin and Engineer Admins, as on the phone) asks first, marks the record deleted with the app's
  "Deleted Weekly Report" audit entry, and moves the project's site photo to the newest one left.
- **New project** (dashboard, for the Super Admin and Engineer Admins: the app's CREATE_PROJECT roles,
  `L.canCreateProject`) is the app's New project form: name, location, implementing office, contractor, type, land
  area, number of units, the scope of works (the total is the original contract cost and each item's weight its
  share), the contract duration, the date started, the assigned staff, and "Already completed" with the actual
  completion date and final accomplishment. Save writes the `projects` document a phone would upload
  (`L.newProject`: revision 1, the creator the only assigned account, as firestore.rules requires of anyone but the
  Super Admin), then the app's "Project Creation" audit entry, and opens the project. The audit entry goes second
  because the rules read the saved project before they let a record in; if it fails the project stands without it.
  A project with the same name and location (ignoring case and spacing) among those the account can see is refused,
  as on the phone. A blank duration is 365 days here for both the saved duration and the completion date (the phone
  saves 365 days but shows the start date as the completion date). The role rule is in the form only:
  firestore.rules lets any approved writer create a project. Assigning other people stays on the phone.
- **Edit** on the Project profile card (Summary), for the people who can edit the project (the app's EDIT_PROJECT
  roles), opens the same form on the saved project (`L.projectStart`), with the Project Profile's projected
  beneficiaries, board resolution, notice of award and notice to proceed, and without the assigned staff. Save
  writes only the fields the form changed (`L.editedProject`), one revision up, with the app's "Project Edit" audit
  entry; a land area or scope of works the form would spell differently is left as saved until its box is changed,
  and saving with nothing changed writes nothing. The revised cost and the revised completion date are not the
  form's to change. **Mark as completed** (shown until work done reaches 100%) sets the target and actual to the
  final accomplishment and the revised completion date to the actual completion date. There is no duplicate name
  check on an edit, as on the phone.
- **Edit** on the Approved bar chart card (Summary), for the Super Admin and an Engineer Admin who can edit the
  project (the app's MANAGE_BAR_CHART roles and the rules: `L.canEditChart`), is the app's bar chart form
  (`barChartForm`): a tab per chart and **Revised chart** to add one, and per Scope of Works item its bar(s) as From
  month and cell, To month and cell. The errors that keep Save off are the app's (`L.chartErrors`): a name, the
  contract total, a revised chart's date, and bars that read. Save writes `barChartJson` in the phone's shape
  (`L.chartJson`), one revision up, with the app's "Bar Chart" audit entry; deleting the only chart removes it.
  **Import from Excel (.xlsx)** is the app's import (`chart-excel.js`, the port of `util/BarChartExcel.kt`; change
  both together): it reads the contractor's workbook in the browser, nothing is uploaded, and replaces the open
  chart's items with the sheet's items and their bars from the coloured cells. A workbook with several chart sheets
  asks which one, and an item whose bars do not cover its Duration column is named. A chart with no items is refused.
- **Delete project** in that form, for the Super Admin and an Engineer Admin who can edit the project (the app's
  DELETE_PROJECT roles, on a project that is not view-only: `L.canDeleteProject`). It asks first, then marks the
  project document deleted the way a phone uploads a delete (`L.deletedProject`); the document and its records stay
  in the cloud, and every phone and this website drop the project. No audit entry is written: the phone's names no
  project, so it never reaches the cloud either; the document keeps who deleted it and when.
- **Delete issue** in the Edit issue form (Progress → Issues), for the Super Admin and Engineer Admins, the app's
  DELETE_ISSUE roles (`L.canDeleteIssue`; a form rule only, like Delete report). It asks first, marks the record
  deleted, as the phones sync a delete, and writes the app's "Issue Deletion" audit entry (`L.deletedIssueAudit`).
- **Assign** on the Project profile card, for the Super Admin only (`L.canAssign`, as in the app and the rules), is
  the app's assignment checklist on its own (`assignForm`): every account by name, with its role, ticked when the
  project is assigned to it; an assigned uid with no account stays listed by its uid. Save writes `assignedUids`
  (those already assigned keep their place) and `assignedStaff`, their names (`L.assignmentFields`), one revision up,
  with the "Project Edit" audit entry. The `syncProjectAssignmentToRecords` cloud function then copies the list to the
  project's records, as after an assignment made on a phone; the emulators do not run it. If the account list
  cannot be loaded there is no Save, so an empty list can never clear the project.
- **Copy files to Google Drive** at the foot of the Project profile card, for the Super Admin only (`L.canAssign`),
  copies the project's photos and PDFs into the Drive of the Google account picked in the sign-in window
  (`driveForm`, `web/drive.js`): `NHA Monitor / <project> / Weekly Reports / <first day> to <last day>` for a report's
  photos (numbered in the report's order, named by their captions), then `Billings`, `Documents`,
  `Letters to Contractor`, `Developer Requests`, `Subdivision Plan`. It is a one-way copy: Firebase stays the working
  store, and nothing is read back, renamed or deleted in Drive. Everything it makes is tagged (`appProperties`), so a
  later run finds its folders wherever they were moved and skips the files already there; a file or folder put in the
  bin is copied again. A file Storage cannot give is named and left out. The sign-in asks only for `drive.file`
  (the files this site makes), and the token lives in the page for the copy and is never saved. The phone's
  **Copy files to Google Drive** (Project profile card, Super Admin) opens `#/p/<project>/drive` here, which shows this dialog.
  It needs the one-time Google setup below; until then the dialog says Drive is not set up.
- **Signatories** on the print page of a weekly or a monthly report, for the people who can edit the project, is the
  app's signatories form: name, position and office for Prepared by, Checked by and Noted by, with the app's
  suggested positions and offices as buttons under each box (any other text can be typed). It starts with what the sheet shows
  (`P.weeklySignatories`, `P.monthlySignatories`). Save writes the fields that differ to the project
  (`L.signatoryFields`), one revision up, with the app's "Project Edit" audit entry, and the sheet redraws. Prepared
  by is shared by both reports; the weekly report has its own checker and noter. The phone asks for them each time
  it prints and saves them then; here they are saved with the button, and a viewer prints what is saved.
- **Recommendations** on a monthly report's print page, for the people who can edit the project, is the text printed
  under VIII. Recommendations. It is kept in the month's `monthly_reports` record, which the phones print from too:
  the first save makes the record as the app's `submitMonthlyReport` does (`L.newMonthlyRecord`: the month's last
  weekly figures, prepared by the signed-in name), a later one changes only the text; each writes the app's audit
  entry (`L.monthlyAudit`). The phone's own monthly form (`AddMonthlyReportDialog`) has no button that opens it and
  no recommendations box, and its workable-day counts are printed nowhere, so the rest of that form is not here.
- **Edit photos** / **Add photos** on a weekly report (Progress → Weekly reports → Report details, and each album under
  Photos) is the app's photo strip for a report already saved: a title for each photo, take one off, add more (stored
  as the phone stores a site photo, a JPEG no larger than 2048 px). Save rewrites the report's two photo columns with
  the app's "Edited Weekly Report" audit entry and, in the same transaction, points the project's card photo
  (`latestUpdatePhotoUrl` on `projects/{id}`, one revision up) at the newest report photo left, as the app's
  `refreshSitePhoto` does. This is the site's only write to a project document. A photo added here has no time or GPS
  (the site does not read EXIF) and no geo-tag stamp.
- **Photos** opens with a notice when a weekly report lists a photo Storage cannot give (`loadMissingPhotos`,
  `L.missingPhotos`): how many, and each report by its week, with **Edit photos** for the people who can edit, to take
  the photo off and add it again. It reads the addresses the gallery already asks for, so it costs no extra request. A
  file that is gone and one this account cannot open look the same, and a photo a phone has not uploaded yet is counted
  until that phone syncs and the page is reloaded. Phones before v2.8.17 deleted weekly report photos a day after they
  were added; this is how to find them. shortcut: one project at a time, on its Photos tab; the dashboard does not
  say which projects have any.
- **Delete request** and **Delete letter**, in those forms, ask first, then mark the record `isDeleted` (one revision up)
  as a phone's delete is uploaded, with the app's audit entry for that deletion.
- **Edit weather** changes a weekly report's weather the way the app's weekly report form does: the report's record
  gets the new `_weather` rows (revision up by one), with the app's "Edited Weekly Report" audit entry. Only the days
  changed are rewritten; the other stored days stay exactly as they were.
- **Users** (Super Admin only, in the header) is the app's "Manage users & roles": approve a new account, give one of
  Engineer Admin, Field Engineer, Viewer or Super Viewer, and turn "See all projects" on or off. Each change is one
  `users/{uid}` update, as the app writes it; `syncUserCustomClaims` updates that person's access.
- Roles come from the same custom claims. The Super Admin, Super Viewers and accounts with
  "See all projects" see every project. Everyone else sees only the projects assigned to them.
  Unassigned projects are tagged **View only**, except for Super Viewers (same as the app).
- Screens: the dashboard (project count, total cost, status pills, attention filters, search, project cards with their own
  Work done and Billed; portfolio averages are deliberately not shown) and five project tabs:
  - **Summary**: profile, key dates with the revised completion from approved orders, contract
    cost, latest weekly report, open issues, status of payment, and the lots status card (the six
    `SdpSummary` bands) for housing projects.
  - **Progress**: weekly reports (all sections and photos), monthly reports by month, every
    photo, and for housing projects the **Lots** of each site's plan in use: the phone's KPIs,
    its search and filters, and the plan PDF with each lot coloured over it, as on the phone (drag to move, wheel
    or pinch to zoom, the zoom bar, double-click for 250%). Click a lot for its card, and "History & details" for its
    status, billing and photos. The filters dim the lots that do not match; billed lots have a dashed blue edge. **Weather** shows each logged day as the phone's 24-hour clock, by weekly report
    (7 days) or by month. **Issues** lists the logged site issues newest first, with the phone's search and
    its All / Critical / Pending / Overdue / Resolved chips.
  - **Actual Accomplishment**: the uploaded abstracts of the actual work, every pay item as in Excel (see above).
  - **Payments**: the billing list with retention, housing units and the attached files.
  - **Documents**: suspension/resumption orders, time extensions, variation orders, letters to
    the contractor, developer requests and related files, each with its scan. The **Audit log**
    (who changed what, newest first, with the phone's search and filter chips) shows for the
    Super Admin, Engineer Admins and Field Engineers, not for viewers (same as the app).
- **Print** (from a weekly report, or a month on the Monthly view): the A4 weekly or monthly
  Project Engineer's Report laid out as the phone prints it (`BlueBookPrint.kt`,
  `BlueBookForms.kt`). Choose "Save as PDF" in the print window to keep it.
- **Project Profile** print (Print on the Summary's profile card): the A4 Project Profile sheet as the phone prints it,
  with the orders under their headings and the person printing as "Prepared By".
- **Status of Construction** print (Print on the Lots view): the plan PDF's first page inside its frame, each lot
  coloured by progress, and the legend of units per band, for one site or all sites on one landscape or portrait
  page, as the phone prints it. The PDF is drawn by pdf.js 4.10.38, bundled in `web/vendor/pdfjs/` (Apache 2.0, see
  `licenses/pdfjs-Apache-2.0.txt`; its standard fonts carry their own licences).

The layouts, type and components follow the Android app (`app/src/main/java/com/example/ui/`).
The colours are a dark "command center" palette (a #0B0F17 canvas with a faint dot grid, #111827
panels) with emerald / amber / rose status colours. The site is always dark, even on a device set
to light mode. The dashboard's attention counts use the app's ProjectAttention
thresholds. IBM Plex Sans (text) and IBM Plex Mono (figures: `L.isFigure`) are bundled in `web/fonts/` (SIL Open Font License, see `licenses/IBM-Plex-OFL.txt`).
The icons are Material Symbols from Google Fonts. See `DESIGN.md` at the repo root for the tokens.

Plain HTML and JavaScript modules. There is no build step. The Firebase JS SDK 10.12.2 loads from
Google's CDN. `logic.js` holds the rules copied from the Android app (contract cost, days left,
net amounts...). Each rule names the Kotlin it mirrors.

## Run locally (emulators, no live data)

You need Node and the Firebase CLI. The emulators also need Java; Android Studio's
`jbr\bin` works. From the repo root:

```bash
firebase emulators:start --only auth,firestore,storage,hosting --project demo-nha
```

In a second terminal:

```bash
node web/emulator/seed.mjs
```

Open http://127.0.0.1:5000/?emulator and sign in with one of the test accounts listed at the top of
`web/emulator/seed.mjs`. Without `?emulator` the page talks to the live project.

Tests for the copied rules:

```bash
node --test "web/*.test.mjs"
```

## One-time setup for the live project

1. Done on 2026-11-07: the Web app "NHA Monitor Web" is registered and its config is in
   `web/firebase-config.js`. These values are public by design, and the security rules protect
   the data. To print the config again:
   `firebase apps:sdkconfig WEB 1:1091951535339:web:9f23ffea71ebca0f868aae`.
2. Authentication > Settings > **Authorized domains**: `nha-monitor-bulacan-2026.web.app` and
   `localhost` are there by default. Add any custom domain you use.

## Copy to Google Drive: one-time Google setup

Done by the owner of the Google Cloud project, once. Until `driveClientId` is filled in, the copy says it is not set up.

1. Open https://console.cloud.google.com and pick the project `nha-monitor-bulacan-2026` (the Firebase project).
2. **APIs & Services > Library**: find **Google Drive API** and press **Enable**.
3. **APIs & Services > OAuth consent screen** (Google Auth Platform): app name `NHA Monitor`, your email as support
   and developer contact, audience **External**. Under **Data access** add the scope `.../auth/drive.file`. Then
   either press **Publish app** (this scope needs no Google review), or leave it in Testing and add, under
   **Audience > Test users**, each Google account that will receive the files.
4. **APIs & Services > Credentials > Create credentials > OAuth client ID**, type **Web application**, name
   `NHA Monitor Web`. Under **Authorized redirect URIs** add exactly
   `https://nha-monitor-bulacan-2026.web.app/drive-auth.html` (and `http://127.0.0.1:5000/drive-auth.html` to try it
   on the emulators). Create it and copy the **Client ID** (it ends in `.apps.googleusercontent.com`; the client
   secret is not used).
5. Paste the Client ID into `driveClientId` in `web/firebase-config.js` and deploy the site.

The sign-in opens in its own window and comes back to `drive-auth.html`, which hands the answer to the waiting page
(`BroadcastChannel`) and closes. No Google script is loaded and the Content-Security-Policy is unchanged
(`www.googleapis.com` is already allowed for data).

## Security headers

`firebase.json` sends a Content-Security-Policy (scripts only from this site and `www.gstatic.com`,
data only from `*.googleapis.com`, no frames, and the site cannot be framed), plus
`X-Frame-Options: DENY`, `nosniff`, a strict referrer policy and a Permissions-Policy. The
`127.0.0.1` entries in the policy are only for the local emulators. If you add a script, font,
image or API host, add it to the policy too or the browser will block it. Auth is started with
`initializeAuth` and no popup/redirect helper, so no Google sign-in iframe is needed.

## Deploy

`firebase.json` now serves `web/` (it used to serve the old `public/` prototype, which pointed
at another Firebase project). Deploy hosting only:

```bash
firebase deploy --only hosting --project nha-monitor-bulacan-2026
```

The site is then live at https://nha-monitor-bulacan-2026.web.app. Rules and functions are not
touched by this command.

## Not on the site yet

Roads (`sdp_roads`) are neither shown nor drawn: the phone cannot draw one any more (its road form is never opened),
and a road on a plan is a lot whose use is Road Lot.
