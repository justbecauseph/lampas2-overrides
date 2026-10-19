# Runs once per player: clear a modifier left by the upstream tick before this override, unless they are riding.
execute unless predicate nova_structures:riding_happy_ghast run attribute @s movement_speed modifier remove nova_structures:boost_player_fov
tag @s add lampas.dnt_fov_migrated
