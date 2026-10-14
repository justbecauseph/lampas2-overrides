# Incendium 5.5.1 helper-marker check that re-adds the chunk [0, 0] force-load before counting.
# Upstream adds the force-load only in technical/load; when something removes it, @e cannot see the markers
# in the unloaded chunk, counts 0 and summons another every 7s. Re-adding it here keeps the markers visible,
# so the count below removes every duplicate.

execute in minecraft:overworld unless loaded 0 0 0 run forceload add 0 0

execute store result score $count in.dummy if entity @e[type=marker,tag=in.helper_entity]

execute unless score $count in.dummy matches 1 run kill @e[type=marker,tag=in.helper_entity]
execute unless score $count in.dummy matches 1 in minecraft:overworld run summon marker 0.0 0.0 0.0 {Tags:["in.helper_entity", "smithed.entity"]}
execute unless score $count in.dummy matches 1 in minecraft:overworld run tp @e[type=marker,tag=in.helper_entity] 0.0 0.0 0.0
