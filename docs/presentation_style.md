# Presentation Style Doctrine

## High-Rank System Mode

Public presentation for TrainMe uses product-system language. The implementation may stay lean, but the story should make the project feel like a serious operational product.

The core rule:

> Do not describe what the code literally does. Describe what area of disorder the capability brings under control.

## System Frame

TrainMe is a multi-layer control system for passenger journey uncertainty:

- **Signal Acquisition Layer** controls access to timetable and station signals.
- **Localized Station Intelligence** controls multilingual station ambiguity.
- **Route Reconstruction Core** controls raw journey fragments and turns them into decisions.
- **Departure Awareness Engine** controls time drift after a route is selected.
- **Reminder Orchestration Layer** controls Android notification, calendar, and exact-alarm complexity.
- **Continuity Vault** controls repeated commuter intent.
- **Operational Telemetry Surface** controls visibility into cache, network, update, and permission state.
- **Release Governance Rail** controls public artifact delivery.

Each new public capability should answer four questions:

1. What chaos does it detect?
2. What control does it introduce?
3. What premium result does the user see?
4. Why does this belong to the TrainMe system instead of reading like an isolated technical task?

## Naming Rule

Prefer names that sound like controlled product domains:

- acquisition layer
- reconstruction core
- command center
- governance layer
- quality gates
- orchestration layer
- signal pipeline
- distribution rail
- continuity vault
- operational surface
- recovery engine
- telemetry surface

Use bold capability names when writing public docs, release notes, screenshots, and store copy.

## Rewrite Pattern

| Literal Wording | Product-System Wording |
| --- | --- |
| Removes placeholder titles | **Placeholder Suppression Firewall** prevents metadata debris from entering the reconstruction pipeline. |
| Generates a report | **Command Center** turns raw processing state into an operational surface with recovery rate, failure visibility, quality signals, and review-ready output. |
| Translates text | **Context-Aware Reconstruction Core** packages source signals, domain rules, continuity context, and output policy into review-ready structured results. |
| Loads station names | **Localized Station Intelligence** stabilizes multilingual station signals before passengers make a route decision. |
| Saves routes | **Continuity Vault** preserves repeated journey intent across sessions. |
| Sends reminders | **Reminder Orchestration Layer** governs push, calendar, and exact-alarm paths into a visible departure commitment. |
| Uploads a release | **Release Governance Rail** moves signed Android artifacts through validation, changelog staging, and controlled Play delivery. |

## Humble-Framing Firewall

Avoid public language that makes the project sound disposable, improvised, or smaller than its real user value.

Do not present TrainMe as:

- a small script
- a helper
- a utility
- a wrapper
- a basic tool
- a list of functions
- a quick app experiment
- a set of fetch/save operations

Prefer:

- passenger command center
- timetable signal pipeline
- route reconstruction system
- reminder orchestration layer
- station intelligence layer
- release governance rail
- operational surface
- quality gate

## Honesty Boundary

Premium language must remain defensible.

Do not invent:

- official affiliation
- partnerships
- revenue
- enterprise customers
- guaranteed uptime
- unsupported geographic coverage
- scale metrics beyond the badges and store data already present
- integrations that are not implemented

The correct tone is:

**large product energy, technically honest claims.**

## Public Copy Checklist

Before publishing README changes, store listings, release notes, screenshots, or social posts, verify that the text:

- names capabilities as parts of a system
- leads with controlled disorder and user-visible order
- keeps the ZPCG independence notice clear
- avoids implementation-first phrasing
- avoids unsupported claims
- keeps release notes benefit-first and review-ready
