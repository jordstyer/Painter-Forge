# Painter

**Reskin your builds without breaking them.** Painter gives you a dedicated
Paintbrush that swaps existing blocks for new ones in a radius around your
cursor — turn a cobblestone wall into stone brick, randomize a floor between
three flooring blocks, or hand-paint a pixel-art pattern onto a surface,
all without breaking and replacing a single block by hand.

Survival-friendly by design: painting consumes matching blocks from your
inventory and refunds the blocks it replaces, so it's balanced for
survival worlds, not just creative.

---

## ✨ Features

**A dedicated Paintbrush item** — craft one with a vanilla Brush + White
Dye. It doesn't touch the regular archaeology brush, so that's still free
for actual brushing.

**Two ways to paint:**
- **Randomize** — every click draws a fresh, weighted-random block from
  your palette. Great for natural variation (stone / cobblestone / mossy
  cobblestone mixed together, for example).
- **Custom** — paint a literal per-cell template onto your brush's grid.
  Assign specific blocks to specific cells for checkerboards, borders, or
  hand-designed pixel patterns, with any leftover cells still drawing
  randomly from your palette.

**A full in-game configuration screen** (Shift + right-click the brush):
- Searchable, scrollable block picker with every block in the game —
  including blocks from other installed mods
- Multi-select blocks, then add them to your palette or your mask in one
  click
- Palette weights are sliders + numeric boxes that always stay normalized
  to 100%
- A visual grid editor for Custom mode — click a cell, click a block,
  done
- Brush size (1–5) and shape (square / circle / diamond)

**Masking** — restrict painting to only certain blocks (*"only replace
stone and dirt"*), or flip it to exclude certain blocks instead
(*"paint over anything except glass"*).

**Built-in safety nets:**
- `/paintbrush undo` reverts your last paint — blocks and inventory both
  roll back
- A brief cooldown between paints keeps holding right-click from
  hammering the server with block updates
- Ores are never duplicated — painting over an ore destroys it instead of
  returning a free item
- Fragile and structural blocks (doors, beds, stairs, slabs, block
  entities, etc.) are protected from accidental painting

**Everything is also available via commands** if you'd rather not open
the GUI — see `/paintbrush help` in-game for the full list.

---

## 🧠 How it works

1. Craft a **Paintbrush** (Brush + White Dye).
2. Hold it and **Shift + right-click** to open the configuration screen.
3. Search for blocks, select them, and add them to your **palette**
   (what you paint *with*) and optionally a **mask** (what you paint
   *onto*).
4. Pick **Randomize** or **Custom** mode, set your brush size/shape, and
   hit **Apply**.
5. **Right-click** a block to paint. Right-click again to keep going.
6. Made a mistake? `/paintbrush undo`.

---

## 📋 Requirements

- Minecraft **1.20.1**
- **Forge 47.2.0** or later
- Java 17

---

## 💬 Feedback

This mod is actively maintained — bug reports and feature suggestions are
welcome via the issue tracker.
