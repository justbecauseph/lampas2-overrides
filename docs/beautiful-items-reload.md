# Beautiful item-model reload compatibility

Beautiful Enchanted Books `6.0.0` (`beb`) and Beautiful Potions `2.0.1`
(`beautiful_potions`) register Fabric extra-model keys during the model-loading plugin's
`initialize(Set, ModelLoadingPlugin.Context)` callback. The installed implementations create a new
`ExtraModelKey` for each discovered item and store it with `REGISTERED_MODELS.putIfAbsent`. After a
resource reload, that map can retain the key from the previous `ModelManager`; resolving the stale
key returns `null`.

The client-only compatibility mixins clear `REGISTERED_MODELS` at the HEAD of the exact initializer
overload. Upstream then stores the newly created keys before registering each extra model for the
current reload. Each target is gated independently by mod ID, exact metadata version, a single
readable path origin, and the SHA-256 of the full installed JAR.

| Mod ID | Version | Installed JAR SHA-256 |
|---|---:|---|
| `beb` | `6.0.0` | `08819eb4b0773d389b850dad7cc0c2134a1fcb5d3693d28e03cc8c309181fa8f` |
| `beautiful_potions` | `2.0.1` | `53d132f68b9c97105699e3de3542ad9f3ef5ec4e4df5e147f00e5f8510e33a82` |

The class and method descriptors, static final map fields, and registration bytecode were confirmed
from the installed JARs with `javap`. The independent profile tuples, fail-closed origin handling,
client-only config, callback-only HEAD injections, and `Map.clear()` calls are also covered by focused
tests.

## Isolated client probe

The final baseline run `20261006T053439Z-d7e9ca4b9b` launched the exact vendor artifacts without
lampas2-overrides. All `695/695` enchanted-book keys and `828/828` potion keys resolved after initial
loading. Two real resource reloads retained the old map entries but resolved `0/695` and `0/828` keys.
The baseline maps also retained removed entries during selected-resource and empty-resource checks.

The final patched run `20261006T053345Z-310cfe8b18` used lampas2-overrides JAR SHA-256
`581569dd77d722b7d7bfbc87853edaf9df96e10536405721c3869d6dd9f413e4`. Two successive resource reloads
kept all `695/695` book and `828/828` potion keys resolvable. Filtering the selected Sharpness and
Swiftness extra models reduced the maps to `694` and `827`, every remaining key resolved, and both
selected keys were absent. Blocking all item-model resources cleared both maps to zero. Restoring
the resources restored all keys and lookups. Both client processes exited with code `0`.

Runtime PATH origins and hashes match the selected fixture JARs. The patched log records both mixins
applying; exported vendor classes show the HEAD clear before the original registration loop and an
intact vendor map initializer.

The controlled GUI item resolver selects the Sharpness book and Swiftness potion custom particle
sprites initially. After reload the baseline selects vanilla sprites, while the patched client keeps
the custom sprites. Selected-resource removal selects vanilla sprites and restoration selects the
custom sprites again. The empty stage deliberately skips sprite selection because all baked item
models were removed.

This title-screen fixture conditionally binds the two unbound item holders to empty component maps,
supplies explicit `ITEM_MODEL` and stored-enchantment/potion components, and uses a synthetic
Sharpness holder. It has no world registry synchronization. These checks establish model selection
for controlled stacks, not full-pack gameplay or visible inventory, held-item, or shader rendering.

Full Gradle test/build passed `111` tests in `19` suites with no failures, errors, or skips. Pipeline
`bun test` passed `179` tests with one optional live-network skip, and `bun run build` passed.

See the [probe runner](../tools/beautiful-items-probe/run.py) and [captured evidence](evidence/beautiful-items-reload.json).

The reproduction, key-retention cause, and local repair results were added to
[upstream BEB issue #7](https://github.com/CERBON-s-Comissions/Beautiful-Enchanted-Books/issues/7#issuecomment-6010109674).
