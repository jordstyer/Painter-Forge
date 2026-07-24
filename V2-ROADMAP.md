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
- [ ] **2. Pattern modes (logic-only, command-driven).** Add `RANDOM` (current),
      `CHECKERBOARD`, `STRIPES` to `PainterLogic`, chosen deterministically from
      the target `BlockPos` so patterns tile seamlessly across a wall. Stored as
      a `pattern` field on the brush. Quick, self-contained, testable via chat.
- [ ] **3. The grid itself.** Introduce the N×N cell grid in `BrushData` +
      `PainterLogic` (cell = block or RANDOM). Still command-driven for now
      (e.g. `/paintbrush cell <x> <y> <block|random>`), so we can validate the
      model before building UI on top of it.
- [ ] **4. Configuration GUI.** A `Screen` opened by right-clicking the brush in
      the air (override `PaintbrushItem.use`). Shows the grid (click a cell →
      assign block or RANDOM), a palette editor (add-block button, per-block
      slider + numeric box), and preset-fill buttons (checkerboard, stripes).
      **Requires networking:** item NBT is server-authoritative, so "Apply"
      sends a packet (Forge `SimpleChannel`) → server writes the config to the
      held brush. Build incrementally: read-only view → edit+apply → grid editor.
- [ ] **5. Undo (nice-to-have).** Remember the last N painted blocks per player;
      `/paintbrush undo` or a GUI button reverts them. Makes experimenting with
      patterns non-punishing since painting consumes inventory blocks.

## Notes / open questions

- Recipe is currently `brush + white_dye`; revisit once the item feels right.
- Creative-tab placement: Tools & Utilities for now.
- Existing v1 brushes (on the vanilla brush) do **not** carry over to the new
  item — that's expected for a major version.
