# Called by clocks/main while $lampas.frozen is positive. item/chilling_blade/on_hit, the only function that gives a mob
# in.frozen, sets $lampas.frozen to 200; this counts it down and re-arms it while any loaded mob is still frozen.

execute as @e[type=#incendium:mobs,scores={in.frozen=1..},predicate=incendium:random/10] run function incendium:entity/chilling
scoreboard players remove $lampas.frozen in.dummy 1
execute if score $lampas.frozen in.dummy matches ..0 if entity @e[type=#incendium:mobs,scores={in.frozen=1..},limit=1] run scoreboard players set $lampas.frozen in.dummy 200
