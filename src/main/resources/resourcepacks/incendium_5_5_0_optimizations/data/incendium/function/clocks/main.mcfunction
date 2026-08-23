# Incendium 5.5.0 clock with Lampas2's mob-init scan throttled to every 5 ticks.

schedule function incendium:clocks/main 1t replace

function incendium:technical/main
execute as @a at @s run function incendium:player/main

# Mob initialization is not latency-sensitive. Keep its counter in Incendium's existing dummy objective.
scoreboard players add $lampas.mob_init in.dummy 1
execute if score $lampas.mob_init in.dummy matches 5.. run function incendium:clocks/lampas_mob_init

execute as @e[type=#incendium:mobs_no_player, tag=in.ticking_entity] at @s run function incendium:entity/mob/main

scoreboard players remove @e[type=#incendium:mobs,scores={in.frozen=1..}] in.frozen 1
execute at @e[type=#incendium:mobs,scores={in.frozen=1..},predicate=incendium:random/10] run particle minecraft:snowflake ~ ~1.6 ~ 0.1 0.05 0.1 .1 5 force

# Non-living initialization remains at 20 Hz because it detects altar items and short-lived projectiles.
execute as @e[type=#incendium:other, tag=!in.checked] at @s run function incendium:entity/other/init
execute as @e[type=#incendium:other, tag=in.ticking_entity] at @s run function incendium:entity/other/main

execute if entity @a[tag=nbs_borderofli] run function incendium:border_of_life/tick
