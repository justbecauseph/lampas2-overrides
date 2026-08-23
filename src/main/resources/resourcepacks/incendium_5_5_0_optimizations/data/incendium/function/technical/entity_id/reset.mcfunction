# Reset the 15-bit ID space and immediately repair every existing ID owner.
# The caller is excluded here because entity_id/check assigns it after this function returns.

tag @s add lampas.eid_reset_current

scoreboard players reset * in.eid
tag @e[tag=in.eid_0] remove in.eid_0
tag @e[tag=in.eid_1] remove in.eid_1
tag @e[tag=in.eid_2] remove in.eid_2
tag @e[tag=in.eid_3] remove in.eid_3
tag @e[tag=in.eid_4] remove in.eid_4
tag @e[tag=in.eid_5] remove in.eid_5
tag @e[tag=in.eid_6] remove in.eid_6
tag @e[tag=in.eid_7] remove in.eid_7
tag @e[tag=in.eid_8] remove in.eid_8
tag @e[tag=in.eid_9] remove in.eid_9
tag @e[tag=in.eid_10] remove in.eid_10
tag @e[tag=in.eid_11] remove in.eid_11
tag @e[tag=in.eid_12] remove in.eid_12
tag @e[tag=in.eid_13] remove in.eid_13
tag @e[tag=in.eid_14] remove in.eid_14

execute as @a[tag=!lampas.eid_reset_current] run function incendium:technical/entity_id/init
execute as @e[type=#incendium:mobs_no_player,tag=in.checked,tag=!lampas.eid_reset_current] run function incendium:technical/entity_id/init

tag @s remove lampas.eid_reset_current

tellraw @a[tag=in.admin,tag=in.debug] {translate:"incendium.system.eid.reset",fallback:"Resetting entity IDs",color:"#ff8000"}
