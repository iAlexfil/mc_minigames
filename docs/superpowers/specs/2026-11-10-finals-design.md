# The Finals — simplified Cashout

Add a standalone `finals` game to the existing Paper plugin, exposed by the common menu, `/play finals`, and `/finals` commands. This is a compact Minecraft adaptation, using the existing Java 25/Paper 26.3 infrastructure and English/Russian localization.

The implementation choice is two balanced red/blue teams, 2–12 players, a generated disposable urban arena, Minecraft weapons and destructible cover. A central vault opens after five seconds. Carry its cashbox to either of two stations, defend a forty-second cashout, or steal ownership by sneaking at the station uninterrupted for five seconds. Damage, death, distance and disconnect cancel stealing. Each completed cashout pays its current owner 10,000; 20,000 wins. At six minutes the higher cash total wins, with exact ties drawn. Respawn after five seconds with two seconds of protection; protection cannot be used to attack or interact with objectives. Solo test games award no persistent points.

Alternatives considered: embedding in Party would constrain the match to round scoring; reusing Warfare's persistent map requires setup. A standalone disposable arena fits the full objective loop and contains destruction. Full firearms, original maps, class selection and structural collapse are outside this simplified version.

Cashbox possession is authoritative match state, not a freely movable inventory item. A displayed ground item represents available cash and is recreated if lost; the carried indicator cannot be stashed, duplicated or placed. Quit/death/drop release it; returning players retain teams. Withdrawal is permanent for this match. Session-local tasks stop on end; core restores player snapshots and deletes the arena.

The pure state model owns payouts, deadlines and victory. The Paper session owns proximity, player actions, combat, respawning and presentation. The arena owns geometry and the exact set of destructible blocks. All built-in text uses paired translation catalogs.
