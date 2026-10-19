# Lampas2 override of Builder's Boundry 0.2 acs/main.
# Upstream runs 26 untyped @e[tag=acs.*] selectors every tick, each a full walk of the loaded entity list.
# This runs one @e[tag=acs.acc.block] walk every 4 ticks and dispatches per furniture entity.
# Light re-placement, clock faces, cutting-board rotation and support checks tolerate a 4-tick delay.
scoreboard players add $lampas.acs_clock acs.facing 1
execute if score $lampas.acs_clock acs.facing matches 4.. run function diesse:acs/lampas/slow
