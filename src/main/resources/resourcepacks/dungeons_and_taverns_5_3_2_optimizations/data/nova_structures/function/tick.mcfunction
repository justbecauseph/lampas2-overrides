# Lampas2 override of Dungeons and Taverns 5.3.2 tick.
# Upstream removes the Swift Soar FOV modifier from every player every tick; the removal fails for everyone
# not riding a sprinting happy ghast and builds a CommandSyntaxException each time. Only players tagged by
# the overridden swift_soar_* functions carry the modifier, so only they pay for the check and removal.
execute as @a[tag=lampas.dnt_fov_boost,predicate=!nova_structures:riding_happy_ghast] run function nova_structures:lampas/clear_fov_boost
# One-time migration for players who received the modifier before this override was installed.
execute as @a[tag=!lampas.dnt_fov_migrated] run function nova_structures:lampas/migrate_fov_boost
execute as @a if items entity @s player.cursor *[minecraft:custom_data~{dnt_chart_bundle:false}] run item modify entity @s player.cursor nova_structures:chart_bundle_ready
