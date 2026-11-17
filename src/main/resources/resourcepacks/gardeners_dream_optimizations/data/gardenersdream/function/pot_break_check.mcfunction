# Lampas2 override of Gardener's Dream pot_break_check (audited on 2.6).
# Upstream runs pot_broken once per online player (execute as @a). pot_broken re-binds @s on every line with an
# un-ranged @e[type=block_display,tag=...] selector and never reads the player, so each pass after the first walks
# the whole loaded entity list four more times and finds nothing left to do. One pass per firing does the same work.
# The @a[limit=1] test keeps upstream's behaviour of not checking while no player is online.
execute if entity @a[limit=1] run function gardenersdream:pot_broken

execute if score #gd_setting gd_check_delay matches 3 run schedule function gardenersdream:pot_break_check 2.0s
execute if score #gd_setting gd_check_delay matches 2 run schedule function gardenersdream:pot_break_check 1.0s
execute if score #gd_setting gd_check_delay matches 1 run schedule function gardenersdream:pot_break_check 0.3s
