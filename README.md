# This mod helps player set up and target [m³'s cannon](https://youtu.be/187oOzMrqRM).

## Setup
- Launch Minecraft with mod installed.
- Open `.minecraft/config/ucsm`.
- Put `Pt.txt` and `Ct.txt` in that folder.

## Usage
- `/ucsm reload`: reload config files.
- `/ucsm precision <int>`: maximum distance to the explosion.
- `/ucsm origin [<pos> <direction>] [mirrored]`: set cannon origin to a location, uses player position and facing direction if no arguments provided.
- `/ucsm target [<pos>]`: output closest configuration to specified position. Uses block player is looking at (even very far) if no argument is provided.
- `/ucsm target moving <direction> [<pos>] [<speed>] [<delay>]`: output closest configuration and launch time to a specified moving target. If no position argument is specified, the block that the player is looking at (even very far) is selected. Note that you do not need to predict the future position of a moving target. The `[<speed>]` argument is the speed of the target in ticks per block, which defaults to 10 ticks/block if not specified (accurate for many basic flying machines). The `[<delay>]` argument specifies the time in seconds between the player running the command and the payload exploding at the predicted target, which defaults to 30 seconds if not specified. 
- `/ucsm pack`: pack Ct.txt and Pt.txt into a binary format to reduce file size.

## Notes
- It's tricky to be fast enough to hit a flying machine. I recommend using mouse tweaks to scroll items in/out of the barrels quickly. Or find yourself a small team. Also, make sure you preload your payload tnt & even some propellant to save time. It will often take a couple tries to successfuly target a flying machine using your cursor. Zoom mods or the f3 cursor can help to more accurately target a far-away flying machine.
- This mod currently has hard-coded timing for m3's cannon, where the fuse time is known to be (90t + 2nd configuration parameter). It should mostly work for other cannons, but be prepared to account for slight inaccuracies in timing.
- Nearly every flying machine operates at 10 ticks/block or slower. To go faster, machines need to use zero-ticking/budded pistons or some other clever method, which will usually be obvious when you see it. Complex flying machines may be slower.
- The tnt fuse time and cannon redstone delay is included in the `[<delay>]` argument, so you will have approximately 25 seconds, rather than 30 (by default) to program and activate the cannon.
