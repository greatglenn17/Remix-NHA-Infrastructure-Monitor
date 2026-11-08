---
name: NHA Monitor Web
description: A read-only portfolio and project Summary viewer for NHA Monitor, in a dark command-center palette.
colors:
  primary: "#2563EB"
  on-primary: "#FFFFFF"
  link: "#60A5FA"
  primary-container: "rgb(59 130 246 / 0.16)"
  on-primary-container: "#93C5FD"
  accent: "#3B82F6"
  billed: "#A78BFA"
  canvas: "#0B0F17"
  dots: "rgb(148 163 184 / 0.13)"
  surface: "rgb(17 24 39 / 0.9)"
  surface-solid: "#111827"
  surface-variant: "#1F2937"
  field: "#0F1623"
  text: "#F1F5F9"
  text-2: "#94A3B8"
  border: "rgb(255 255 255 / 0.08)"
  outline: "rgb(255 255 255 / 0.18)"
  track: "rgb(255 255 255 / 0.08)"
  ok: "#34D399"
  ok-container: "rgb(16 185 129 / 0.14)"
  watch: "#FBBF24"
  watch-container: "rgb(245 158 11 / 0.14)"
  behind: "#FB7185"
  behind-container: "rgb(244 63 94 / 0.14)"
  neutral: "#94A3B8"
  neutral-container: "rgb(148 163 184 / 0.14)"
  hero-start: "#1E3A8A"
  hero-end: "#0B0F17"
  bar-bg: "#0B0F17"
  bar-text: "#F1F5F9"
  bar-text-2: "#94A3B8"
  bar-chip: "#1F2937"
  bar-link: "#93C5FD"
typography:
  display:
    fontFamily: "Inter, system-ui, sans-serif"
    fontSize: "34px"
    fontWeight: 600
    lineHeight: 1.15
    letterSpacing: "-0.6px"
  headline:
    fontFamily: "Inter, system-ui, sans-serif"
    fontSize: "24px"
    fontWeight: 600
    letterSpacing: "-0.3px"
  title-lg:
    fontFamily: "Inter, system-ui, sans-serif"
    fontSize: "22px"
    fontWeight: 600
    letterSpacing: "-0.2px"
  title:
    fontFamily: "Inter, system-ui, sans-serif"
    fontSize: "15px"
    fontWeight: 600
    lineHeight: 1.3
  title-sm:
    fontFamily: "Inter, system-ui, sans-serif"
    fontSize: "14px"
    fontWeight: 600
  body:
    fontFamily: "Inter, system-ui, sans-serif"
    fontSize: "15px"
    fontWeight: 400
    lineHeight: 1.45
  body-sm:
    fontFamily: "Inter, system-ui, sans-serif"
    fontSize: "13px"
    fontWeight: 400
  label:
    fontFamily: "Inter, system-ui, sans-serif"
    fontSize: "12px"
    fontWeight: 500
  label-sm:
    fontFamily: "Inter, system-ui, sans-serif"
    fontSize: "11px"
    fontWeight: 700
  numeric:
    fontFamily: "Inter, system-ui, sans-serif"
    fontFeature: "tnum"
rounded:
  bar: "3px"
  xs: "4px"
  sm: "6px"
  md: "8px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "12px"
  lg: "16px"
  xl: "20px"
  xxl: "24px"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    rounded: "{rounded.sm}"
    padding: "10px 18px"
    height: "40px"
  button-text:
    textColor: "{colors.primary}"
    rounded: "{rounded.sm}"
    padding: "8px"
  button-text-hover:
    backgroundColor: "{colors.primary-container}"
    textColor: "{colors.primary}"
  chip:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text}"
    rounded: "{rounded.md}"
    padding: "6px 12px"
    height: "36px"
  chip-selected:
    backgroundColor: "{colors.primary-container}"
    textColor: "{colors.on-primary-container}"
    rounded: "{rounded.md}"
  input:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text}"
    rounded: "{rounded.sm}"
    padding: "11px 12px"
    height: "44px"
  card:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.md}"
  badge-ok:
    backgroundColor: "{colors.ok-container}"
    textColor: "{colors.ok}"
    typography: "{typography.label-sm}"
    rounded: "{rounded.sm}"
    padding: "3px 8px"
  badge-watch:
    backgroundColor: "{colors.watch-container}"
    textColor: "{colors.watch}"
    typography: "{typography.label-sm}"
    rounded: "{rounded.sm}"
    padding: "3px 8px"
  badge-behind:
    backgroundColor: "{colors.behind-container}"
    textColor: "{colors.behind}"
    typography: "{typography.label-sm}"
    rounded: "{rounded.sm}"
    padding: "3px 8px"
  badge-view:
    backgroundColor: "{colors.surface-variant}"
    textColor: "{colors.text-2}"
    rounded: "{rounded.xs}"
    padding: "2px 6px"
  progress-bar:
    backgroundColor: "{colors.border}"
    rounded: "{rounded.bar}"
    height: "6px"
---

# Design System: NHA Monitor Web

## Overview

**Creative North Star: "The Phone, Opened Wide"**

The web viewer is the NHA Monitor Android app shown on a laptop or a meeting-room projector. It is not a separate identity. Its layouts, type, shapes and components follow the app (`app/src/main/java/com/example/ui/`). Its colours do not: on 2026-11-08 the user asked for an executive "Architectural Command Center" look, a #0B0F17 canvas with a blueprint dot grid and #111827 panels, always dark. Someone who uses the app on a phone should find the same header, the same chips, and the same bars in the same places.

The look is a quiet control room: a near-black canvas with a faint dot grid, dark panels with white 8% hairlines and 12px corners, blue for action and on-plan progress, violet for money, and four status colours that only ever describe a project's state. Density is moderate. Screens have to read on a projector, so the hierarchy comes from weight (600 titles over 400 body) and grey secondary text, not from big type. Everything is read-only and quiet. Colour appears where it carries meaning (progress, slippage, attention) and almost nowhere else.

It rejects the generic admin template (side menu, KPI cards, blue-on-white data tables) and it does not imitate official NHA or government branding.

**Key Characteristics:**
- Always dark (`color-scheme: dark`), whatever the device's setting. There is no light theme.
- Near-black dot-grid canvas, dark hairline panels, 12px card radius, no shadows; cards lift 2px on hover.
- Blue for interaction and on-plan work, violet for Billed. Status colours are for status only.
- Inter throughout. Tabular figures only on numbers that line up.
- Material Symbols Outlined icons at 20px (16px small), always next to a text label.
- One motion: the progress bars fill in when data arrives.

## Colors

A near-black canvas with a faint dot grid, dark translucent panels with white 8% hairlines, blue for action and on-plan progress, violet for money, and a four-step status scale with tinted containers and 30% outlines. Dark only.

### Primary
- **Action Blue** (`primary`): the Sign in button. **Link Blue** (`link`): text buttons, links, focus rings, the caret and section-head icons. Selected chips and the "missing data" note use **Blue Haze** (`primary-container`) with `on-primary-container` text.
- **Progress Blue** (`accent`): the Work done bar while a project is on or ahead of plan, and the On-going status pill.
- **Billed Violet** (`billed`): every Billed bar, so money never reads as a work colour.
- **Brand Gradient** (`hero-start` navy to `hero-end` canvas, 135deg): used only on the sign-in hero panel. Text on it is white.

### Top bar
- **Canvas** (`bar-bg`) with a bottom hairline, `bar-text` title, `bar-text-2` secondary text, a `bar-chip` fill for the read-only badge and **Sky** (`bar-link`) for Sign out.

### Status (semantic, from the app's onTrack / watch / behind / neutral)
- **Emerald** (`ok`) on `ok-container`: completed projects, the finished Work done bar, "on schedule" and "ahead" notes, success notes.
- **Amber** (`watch`) on `watch-container`: behind schedule by less than 15%, warning-level flags, orders waiting approval, the priority callout.
- **Rose** (`behind`) on `behind-container`: 15% or more behind, overdue, critical flags, form errors.
- **Neutral Slate** (`neutral`) on `neutral-container`: no status yet (not started, suspended).

### Neutral
- **Canvas** (`canvas`) with the **Dot Grid** (`dots`, 1px dots every 22px, fixed): the page background.
- **Panel** (`surface`, 90% opaque): cards and chips. **Solid Panel** (`surface-solid`): dialogs. **Field** (`field`): inputs and the segmented control.
- **Soft Panel** (`surface-variant`): photo placeholders, scope blocks, the selected segment, the view-only badge.
- **Ink** (`text`) and **Secondary Slate** (`text-2`): primary and secondary text. Every label, unit, and metadata line uses `text-2`.
- **Hairline** (`border`): card borders, row dividers. **Track** (`track`): the empty part of a progress bar.
- **Outline** (`outline`): input strokes, chip borders on hover.

### Named Rules
**The Meaning-Only Colour Rule.** Emerald, amber, rose, and neutral slate describe a project's state and nothing else. They are never decoration, section tints, or brand accents.

**The No-Portfolio-Average Rule.** Work done and billed are tracked per project only. The dashboard banner never shows an average work done or a portfolio billed percentage (user's rule, 2026-11-08).

**The Palette-Is-Fixed Rule.** Every colour comes from the tokens above, set in `web/style.css` `:root`. Don't add a colour outside them.

## Typography

**Display Font:** Inter (bundled at `web/fonts/Inter.ttf`, OFL, variable weight 100 to 900), falling back to system-ui, sans-serif
**Body Font:** Inter (same file)

**Character:** One neutral grotesque at 400 to 700. Titles get slight negative tracking. The hierarchy is built mostly from weight and grey, which matches the app's Type.kt and keeps a projector screen calm.

### Hierarchy
- **Display** (600, 34px, 1.15, -0.6px; 26px under 900px): the sign-in hero headline only, with a measure of about 14ch.
- **Headline** (600, 24px, -0.3px): the sign-in form title.
- **Banner Count** (700, 30px, -0.03em, uppercase, tabular; 26px under 600px): the project count in the dashboard banner. The total contract cost under it is set in the system monospace.
- **Title Large** (600, 22px, -0.2px): the project name and the large progress figure in the project header.
- **Title** (600, 16px, 1.3): project card names, clamped to two lines.
- **Title Small** (600, 14px): section-card headings and dialog terms.
- **Body** (400, 15px, 1.45): default text and input values.
- **Body Small** (13px): key-value rows, tables, logs, field labels (500), the app-bar user line.
- **Label** (500, 12px): progress-line labels, card metadata, sub-headings in `text-2`.
- **Label Small** (700, 11px): status badges.
- **Office line** (600, 11px or 12px, uppercase, 0.6px or 1px tracking): the office name under the brand title in the app bar and at the foot of the sign-in hero. It always sits under its title and is never a section label.

### Named Rules
**The Tabular-Where-It-Lines-Up Rule.** `font-variant-numeric: tabular-nums` applies only to numeric elements: table number cells, progress values, chip counts, and the portfolio total. Running text keeps proportional figures. This mirrors Type.kt TabularNumbers.

## Layout

The content column is centred at 1320px max width, with 24px side padding (16px under 600px) and 20px top / 48px bottom padding inside `main`. Spacing follows a 4px base (4, 8, 12, 16, 20, 24px). Gaps between cards are 12px, and gaps between paired bars are 16px to 24px.

- **App bar:** sticky, canvas-coloured, with a bottom hairline. Brand block on the left, user and Sign out pushed to the right.
- **Sign-in:** a full-bleed two-column grid (hero 5fr, form 6fr) with no app bar. Under 900px it stacks, hero first. The form is at most 400px wide and centred.
- **Dashboard:** the command banner (count and total cost on the left, status pills on the right, the priority callout across the bottom; stacked under 900px), then one control row (search with a Cmd/Ctrl+K hint, the My / All projects segmented control, the attention filter chips, an icon-only help button), then a project grid of `auto-fill, minmax(min(380px, 100%), 1fr)`, which gives two or three columns on desktop.
- **Project card:** a header row (48px photo, name and town, status badge on the right), Work done over Billed, an optional flag line, and a footer under a hairline with the completion date and the waiting-approval badge. Under 600px the badges drop under the name.
- **Project Summary:** a two-column card grid (one column under 900px). Wide sections span both columns.
- **Phone widths:** status pills and attention chips form 2-up grids under 600px; the help button shows its words. The payments table moves the date under the billing name under 560px.

## Elevation & Depth

Flat, with tonal layering. Depth comes from dark translucent panels over the dot grid plus a 1px white 8% hairline. There are no shadows. Project cards rise 2px on hover and their border turns 40% blue (150ms, none under reduced motion). Dialogs sit on a 60% black backdrop.

### Shadow Vocabulary
- None. Panels separate from the canvas by colour and hairline.

### Named Rules
**The Hairline-First Rule.** A panel is defined by its 1px border and its colour step from the canvas. Nothing casts a shadow.

## Shapes

4px (view badge, focus ring, key hint), 6px (buttons, inner blocks, segments), 8px (inputs, photos, the segmented control), 12px (cards, the callout, dialogs), 4px for the 8px-tall progress bars, and full pills for status badges, status pills and filter chips (8px on phones, where they form a grid). Photos are clipped to their radius with `object-fit: cover`. A site photo is a 64px placeholder until it loads, then 16:9.

## Components

### Buttons
- **Shape:** gently rounded (6px), at least 40px tall.
- **Primary:** Action Blue fill, `on-primary` text, 600 14px, 10px 18px padding. Used once per view at most (Sign in).
- **Hover / Focus:** hover brightens the fill (`filter: brightness(1.1)`). Focus is a 2px primary outline with a 2px offset. Disabled is 0.6 opacity.
- **Text button:** blue 600 14px, 8px padding, Blue Haze background on hover. Used for Sign out, Back, Forgot password, and "What these mean" (icon only on desktop, with its name kept for screen readers). An icon leads the label.

### Chips
- **Style:** Panel, 1px hairline, pill radius, 500 13px, at least 36px tall. A 16px icon leads, and a bold tabular count goes before the label on attention chips.
- **State:** selected (`aria-pressed="true"`) uses Blue Haze with `on-primary-container` text and a 40% blue border. Hover darkens the border to `outline`. Attention chips tint only their icon (behind red, watch orange).

### Status Pills and Priority Callout
- **Status pills** (banner): On-going (blue), Completed (emerald), Overdue / behind (rose), Orders waiting approval (amber). Each is the status colour on its container with a 30% outline, a 16px icon and a bold tabular count. They show counts only and are not filters.
- **Priority callout:** the dashboard's one finding, "First to look at: <project>, <worst flag> · N more flagged", in an amber box (container fill, 30% outline, 12px radius) with a warning icon. With nothing flagged it turns emerald.

### Segmented Control
My projects | All projects, for accounts that see every project (not Super Viewers). A Field-coloured track with a hairline; the selected segment is Soft Panel with Ink text.

### Cards / Containers
- **Corner Style:** 12px.
- **Background:** Panel over the dot-grid canvas.
- **Shadow Strategy:** none (see Elevation & Depth).
- **Border:** 1px hairline. On project cards, hover turns it 40% blue and lifts the card 2px (150ms).
- **Internal Padding:** 16px to 20px. Section cards use a 48px-tall head row with a blue 20px icon and a 14px/600 title.

### Inputs / Fields
- **Style:** Field fill, 1px `outline` stroke, 8px radius, 11px 12px padding, at least 44px tall. The label sits above (13px/500, `text-2`). The search field has a 38px left inset for the icon and a Cmd/Ctrl+K hint on the right that hides on focus, when typed in, and on touch screens.
- **Focus:** the border turns Progress Blue and gains a 1px ring (`box-shadow: 0 0 0 1px`).
- **Error:** a 13px/500 Behind Red line under the fields, with `role="alert"`.

### Navigation
- **App bar:** a canvas-coloured sticky bar with a bottom hairline. The brand title is 16px/600 with the office line under it. On the right: the user line (13px `bar-text-2`; the email hides under 600px), a view-only badge, and the Sign out text button. Navigation between pages is a Back text button with no menu.

### Status Badge
A small pill in 700 11px, with status colour text on its container. On project cards an overdue project's badge reads "Overdue by N days" in rose. The **view-only** variant is quieter: 4px radius, 500 weight, Soft Panel with `text-2` and an eye icon.

### Progress Line (signature)
The label (13px/500 `text-2`) on the left; the value (600, tabular) and a 12px note on the right; an 8px bar on the track with 4px corners beneath. **Work done** is emerald when finished, Progress Blue on or ahead of plan, amber when behind, rose from 15% behind; its note is the schedule variance ("on schedule" or "3.75% behind" with a warning icon) in the matching colour. **Billed** is always Billed Violet, with the peso amount as its note. The two always appear as a pair on every project card and header, never as portfolio averages. In headers the value is 22px/600. When fresh data arrives, the bars fill from the left (700ms, `cubic-bezier(0.16, 1, 0.3, 1)`, scaleX). This is turned off under `prefers-reduced-motion`.

## Do's and Don'ts

### Do:
- **Do** take type values from the app's theme files, and colours only from the command-center tokens above.
- **Do** pair Work done (schedule colour) with Billed (violet) wherever a project's progress appears.
- **Do** put a text label next to every Material Symbols icon, and keep icons at 20px or 16px.
- **Do** apply tabular figures only to numbers that align (table cells, progress values, counts, totals).
- **Do** define panels with a 1px hairline over the dot-grid canvas, without shadows.
- **Do** keep the page dark on every device.

### Don't:
- **Don't** use the status colours for anything other than project state.
- **Don't** introduce a side menu or KPI tiles, and don't put average work done or portfolio billed back on the dashboard.
- **Don't** imitate official NHA or government branding.
- **Don't** use the brand gradient outside the sign-in hero.
- **Don't** use tracked uppercase lines as section labels or eyebrows. The office line and the banner count are the only uppercase text.
- **Don't** add motion beyond the bar fill and the card hover lift, and never animate when reduced motion is requested.
