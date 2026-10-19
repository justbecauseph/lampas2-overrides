# As one Builder's Boundry furniture item frame, at its position.
# Support checks (upstream acs/main). delete/all kills @s, so return instead of acting on a removed entity.
execute if entity @s[tag=acs.face.top] if block ~ ~-1 ~ air run return run function diesse:acs/delete/all
execute if entity @s[tag=acs.face.side] if block ^ ^ ^-0.5 air run return run function diesse:acs/delete/all
# Facing checks (upstream acs/face/all). face/top and face/side give the item back and kill @s.
execute if entity @s[tag=acs.face.top,nbt=!{Facing:1b}] run return run function diesse:acs/face/top
execute if entity @s[tag=acs.face.side] store result score @s acs.facing run data get entity @s Facing
execute if entity @s[tag=acs.face.side] unless score @s acs.facing matches 2..5 run return run function diesse:acs/face/side
# Actions (upstream acs/actions/all), same tags in the same order.
execute if entity @s[tag=acs.tall_lamp] run function diesse:acs/actions/tall_lamp
execute if entity @s[tag=acs.candlestick] run function diesse:acs/actions/candlestick
execute if entity @s[tag=acs.gold_candlestick] run function diesse:acs/actions/candlestick
execute if entity @s[tag=acs.black_lamp] run function diesse:acs/actions/lamp
execute if entity @s[tag=acs.light_gray_lamp] run function diesse:acs/actions/lamp
execute if entity @s[tag=acs.gray_lamp] run function diesse:acs/actions/lamp
execute if entity @s[tag=acs.blue_lamp] run function diesse:acs/actions/lamp
execute if entity @s[tag=acs.light_blue_lamp] run function diesse:acs/actions/lamp
execute if entity @s[tag=acs.yellow_lamp] run function diesse:acs/actions/lamp
execute if entity @s[tag=acs.pink_lamp] run function diesse:acs/actions/lamp
execute if entity @s[tag=acs.purple_lamp] run function diesse:acs/actions/lamp
execute if entity @s[tag=acs.white_lamp] run function diesse:acs/actions/lamp
execute if entity @s[tag=acs.brown_lamp] run function diesse:acs/actions/lamp
execute if entity @s[tag=acs.lime_lamp] run function diesse:acs/actions/lamp
execute if entity @s[tag=acs.green_lamp] run function diesse:acs/actions/lamp
execute if entity @s[tag=acs.magenta_lamp] run function diesse:acs/actions/lamp
execute if entity @s[tag=acs.cyan_lamp] run function diesse:acs/actions/lamp
execute if entity @s[tag=acs.orange_lamp] run function diesse:acs/actions/lamp
execute if entity @s[tag=acs.red_lamp] run function diesse:acs/actions/lamp
execute if entity @s[tag=acs.clock] run function diesse:acs/actions/clock/all
execute if entity @s[tag=acs.cutting_board,nbt={ItemRotation:1b}] run function diesse:acs/actions/cutting_board/used
