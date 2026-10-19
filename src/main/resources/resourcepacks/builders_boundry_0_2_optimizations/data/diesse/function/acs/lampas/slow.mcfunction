# Runs every 4 ticks from diesse:acs/main. Order per entity matches upstream main: support check, facing check, action.
scoreboard players set $lampas.acs_clock acs.facing 0
execute as @e[tag=acs.acc.block] at @s run function diesse:acs/lampas/entity
# Upstream resets acs.place every tick after the face checks; keep the reset after the checks so
# @p[scores={acs.place=1..}] in face/top and face/side still finds the placing player.
scoreboard players set @a[scores={acs.place=1..}] acs.place 0
