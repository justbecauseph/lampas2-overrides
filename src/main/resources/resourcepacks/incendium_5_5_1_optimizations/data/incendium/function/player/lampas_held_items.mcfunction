# Called by player/main for a player whose main hand, off hand or head slot holds an Incendium item.
# These are upstream 5.5.1's lines, unchanged and in upstream order.

execute if entity @s[predicate=incendium:holding/voltaic_trident] run function incendium:item/voltaic_trident/holding

## hefty pickaxe
execute if entity @s[predicate=incendium:holding/hefty_pickaxe] run function incendium:item/hefty_pickaxe/update

## scout's helm
execute if entity @s[predicate=incendium:wearing/scouts_battle_helm] run function incendium:item/scouts_battle_helm/main

## loaded spec. arrow
# firestorm
execute if entity @s[predicate=incendium:holding/firestorm] run function incendium:item/firestorm/holding

execute if entity @s[predicate=incendium:holding/loaded,predicate=!incendium:holding/loaded_spectral_arrow,predicate=incendium:holding/firestorm] run function incendium:item/firestorm/check_arrow

# holy wrath
execute if entity @s[predicate=incendium:holding/loaded,predicate=!incendium:holding/loaded_spectral_arrow,predicate=incendium:holding/holy_wrath] run function incendium:item/patron/holy_wrath/check_arrow

# sentrys wrath
execute if entity @s[predicate=incendium:holding/sentrys_wrath] run function incendium:item/sentrys_wrath/holding
