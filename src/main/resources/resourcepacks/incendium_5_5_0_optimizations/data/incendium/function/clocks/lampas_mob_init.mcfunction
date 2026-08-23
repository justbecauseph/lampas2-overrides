# Called by clocks/main every 5 ticks.

scoreboard players set $lampas.mob_init in.dummy 0
execute as @e[type=#incendium:mobs_no_player, tag=!in.checked] at @s run function incendium:entity/mob/init
