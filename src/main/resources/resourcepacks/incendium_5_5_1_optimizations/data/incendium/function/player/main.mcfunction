# from: clocks/main
# @s: player tick

# say hello?
# no, I don't think I will :)

#> in.valid_player
#>   a non-invis player, non-creative, non-spectator player
#>   used extensively in custom mob targetting and more
tag @s remove in.valid_player
tag @s remove in.survival_player
tag @s[gamemode=survival] add in.survival_player
tag @s[gamemode=adventure] add in.survival_player
tag @s[tag=in.survival_player,tag=!in.invis] add in.valid_player

# check eid resets
execute unless score @s in.eid matches 0..32767 run function incendium:technical/entity_id/check

# are scores set?
execute unless entity @s[scores={in.cd_shield=-1..}] run scoreboard players add @s in.cd_shield 0
execute unless entity @s[scores={in.cd_charm=-1..}] run scoreboard players add @s in.cd_charm 0
execute unless entity @s[scores={in.cd_corrupt=-1..}] run scoreboard players add @s in.cd_corrupt 0

#function incendium:player/advancements/main

# items
# shielding
scoreboard players set @s[advancements={incendium:technical/using/shield=false}] in.shielding 0
execute if entity @s[advancements={incendium:technical/using/shield=true}] run function incendium:player/event/shielding


## scarlet dagger particles
execute if entity @s[predicate=incendium:random/25,predicate=incendium:holding/scarlet_dagger] anchored eyes positioned ^ ^ ^1 run particle minecraft:dust{color:[1.0, 0.2, 0.0], scale:1.0} ~ ~ ~ 0.6 0.6 0.6 0 2 force

## voltaic trident
execute if entity @s[scores={in.trident=1..}] run function incendium:item/voltaic_trident/on_throw
# Lampas2: the seven held-item checks that stood here (voltaic trident holding .. sentry's wrath) are in
# player/lampas_held_items, unchanged and in the same order. Each needs an Incendium item in the main hand, off hand
# or head slot, so one predicate over those three slots decides whether any of them can match.
execute if entity @s[predicate=incendium:lampas/holds_incendium_item] run function incendium:player/lampas_held_items

# trailblazer
execute unless score @s in.wet matches -2147483647.. run scoreboard players set @s in.wet 0
execute if entity @s[tag=in.trailblazer_fix,scores={in.trailblazer=0},predicate=incendium:holding/trailblazer] run function incendium:item/trailblazer/fix
execute if entity @s[scores={in.wet=..0,in.use_bow=1..},predicate=incendium:holding/trailblazer] run function incendium:item/trailblazer/shot
execute if entity @s[scores={in.wet=..0,in.trailblazer=1..}, advancements={incendium:technical/using/trailblazer={requirement=false}}] run function incendium:item/trailblazer/on_unload
execute if entity @s[scores={in.wet=..0},advancements={incendium:technical/using/trailblazer={requirement=true}}] run function incendium:item/trailblazer/using
execute if entity @s[scores={in.wet=1..},advancements={incendium:technical/using/trailblazer={requirement=true}}] run function incendium:item/trailblazer/using/while_wet

# ragnarok
execute if entity @s[tag=in.ragnarok_fix,scores={in.ragnarok=0},predicate=incendium:holding/ragnarok] run function incendium:item/ragnarok/fix
execute if entity @s[scores={in.use_bow=1..},predicate=incendium:holding/ragnarok] run function incendium:item/ragnarok/shot
execute if entity @s[scores={in.ragnarok=1..}, advancements={incendium:technical/using/ragnarok={requirement=false}}] run function incendium:item/ragnarok/on_unload
execute if entity @s[advancements={incendium:technical/using/ragnarok={requirement=true}}] run function incendium:item/ragnarok/using

# restless nature
execute if entity @s[predicate=incendium:holding/restless_nature,predicate=incendium:dimension/nether] run scoreboard players set @s in.restless_nature 5
execute if entity @s[scores={in.use_snowball=1..,in.restless_nature=1..}] run execute as @e[type=snowball,distance=..2,limit=1,sort=nearest] run function incendium:item/restless_nature/init
execute if entity @s[scores={in.restless_nature=1..}] run scoreboard players remove @s in.restless_nature 1
scoreboard players set @s in.use_snowball 0

# scroll of returning
execute if entity @s[scores={in.use_map=1..},tag=in.scroll] run function incendium:item/patron/scroll_of_returning/use

execute if entity @s[tag=in.mainhand_scroll,predicate=!incendium:holding/scroll_of_returning/mainhand] run tag @s remove in.mainhand_scroll
execute if entity @s[tag=!in.mainhand_scroll,predicate=incendium:holding/scroll_of_returning/mainhand] run tag @s add in.mainhand_scroll

execute if entity @s[tag=in.offhand_scroll,predicate=!incendium:holding/scroll_of_returning/offhand] run tag @s remove in.offhand_scroll
execute if entity @s[tag=!in.offhand_scroll,predicate=incendium:holding/scroll_of_returning/offhand] run tag @s add in.offhand_scroll

tag @s[tag=in.offhand_scroll] add in.scroll
tag @s[tag=in.mainhand_scroll] add in.scroll

tag @s[tag=!in.offhand_scroll,tag=!in.mainhand_scroll] remove in.scroll

# decr scoreboards
scoreboard players remove @s[scores={in.cd_shield=1..}] in.cd_shield 1
scoreboard players remove @s[scores={in.cd_corrupt=1..}] in.cd_corrupt 1
scoreboard players remove @s[scores={in.cd_charm=1..}] in.cd_charm 1
scoreboard players remove @s[scores={in.cd_weapon=1..}] in.cd_weapon 1
scoreboard players remove @s[scores={in.cd_wrath=1..}] in.cd_wrath 1
scoreboard players remove @s[scores={in.wet=1..}] in.wet 1


# resets
scoreboard players set @s in.use_map 0

scoreboard players set @s in.use_bow 0
scoreboard players set @s in.trident 0
