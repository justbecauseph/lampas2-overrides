# Trinkets V1460 structure DFU repair

## Cause and scope

On Minecraft 26.2, the exact `trinkets_updated` 4.1.0+26.2 hook repairs the Trinkets V1460
player/entity schema wrapper so vanilla structure DFU reaches nested items. It preserves modern,
legacy, and cosmetic inventories plus unknown data, and fails closed outside the audited shape.
The gate requires class SHA-256
`089c59253b7b9ab9f7f0a54c1f0dc7912379ad5bfa335c4acfcfbb91e17914f9`; the deployed Trinkets JAR
reference hash is `e1b49012e3fe25e63812ef73dfd0f2e0891fd6aa88c7ef4e73d781fddc714f39`.

The tested Lampas artifact is `lampas2-overrides-1.0.0.jar`, SHA-256
`e2f05414e9fdc5c16f7860670c14cef05e7b50f78789917afbc11d01b36ebbc8`.

## Validation

- 75 tests and the full build pass.
- The exact templates `grim_kingdoms:plains/zweiberg_plains` at `200,80,0` and
  `grim_kingdoms:water/blackwater` at `300,80,0` loaded with 0 decode failures, 0 schema mismatches,
  and 10 separate attachment-position errors.
- Priority 1500 runs after Trinkets priority 1000 and is proven by exported bytecode: entity offsets
  37 upstream / 42 repaired, player offsets 174 upstream / 178 repaired. The priority-500 attempt
  was rejected because it ran before the upstream wrapper.
- Runtime evidence is recorded in
  `docs/evidence/phase2a-final-priority1500.json` (with the full console log retained in the run directory).

## Limitations

The console's arbitrary item queries were inconclusive, but saved-world FastItemFrames NBT verifies
the targeted splash potion and Wraith Lens items, including names, lore, modifiers, and curse data.
Evidence is recorded in `docs/evidence/phase2a-frame-items.json`. No live deployment
has been performed.
