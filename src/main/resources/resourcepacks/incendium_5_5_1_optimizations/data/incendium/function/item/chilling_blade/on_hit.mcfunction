# from: player/on_hit/entity
# @s: entity being hit

effect give @s slowness 6 1

particle minecraft:snowflake ~ ~1.6 ~ 0.1 0.05 0.1 0.01 10 normal
particle minecraft:snowflake ~ ~1.6 ~ 0.1 0.05 0.1 0 20 normal

playsound minecraft:entity.player.hurt_freeze player @a[distance=..12] ~ ~ ~ 0.37 1.45
playsound minecraft:block.powder_snow.break player @a[distance=..12] ~ ~ ~ 1 0.5

scoreboard players set @s in.frozen 120

# Lampas2: arm clocks/lampas_frozen, which runs the in.frozen walk only while this score is positive.
scoreboard players set $lampas.frozen in.dummy 200
