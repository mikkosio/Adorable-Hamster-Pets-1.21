# Adorable Hamster Pets - Crafted Guide Book Effects
# Plays when the player crafts the Hamster Guide Book.

# Sound effect (optional, could be a gentle "magic" sound or page turning)
playsound minecraft:block.enchantment_table.use player @s ~ ~ ~ 0.5 1.2
playsound minecraft:item.book.page_turn player @s ~ ~ ~ 0.7 1.5

# Particle effect 1: A burst of enchantment-like particles
particle minecraft:enchant ~ ~1 ~ 0.3 0.5 0.3 0.05 50 force @s

# Particle effect 2: Some happy villager particles (green sparkles)
particle minecraft:happy_villager ~ ~1 ~ 0.5 0.5 0.5 0.02 20 force @s

# Particle effect 3: A few falling spore blossom particles for a gentle, magical feel
particle minecraft:falling_spore_blossom ~ ~1.5 ~ 0.2 0.3 0.2 0.01 10 force @s

# Action bar message:
title @s actionbar {"text":"A wealth of hamster knowledge, rediscovered.","color":"gold"}