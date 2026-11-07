---
name: NHA Monitor Web
description: The NHA Monitor Android app on a wide screen; a read-only portfolio and project Summary viewer.
colors:
  primary: "#0A6FD4"
  on-primary: "#FFFFFF"
  primary-container: "#E5F4FF"
  on-primary-container: "#0757A8"
  canvas: "#F5F5F5"
  surface: "#FFFFFF"
  surface-variant: "#F0F0F0"
  text: "#1E1E1E"
  text-2: "#666666"
  border: "#E6E6E6"
  outline: "#8C8C8C"
  ok: "#15803D"
  ok-container: "#DCFCE7"
  watch: "#B45309"
  watch-container: "#FEF3C7"
  behind: "#B91C1C"
  behind-container: "#FEE2E2"
  neutral: "#6B6B6B"
  neutral-container: "#F0F0F0"
  hero-start: "#0A6FD4"
  hero-end: "#06396E"
  primary-dark: "#3DADFF"
  on-primary-dark: "#1E1E1E"
  primary-container-dark: "#1B3A57"
  on-primary-container-dark: "#E5F4FF"
  canvas-dark: "#1E1E1E"
  surface-dark: "#2C2C2C"
  surface-variant-dark: "#383838"
  text-dark: "#F5F5F5"
  text-2-dark: "#A3A3A3"
  border-dark: "#444444"
  outline-dark: "#8A8A8A"
  ok-dark: "#4ADE80"
  ok-container-dark: "#173322"
  watch-dark: "#FBBF24"
  watch-container-dark: "#3A2D10"
  behind-dark: "#FCA5A5"
  behind-container-dark: "#3F1E1E"
  neutral-dark: "#A3A3A3"
  hero-start-dark: "#15508F"
  hero-end-dark: "#0A2440"
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

The web viewer is the NHA Monitor Android app shown on a laptop or a meeting-room projector. It is not a separate identity. The tokens in `web/style.css` mirror the app's theme (`app/src/main/java/com/example/ui/theme/Color.kt`, `Type.kt`, `Theme.kt`, `Motion.kt`), and the app is the source of truth: a token changes here only when it changes there. Someone who uses the app on a phone should find the same header, the same chips, and the same bars in the same places.

The look is a calm Figma-style system: a grey canvas, white panels with 1px hairlines and 8px corners, one blue, and four status colours that only ever describe a project's state. Density is moderate. Screens have to read on a projector, so the hierarchy comes from weight (600 titles over 400 body) and grey secondary text, not from big type. Everything is read-only and quiet. Colour appears where it carries meaning (progress, slippage, attention) and almost nowhere else.

It rejects the generic admin template (side menu, KPI cards, blue-on-white data tables) and it does not imitate official NHA or government branding.

**Key Characteristics:**
- Mirrors the Android app's tokens exactly, in light and dark (follows `prefers-color-scheme`).
- Grey canvas, white hairline panels, 8px card radius, a faint ambient lift.
- One blue for interaction and the Billed bar. Status colours are for status only.
- Inter throughout. Tabular figures only on numbers that line up.
- Material Symbols Outlined icons at 20px (16px small), always next to a text label.
- One motion: the progress bars fill in when data arrives.

## Colors

A restrained grey-and-white base with a single Figma blue and a four-step status scale, each with a tinted container. Dark mode swaps in the `-dark` values. The roles stay the same.

### Primary
- **Figma Blue** (`primary`, dark `primary-dark`): buttons, text buttons, links, focus rings, the caret, section-head icons, and the Billed progress bar. Selected chips and the "missing data" note use **Blue Mist** (`primary-container`) with **Deep Blue** text (`on-primary-container`).
- **Brand Gradient** (`hero-start` to `hero-end`, 135deg): used only on the sign-in hero panel, which is the app's LoginScreen brand area. Text on it is white.

### Status (semantic, from the app's onTrack / watch / behind / neutral)
- **On Track Green** (`ok`) on `ok-container`: projects on schedule, the Work done bar when slippage is small, and success notes.
- **Watch Amber** (`watch`) on `watch-container`: slippage to keep an eye on, warning-level attention flags.
- **Behind Red** (`behind`) on `behind-container`: projects behind schedule, critical flags, form errors.
- **Neutral Grey** (`neutral`) on `neutral-container`: no status yet (not started, no data).

### Neutral
- **Canvas Grey** (`canvas`): the page background.
- **Panel White** (`surface`): cards, the app bar, inputs, chips, dialogs.
- **Soft Grey** (`surface-variant`): photo placeholders, scope blocks, the view-only badge.
- **Ink** (`text`) and **Secondary Grey** (`text-2`): primary and secondary text. Every label, unit, and metadata line uses `text-2`.
- **Hairline** (`border`): card borders, row dividers, the empty track of a progress bar.
- **Outline** (`outline`): input strokes, and card or chip borders on hover.

### Named Rules
**The Meaning-Only Colour Rule.** Green, amber, red, and neutral grey describe a project's state and nothing else. They are never decoration, section tints, or brand accents.

**The App-Is-Truth Rule.** Every colour token has a counterpart in `Color.kt`. Don't add or adjust a web colour that the app doesn't also have.

## Typography

**Display Font:** Inter (bundled at `web/fonts/Inter.ttf`, OFL, variable weight 100 to 900), falling back to system-ui, sans-serif
**Body Font:** Inter (same file)

**Character:** One neutral grotesque at 400 to 700. Titles get slight negative tracking. The hierarchy is built mostly from weight and grey, which matches the app's Type.kt and keeps a projector screen calm.

### Hierarchy
- **Display** (600, 34px, 1.15, -0.6px; 26px under 900px): the sign-in hero headline only, with a measure of about 14ch.
- **Headline** (600, 24px, -0.3px): the sign-in form title.
- **Title Large** (600, 22px, -0.2px): page titles (portfolio totals, project name) and the large progress figure in headers.
- **Title** (600, 15px, 1.3): project card names, clamped to two lines.
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

- **App bar:** sticky, white, with a bottom hairline. Brand block on the left, user and Sign out pushed to the right.
- **Sign-in:** a full-bleed two-column grid (hero 5fr, form 6fr) with no app bar. Under 900px it stacks, hero first. The form is at most 400px wide and centred.
- **Dashboard:** portfolio card (title beside two bars, stacked under 900px), then a toolbar row (search, scope chips, count), then a project grid of `auto-fill, minmax(min(380px, 100%), 1fr)`, which gives two or three columns on desktop.
- **Project card:** a container query. Work done and Billed bars stack as on the phone and sit side by side only when the card is at least 560px wide.
- **Project Summary:** a two-column card grid (one column under 900px). Wide sections span both columns.
- **Phone widths:** attention chips form a 2-up grid under 600px. The payments table moves the date under the billing name under 560px.

## Elevation & Depth

Mostly flat, with tonal layering. Depth comes from white panels on the grey canvas plus a 1px hairline. Cards add one faint ambient lift. Nothing gets more lift on hover: hover changes the border to `outline`. Dialogs sit on a 40% black backdrop.

### Shadow Vocabulary
- **Lift** (`box-shadow: 0 1px 2px rgb(0 0 0 / 0.06), 0 1px 3px rgb(0 0 0 / 0.04)`; dark `0 1px 2px rgb(0 0 0 / 0.4)`): every card at rest.

### Named Rules
**The Hairline-First Rule.** A panel is defined by its 1px border and its colour step from the canvas. The shadow is a whisper and never grows on interaction.

## Shapes

Small, consistent radii from the app's FigmaShapes: 4px (view badge, focus ring), 6px (buttons, inputs, status badges, photos, inner blocks), 8px (cards, chips, dialogs), and 3px for the 6px-tall progress bars. Nothing is pill-shaped or sharp-cornered. Photos are clipped to their radius with `object-fit: cover`. A site photo is a 64px placeholder until it loads, then 16:9.

## Components

### Buttons
- **Shape:** gently rounded (6px), at least 40px tall.
- **Primary:** Figma Blue fill, `on-primary` text, 600 14px, 10px 18px padding. Used once per view at most (Sign in).
- **Hover / Focus:** hover brightens the fill (`filter: brightness(1.08)`). Focus is a 2px primary outline with a 2px offset. Disabled is 0.6 opacity.
- **Text button:** blue 600 14px, 8px padding, Blue Mist background on hover. Used for Sign out, Back, Forgot password, and "What these mean". An icon leads the label.

### Chips
- **Style:** white, 1px hairline, 8px radius, 500 14px, at least 36px tall. A 16px icon leads, and a bold tabular count goes before the label on attention chips.
- **State:** selected (`aria-pressed="true"`) uses Blue Mist with Deep Blue text and no border. Hover darkens the border to `outline`. Attention chips tint only their icon (behind red, watch amber).

### Cards / Containers
- **Corner Style:** 8px.
- **Background:** Panel White on Canvas Grey.
- **Shadow Strategy:** Lift (see Elevation & Depth).
- **Border:** 1px hairline. On link cards, hover changes it to `outline` (150ms).
- **Internal Padding:** 16px to 20px. Section cards use a 48px-tall head row with a blue 20px icon and a 14px/600 title.

### Inputs / Fields
- **Style:** white, 1px `outline` stroke, 6px radius, 11px 12px padding, at least 44px tall. The label sits above (13px/500, `text-2`). The search field has a 40px left inset for the icon.
- **Focus:** the border turns primary and gains a 1px primary ring (`box-shadow: 0 0 0 1px`).
- **Error:** a 13px/500 Behind Red line under the fields, with `role="alert"`.

### Navigation
- **App bar:** a white sticky bar. The brand title is 16px/600 with the office line under it. On the right: the user line (13px `text-2`; the email hides under 600px), a view-only badge, and the Sign out text button. Navigation between pages is a Back text button with no menu.

### Status Badge
A small 6px tag in 700 11px, with status colour text on its container. The **view-only** variant is quieter: 4px radius, 500 weight, Soft Grey with `text-2` and an eye icon.

### Progress Line (signature)
A label (12px/500 `text-2`) and a value (600, tabular) over a 6px bar on the hairline track with 3px corners. **Work done** is coloured by slippage (ok, watch, behind, or neutral). **Billed** is always Figma Blue. The two always appear as a pair on every project card and header. In headers the value is 22px/600 and secondary text follows it. When fresh data arrives, the bars fill from the left (700ms, `cubic-bezier(0.16, 1, 0.3, 1)`, scaleX). This is turned off under `prefers-reduced-motion`.

## Do's and Don'ts

### Do:
- **Do** take every colour, radius, and type value from the app's theme files, and change the web tokens only together with the app.
- **Do** pair Work done (slippage colour) with Billed (blue) wherever a project's progress appears.
- **Do** put a text label next to every Material Symbols icon, and keep icons at 20px or 16px.
- **Do** apply tabular figures only to numbers that align (table cells, progress values, counts, totals).
- **Do** define panels with a 1px hairline on the grey canvas and the single Lift shadow. Hover changes the border only.
- **Do** keep the dark theme as a role-for-role swap through `prefers-color-scheme`.

### Don't:
- **Don't** use the status colours for anything other than project state.
- **Don't** introduce a side menu, KPI tiles, or a separate web identity. The web is the app on a wide screen.
- **Don't** imitate official NHA or government branding.
- **Don't** use the brand gradient outside the sign-in hero.
- **Don't** use tracked uppercase lines as section labels or eyebrows. The office line under the brand is the only uppercase text.
- **Don't** add motion beyond the bar fill and border-colour hovers, and never animate when reduced motion is requested.
