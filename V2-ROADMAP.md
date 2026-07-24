# Painter v2 roadmap (version2 branch)

`master` = **Release 1** (stable, tagged `v1.0`). All v2 work happens here on
`version2` and only merges back once approved.

## The unifying idea

v2 is built around a single core concept that merges the two block-selection
models we discussed:

> **The brush is a grid. Each cell holds either a specific block *or* a `RANDOM`
> marker. `RANDOM` cells draw from a weighted palette (the old % system).**

So the "pixel-art stamp" and the "weighted random fill" aren't two separate
features — they're the same grid with two kinds of cell. Preset patterns
(checkerboard, stripes) are just convenience fills for that grid.

Data model (stored in ItemStack NBT via `BrushData`):
- `grid`: N×N cells, each = a block id **or** the sentinel `RANDOM`
- `palette`: weighted block → % map, used by any `RANDOM` cell (already exists)
- `size`, `shape`, `mask`, `profile`: as today

## Increments (in order)

- [x] **1. Custom Paintbrush item** — dedicated `painter:paintbrush`, no more
      vanilla-brush hijack. Painting/tooltip/outline/commands all key off it.
      *(done — commit after this doc)*
- [x] **2. Pattern modes (logic-only, command-driven).** `RANDOM` (current),
      `CHECKERBOARD`, `STRIPES` in `PainterLogic`, chosen deterministically from
      the target `BlockPos` so patterns tile seamlessly across a wall. Stored as
      a `pattern` field on the brush. Set via `/paintbrush pattern <type>`; shown
      in the tooltip. Non-random modes ignore weights and cycle the palette blocks
      (ordered by registry id). *(done)*
- [x] **3. The grid itself.** N×N cell grid in `BrushData` + `PainterLogic`
      (cell = specific block, or RANDOM → falls back to palette/pattern). Grid is
      tied to the current size, stored row-major in NBT. Command-driven:
      `/paintbrush grid set <row> <col> <block>`, `grid random <row> <col>`,
      `grid fill <block>`, `grid clear`. Painting now works from a palette AND/OR
      a grid (a pure grid needs no palette). Tooltip shows an ASCII grid preview.
      *(done)*
- [x] **4. Configuration GUI.** `Screen` opened by right-clicking the brush in the
      air (`PaintbrushItem.use`). Grid editor (click a cell to select, right-click =
      RANDOM); **searchable, scrollable icon-grid block picker**; **weighted-palette
      editor** (+ Add / Remove, per-entry % slider + numeric box, kept in sync); and
      a **Pattern** cycle button (Random/Checker/Stripes). Picked blocks route to the
      selected grid cell or, in "+ Add" mode, to the palette. "Apply" sends a
      `BrushConfigPacket` (grid + palette + pattern) over a Forge `SimpleChannel` →
      server writes it to the held brush (item NBT is server-authoritative).
      **Still needs in-game playtesting** — layout/rendering can't be compile-verified.
- [x] **5. Undo.** Server-side single-level undo: `/paintbrush undo` reverts your
      last paint. Restores the block states (skipping any cell changed since) and
      best-effort reverses the item economy — refunds the placed blocks, removes the
      blocks painting handed back. In creative it just restores blocks. Not persisted
      across a server restart. *(done)*

## Feedback round 1 (playtest fixes)

- [x] **Everything through the UI.** Size (stepper), shape (cycle), pattern (cycle),
      and a **mask editor** (add/remove block set) are now all in the GUI, alongside
      the grid and palette. Commands still exist as a secondary interface.
- [x] **Vanilla brush decoupled** — confirmed already off `Items.BRUSH` on this branch
      (only the crafting recipe references it). Bumped `mod_version` to `4.0.0-dev` so
      v2 jars (`painter-4.0.0-dev.jar`) don't collide with the Release 1 jar name.
- [x] **Palette locks to 100%.** Weights auto-normalize: editing one entry (slider or
      numeric box) proportionally rebalances the others; adding/removing re-normalizes.
      The strip shows live percentages.
- [x] **Split right-click.** Plain right-click paints; **Shift + right-click opens the
      GUI** (both on a block and in the air).

Remaining from the user: "a few more fixes" to be addressed next.

## Notes / open questions

- Recipe is currently `brush + white_dye`; revisit once the item feels right.
- Creative-tab placement: Tools & Utilities for now.
- Existing v1 brushes (on the vanilla brush) do **not** carry over to the new
  item — that's expected for a major version.
