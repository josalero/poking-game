---
name: Agile Pulse
colors:
  surface: '#fbf8ff'
  surface-dim: '#d8d8f1'
  surface-bright: '#fbf8ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f4f2ff'
  surface-container: '#edecff'
  surface-container-high: '#e6e6ff'
  surface-container-highest: '#e0e0fa'
  on-surface: '#181a2c'
  on-surface-variant: '#464556'
  inverse-surface: '#2d2f42'
  inverse-on-surface: '#f1efff'
  outline: '#767587'
  outline-variant: '#c6c4d8'
  surface-tint: '#4643e9'
  primary: '#413fe6'
  on-primary: '#ffffff'
  primary-container: '#5c5cff'
  on-primary-container: '#fdf9ff'
  inverse-primary: '#c1c1ff'
  secondary: '#795900'
  on-secondary: '#ffffff'
  secondary-container: '#ffbf00'
  on-secondary-container: '#6d5000'
  tertiary: '#565b61'
  on-tertiary: '#ffffff'
  tertiary-container: '#6f737a'
  on-tertiary-container: '#f8f9ff'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#e1dfff'
  primary-fixed-dim: '#c1c1ff'
  on-primary-fixed: '#08006b'
  on-primary-fixed-variant: '#2a21d3'
  secondary-fixed: '#ffdfa0'
  secondary-fixed-dim: '#fbbc00'
  on-secondary-fixed: '#261a00'
  on-secondary-fixed-variant: '#5c4300'
  tertiary-fixed: '#dfe2ea'
  tertiary-fixed-dim: '#c3c6ce'
  on-tertiary-fixed: '#181c21'
  on-tertiary-fixed-variant: '#43474d'
  background: '#fbf8ff'
  on-background: '#181a2c'
  surface-variant: '#e0e0fa'
typography:
  display-xl:
    fontFamily: Plus Jakarta Sans
    fontSize: 48px
    fontWeight: '800'
    lineHeight: 56px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
    letterSpacing: -0.01em
  headline-lg-mobile:
    fontFamily: Plus Jakarta Sans
    fontSize: 24px
    fontWeight: '700'
    lineHeight: 32px
  headline-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 28px
  body-lg:
    fontFamily: Work Sans
    fontSize: 18px
    fontWeight: '400'
    lineHeight: 28px
  body-md:
    fontFamily: Work Sans
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  label-md:
    fontFamily: Work Sans
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
    letterSpacing: 0.05em
  label-sm:
    fontFamily: Work Sans
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  base: 4px
  xs: 4px
  sm: 8px
  md: 16px
  lg: 24px
  xl: 32px
  gutter: 16px
  margin-mobile: 20px
  margin-desktop: 48px
---

## Brand & Style

The design system is centered on the intersection of productivity and playfulness. It targets agile teams who value efficiency but want to maintain a high-energy, collaborative spirit during estimation sessions. The emotional response is one of clarity, momentum, and shared purpose.

The style is **Modern Professional with a Playful Edge**. It utilizes clean, systematic layouts to convey reliability, while introducing "squishy" tactile elements and vibrant color pops to prevent the experience from feeling sterile. Key characteristics include generous whitespace, soft-touch surfaces, and energetic micro-interactions that mirror the physical act of flipping a planning card.

## Colors

The palette is anchored by a deep **Indigo Primary** (#5C5CFF) which represents the professional, systematic nature of Scrum. This is contrasted by a **Soft Amber Secondary** (#FFBF00), used sparingly for high-attention actions, achievements, and "poker card" highlights to inject warmth and energy.

The background uses a **Soft Tinted Neutral** (#F4F7FF) instead of pure white to reduce eye strain and provide a more premium, cohesive feel. Text and critical UI boundaries use a **Deep Navy** (#1A1C2E) for optimal legibility and a grounded sense of authority.

## Typography

This design system utilizes **Plus Jakarta Sans** for headings to provide a friendly, rounded geometric appearance that feels contemporary and inviting. For body text and functional labels, **Work Sans** is employed due to its exceptional legibility and professional, neutral character.

The typographic hierarchy emphasizes clear scanning. Large display styles are reserved for card values and session titles. Labels use a slight uppercase tracking to differentiate them from interactive body text.

## Layout & Spacing

The design system follows a **Fluid Grid** model based on a 4px baseline. Mobile layouts utilize a 4-column structure with 16px gutters and 20px outer margins to ensure content is comfortably inset from device edges.

Spacing is used to group related "poker" cards and participant avatars. Use `lg` (24px) spacing between distinct sections (e.g., the card deck and the voting results) and `sm` (8px) for internal element grouping within cards or list items.

## Elevation & Depth

Hierarchy is established through **Ambient Shadows** and **Tonal Layering**. Surfaces do not use harsh borders; instead, they rely on soft, multi-layered shadows with a slight Indigo tint (#5C5CFF at 8-12% opacity) to create a sense of floating objects.

- **Level 0 (Background):** Primary surface (#F4F7FF).
- **Level 1 (Cards/Containers):** Pure white with a 4px blur shadow.
- **Level 2 (Active/Selected Card):** Pure white with a 12px blur shadow and a 2px Primary Indigo border.
- **Level 3 (Modals/Overlays):** Pure white with a 24px diffused shadow.

Interactive elements should appear to "lift" on press by increasing the shadow spread and slightly scaling up (1.02x).

## Shapes

The shape language is consistently **Rounded**, reinforcing the friendly and collaborative brand personality. 

- **Standard Buttons & Inputs:** 0.5rem (8px) corner radius.
- **Estimation Cards:** 1rem (16px) corner radius for a tactile, handheld feel.
- **Avatars & Status Indicators:** Fully circular (Pill-shaped) to represent individual people within the team.
- **Selection Chips:** Fully circular for a "pill" aesthetic that feels distinct from action buttons.

## Components

### Buttons
Primary buttons use the Indigo background with white text. High-priority "Reveal" or "Finish" buttons may use the Amber background for maximum visibility. Buttons should have a minimum height of 48px for mobile tap targets.

### Estimation Cards
Cards are the hero component. They feature a white background, centered display-xl typography for the value, and a subtle pattern or logo in the corner. When selected, the card gains a 2px Primary Indigo stroke.

### Avatars & Participant Lists
Participants are shown as circular avatars. When a user has voted, a green checkmark badge appears at the bottom-right of their avatar. If they are currently "thinking," a pulsing Indigo ring surrounds the avatar.

### Data Visualization
Bar charts for vote distribution should use rounded caps. The "winning" or "consensus" estimate is highlighted in Amber, while others remain in a soft Indigo-gray.

### Input Fields
Search or session name inputs use a white background with a subtle 1px border (#D1D5DB). On focus, the border transitions to Primary Indigo with a soft outer glow.