# Bee and spawner structure DFU validation

Phase 2B validates the exact runtime fixtures that previously produced bee and spawner decode failures. The run used the override artifact `e2f05414…` and Trinkets 4.1.0 (`e1b49012…`), recorded in [the evidence file](evidence/phase2b-bees-spawner.json). The 4.1.0 repair profile is no longer supported, so this run is historical. These fixtures have not been re-validated with the current Trinkets 4.1.1 repair.

The Trek `strange_stone` fixture loaded at `[600,80,0]` with size `[9,10,11]`. Its two hives retained the complete vanilla STRUCTURE-DFU bee lists, including timer pairs `(600,378)/(600,368)` and `(600,455)/(600,443)`, bee IDs, attributes, and nested entity properties.

The Stoneholm `bell_01` fixture loaded at `[700,80,0]` with size `[9,5,9]`. Its spawner retained every vanilla reference semantic field: zombie `SpawnData`, one weight-one zombie potential, `SpawnCount=4`, `Delay=0`, delays `200/800`, range `4`, required player range `16`, and maximum nearby entities `6`.

Both templates loaded and placed successfully, produced zero target decode or serialization errors, and the server exited cleanly. This confirmed that the 4.1.0 repair covered these exact fixtures; it does not claim validation of every structure in the pack.

The focused semantic suite and full repository verification reported 75 tests with zero failures or errors at that run, and `./gradlew.bat build` succeeded. The verified artifact SHA-256 was `e2f05414e9fdc5c16f7860670c14cef05e7b50f78789917afbc11d01b36ebbc8`. No deployment or vendor JAR modification was performed.
