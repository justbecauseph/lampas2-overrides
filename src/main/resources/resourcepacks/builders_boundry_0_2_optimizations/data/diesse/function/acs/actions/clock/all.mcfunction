# Lampas2 override of Builder's Boundry 0.2 acs/actions/clock/all.
# 26.2 parses the time query argument as a timeline id, so upstream's `time query daytime` (minecraft:daytime)
# fails to load and wall clocks never change face. `time of minecraft:overworld` reads the Overworld clock from any
# dimension (the Nether has no default clock and the End's is minecraft:the_end), as upstream's global daytime did.
# Also fixes two upstream range typos that left 18100-18999 and 15000-15999 without a face.
##
 # all.mcfunction
 # 
 #
 # Created by .
##
execute store result score $daytime acs.daytime run time of minecraft:overworld query day
execute if score $daytime acs.daytime matches 16000..16999 run data merge entity @s {Item:{id:"minecraft:knowledge_book",components:{"minecraft:item_model":"minecraft:clock/0"}}}
execute if score $daytime acs.daytime matches 17000..17999 run data merge entity @s {Item:{id:"minecraft:knowledge_book",components:{"minecraft:item_model":"minecraft:clock/1"}}}
execute if score $daytime acs.daytime matches 18000..18999 run data merge entity @s {Item:{id:"minecraft:knowledge_book",components:{"minecraft:item_model":"minecraft:clock/2"}}}
execute if score $daytime acs.daytime matches 19000..19999 run data merge entity @s {Item:{id:"minecraft:knowledge_book",components:{"minecraft:item_model":"minecraft:clock/3"}}}
execute if score $daytime acs.daytime matches 20000..20999 run data merge entity @s {Item:{id:"minecraft:knowledge_book",components:{"minecraft:item_model":"minecraft:clock/4"}}}
execute if score $daytime acs.daytime matches 21000..21999 run data merge entity @s {Item:{id:"minecraft:knowledge_book",components:{"minecraft:item_model":"minecraft:clock/5"}}}
execute if score $daytime acs.daytime matches 22000..22999 run data merge entity @s {Item:{id:"minecraft:knowledge_book",components:{"minecraft:item_model":"minecraft:clock/6"}}}
execute if score $daytime acs.daytime matches 23000..23999 run data merge entity @s {Item:{id:"minecraft:knowledge_book",components:{"minecraft:item_model":"minecraft:clock/7"}}}
execute if score $daytime acs.daytime matches 0..999 run data merge entity @s {Item:{id:"minecraft:knowledge_book",components:{"minecraft:item_model":"minecraft:clock/8"}}}
execute if score $daytime acs.daytime matches 1000..1999 run data merge entity @s {Item:{id:"minecraft:knowledge_book",components:{"minecraft:item_model":"minecraft:clock/9"}}}
execute if score $daytime acs.daytime matches 2000..2999 run data merge entity @s {Item:{id:"minecraft:knowledge_book",components:{"minecraft:item_model":"minecraft:clock/10"}}}
execute if score $daytime acs.daytime matches 3000..3999 run data merge entity @s {Item:{id:"minecraft:knowledge_book",components:{"minecraft:item_model":"minecraft:clock/11"}}}
execute if score $daytime acs.daytime matches 4000..4999 run data merge entity @s {Item:{id:"minecraft:knowledge_book",components:{"minecraft:item_model":"minecraft:clock/0"}}}
execute if score $daytime acs.daytime matches 5000..5999 run data merge entity @s {Item:{id:"minecraft:knowledge_book",components:{"minecraft:item_model":"minecraft:clock/1"}}}
execute if score $daytime acs.daytime matches 6000..6999 run data merge entity @s {Item:{id:"minecraft:knowledge_book",components:{"minecraft:item_model":"minecraft:clock/2"}}}
execute if score $daytime acs.daytime matches 7000..7999 run data merge entity @s {Item:{id:"minecraft:knowledge_book",components:{"minecraft:item_model":"minecraft:clock/3"}}}
execute if score $daytime acs.daytime matches 8000..8999 run data merge entity @s {Item:{id:"minecraft:knowledge_book",components:{"minecraft:item_model":"minecraft:clock/4"}}}
execute if score $daytime acs.daytime matches 9000..9999 run data merge entity @s {Item:{id:"minecraft:knowledge_book",components:{"minecraft:item_model":"minecraft:clock/5"}}}
execute if score $daytime acs.daytime matches 10000..10999 run data merge entity @s {Item:{id:"minecraft:knowledge_book",components:{"minecraft:item_model":"minecraft:clock/6"}}}
execute if score $daytime acs.daytime matches 11000..11999 run data merge entity @s {Item:{id:"minecraft:knowledge_book",components:{"minecraft:item_model":"minecraft:clock/7"}}}
execute if score $daytime acs.daytime matches 12000..12999 run data merge entity @s {Item:{id:"minecraft:knowledge_book",components:{"minecraft:item_model":"minecraft:clock/8"}}}
execute if score $daytime acs.daytime matches 13000..13999 run data merge entity @s {Item:{id:"minecraft:knowledge_book",components:{"minecraft:item_model":"minecraft:clock/9"}}}
execute if score $daytime acs.daytime matches 14000..14999 run data merge entity @s {Item:{id:"minecraft:knowledge_book",components:{"minecraft:item_model":"minecraft:clock/10"}}}
execute if score $daytime acs.daytime matches 15000..15999 run data merge entity @s {Item:{id:"minecraft:knowledge_book",components:{"minecraft:item_model":"minecraft:clock/11"}}}
