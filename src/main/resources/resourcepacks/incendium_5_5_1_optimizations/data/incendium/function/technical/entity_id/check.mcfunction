# Assign an ID without ever emitting the invalid 32768 value.
# entity_id/reset repairs every existing owner before control returns here.

execute if score $current.id in.eid matches 32767.. run function incendium:technical/entity_id/reset
function incendium:technical/entity_id/init
