package dev.kikugie.ucsm.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import dev.kikugie.ucsm.UCSM
import dev.kikugie.ucsm.cannon.CannonInstance
import dev.kikugie.ucsm.mixin.DefaultPosArgumentAccessor
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.command.argument.BlockPosArgumentType
import net.minecraft.command.argument.DefaultPosArgument
import net.minecraft.text.Text
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import net.minecraft.world.RaycastContext
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.math.floor


object ModCommand {
    var precision = 10
    val NO_INSTANCE = SimpleCommandExceptionType(Text.of("Load an instance with `/ucsm load` first"))
    val NO_ORIGIN = SimpleCommandExceptionType(Text.of("Set the origin with `/ucsm origin` first"))

    fun register(dispatcher: CommandDispatcher<FabricClientCommandSource>, ignoredAccess: CommandRegistryAccess) {
        dispatcher.register(
            literal("ucsm")
                .executes(::help)
                .then(literal("help").executes(::help))
                .then(literal("reload").executes(::reload))
                .then(literal("unload").executes(::unload))
                .then(
                    literal("load").then(
                        argument("dir", DirectoryArgumentType(UCSM.CONFIG))
                            .executes(::load)
                    )
                )
                .then(
                    literal("precision").then(
                        argument(
                            "range",
                            IntegerArgumentType.integer(1, 9000)
                        ).executes(::tntRange)
                    )
                )
                .then(
                    literal("origin")
                        .executes { originFromPlayerPos(it, false) }
                        .then(
                            literal("mirrored")
                                .executes { originFromPlayerPos(it, true) })
                        .then(
                            argument("pos", BlockPosArgumentType.blockPos())
                                .then(
                                    argument("direction", DirectionArgumentType())
                                        .executes { originFromPos(it, false) }
                                        .then(
                                            literal("mirrored")
                                                .executes { originFromPos(it, true) })
                                )
                        )
                )
                .then(
                    literal("target")
                            .executes(::raycastTarget)
                            .then(
                                argument("pos", BlockPosArgumentType.blockPos())
                                    .executes(::posTarget)
                            )
                            .then(
                                argument("config", CannonConfigArgumentType())
                                    .executes(::configTarget)
                            )
                            .then(
                                literal("moving").then(
                                    argument("direction", DirectionArgumentType())
                                        .executes{ raycastMovingTarget(it, 10, 30) }
                                        .then(
                                            argument("pos", BlockPosArgumentType.blockPos())
                                                .executes{ posMovingTarget(it, 10, 30) }
                                                .then(
                                                    argument("speed", IntegerArgumentType.integer(1, 999))
                                                    .executes { posMovingTarget(it, IntegerArgumentType.getInteger(it, "speed"), 30) }
                                                    .then(
                                                        argument("delay", IntegerArgumentType.integer(1,120))
                                                            .executes { posMovingTarget(it, IntegerArgumentType.getInteger(it, "speed"), IntegerArgumentType.getInteger(it, "delay")) }
                                                    )
                                                )
                                        )
                                        .then(
                                            argument("speed", IntegerArgumentType.integer(1, 999))
                                                .executes { raycastMovingTarget(it, IntegerArgumentType.getInteger(it, "speed"), 30) }
                                                .then(
                                                    argument("delay", IntegerArgumentType.integer(1,120))
                                                        .executes { raycastMovingTarget(it, IntegerArgumentType.getInteger(it, "speed"), IntegerArgumentType.getInteger(it, "delay")) }
                                                )
                                        )
                                )
                            )
                )
        )
    }

    private fun unload(context: CommandContext<FabricClientCommandSource>): Int {
        checkInstance()
        UCSM.cannon!!.also {
            it.unload()
            UCSM.cannonCache.remove(it.file)
            context.source.sendFeedback(Text.of("§aUnloaded cannon ${it.file.name}"))
        }
        return 0
    }

    private fun configTarget(context: CommandContext<FabricClientCommandSource>): Int {
        checkInstance()
        val config = context.getArgument("config", String::class.java)
        val result = UCSM.cannon?.getExtra(config) ?: return -1
        context.source.sendFeedback(Text.of("§aConfiguration: $result"))
        return 0
    }

    private fun posTarget(context: CommandContext<FabricClientCommandSource>): Int =
        context.source.let {
            setTarget(
                getPosFromArgument(
                    context.getArgument("pos", DefaultPosArgument::class.java), it
                ), it
            )
        }

    private fun raycastTarget(context: CommandContext<FabricClientCommandSource>): Int {
        val source = context.source
        val player = source.player
        val result = player.world.raycast(
            RaycastContext(
                player.eyePos,
                player.eyePos.add(player.rotationVecClient.multiply(1000.0)),
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.ANY,
                player
            )
        )
        return if (result.type == HitResult.Type.MISS) {
            context.source.sendError(Text.of("You are looking into nowhere..."))
            -1
        } else setTarget(result.blockPos, source)
    }

    private fun setTarget(pos: BlockPos, source: FabricClientCommandSource): Int {
        checkInstance()
        checkCannon()
        val cannon = UCSM.cannon!!
        val origin = cannon.origin!!

        var target = Vec3d.of(pos)
            .subtract(
                origin.x - 0.5,
                origin.y - 0.5,
                origin.z - 0.5
            ) // Aim at the center of the block
            .rotateY(Math.toRadians((cannon.direction!!.asRotation() + 180).toDouble()).toFloat())
        if (cannon.mirrored!!) {
            target = Vec3d(-target.x, target.y, target.z)
        }

        val result = cannon.getCoord(target) ?: return -1
        return if (result.distance > precision) {
            source.sendError(Text.of("Your target is too far away!"))
            -1
        } else {
            source.sendFeedback(Text.of("§aConfiguration: ${result.payload}, distance: ${result.distance}"))
            1
        }
    }

    private fun posMovingTarget(context: CommandContext<FabricClientCommandSource>, speed: Int, delay: Int): Int {

        return setMovingTarget(getPosFromArgument(context.getArgument("pos", DefaultPosArgument::class.java), context.source), context, speed, delay)

    }

    private fun raycastMovingTarget(context: CommandContext<FabricClientCommandSource>, speed: Int, delay: Int): Int {
    
        val player = context.source.player
        val result = player.world.raycast(
            RaycastContext(
                player.eyePos,
                player.eyePos.add(player.rotationVecClient.multiply(1000.0)),
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.ANY,
                player
            )
        )

        return if (result.type == HitResult.Type.MISS) {
            context.source.sendError(Text.of("You are looking into nowhere..."))
            -1
        } else setMovingTarget(result.blockPos, context, speed, delay)
    }

    private fun setMovingTarget(pos: BlockPos, context: CommandContext<FabricClientCommandSource>, speed: Int, delay: Int): Int {
        checkInstance()
        checkCannon()
        val cannon = UCSM.cannon!!
        val origin = cannon.origin!!

        val blockdistance = 20 * delay.toDouble() / speed.toDouble() //speed is in ticks/block, delay is in seconds

        var travel = Vec3d(0.0, 0.0, 1.0) //"unit" vector that we will rotate using given direction. note that facing south (+z) has yaw of 0deg
            .rotateY(Math.toRadians(context.getArgument("direction", Direction::class.java).asRotation().toDouble()).toFloat() * -1)
            .multiply(blockdistance)

        var target = Vec3d.of(pos)
            .add(travel)
            .subtract(
                origin.x - 0.5,
                origin.y - 0.5,
                origin.z - 0.5
            ) // Aim at the center of the block
            .rotateY(Math.toRadians((cannon.direction!!.asRotation() + 180).toDouble()).toFloat())
        if (cannon.mirrored!!) {
            target = Vec3d(-target.x, target.y, target.z)
        }

        val result = cannon.getCoord(target) ?: return -1

        //this is super specific to m3's cannon, but the formula is basically:
        //fuse time = (79t for tnt fuse) + (5t for transmission between button->countdown) + (6t for redstone transmission after barrel is empty) + (parameter 2, in ticks)
        val fusetime = result.payload.substringAfter(",").substringBefore(",").toInt() + 90
        val launchtime = ( context.source.player.world.getTimeOfDay() + (20 * delay) - fusetime ) % 24000

        //convert gameday ticks to game-time HH:MM for easier timing (many mods can display this time)
        val lt_hr = floor(((launchtime / 1000) + 5).toDouble()) % 24 + 1
        val lt_min = floor( (launchtime % 1000) * 0.06)
        val lt_fmt = String.format("%02d", lt_hr.toInt()) + ":" + String.format("%02d", lt_min.toInt())
        
        return if (result.distance > precision) {
            context.source.sendError(Text.of("Target will not fall within range at detonation time..."))
            -1
        } else {
            context.source.sendFeedback(Text.of("§aConfiguration: ${result.payload}, distance: ${result.distance}"))
            context.source.sendFeedback(Text.of("§bLaunch time: ${launchtime} (current day ticks)- ${lt_fmt} (in-game time)"))
            1
        }
    }

    private fun originFromPos(context: CommandContext<FabricClientCommandSource>, mirrored: Boolean): Int =
        setOrigin(
            context,
            getPosFromArgument(
                context.getArgument("pos", DefaultPosArgument::class.java),
                context.source
            ),
            context.getArgument("direction", Direction::class.java),
            mirrored
        )

    private fun originFromPlayerPos(context: CommandContext<FabricClientCommandSource>, mirrored: Boolean): Int =
        context.source.player.let { setOrigin(context, it.blockPos, it.horizontalFacing, mirrored) }

    private fun setOrigin(
        context: CommandContext<FabricClientCommandSource>,
        pos: BlockPos,
        facing: Direction,
        mirrored: Boolean
    ): Int {
        checkInstance()
        UCSM.cannon!!.setProperties(pos, facing, mirrored)
        context.source.sendFeedback(Text.of("§oOrigin set to ${pos.toShortString()} facing ${facing.asString()}"))
        return 0
    }

    private fun tntRange(context: CommandContext<FabricClientCommandSource>): Int {
        precision = context.getArgument("range", Int::class.java)
        context.source.sendFeedback(Text.of("§oPrecision set to $precision"))
        return 0
    }

    private fun load(context: CommandContext<FabricClientCommandSource>): Int {
        val dir = context.getArgument("dir", Path::class.java)
        UCSM.cannon = CannonInstance.lazyLoad(dir)
        context.source.sendFeedback(Text.of("§oLoading cannon ${dir.name}"))
        return 0
    }

    private fun reload(context: CommandContext<FabricClientCommandSource>): Int {
        checkInstance()
        val dir = UCSM.cannon!!.file
        UCSM.cannon!!.reload()
        context.source.sendFeedback(Text.of("§oReloading cannon ${dir.name}"))
        return 0
    }

    private fun help(context: CommandContext<FabricClientCommandSource>): Int {
        context.source.sendFeedback(
            Text.of(
                """
            §oCommand functionality:
            - load <dir>: Load cannon config from .minecraft/config/ucsm/{dir}. Cannon origins are preserved when switching.
            - reload: Reload files for the current cannon.
            - unload: Unloads the current cannon entirely.
            - precision: Set the maximum valid distance from tnt to the target
            - origin [<pos> <direction>] [mirrored]: Set cannon origin to a location. If empty, uses player position and rotation.
            - target <conf>: If the active cannon has custom presets, finds the matching one. Doesn't require an origin to be set up.
            - target [<pos>]: Find the closest configuration to the given position. If empty, finds the block the player is looking at.
        """.trimIndent()
            )
        )
        return 0
    }

    private fun checkInstance() {
        if (UCSM.cannon == null) throw NO_INSTANCE.create()
    }

    private fun checkCannon() {
        if (UCSM.cannon!!.origin == null) throw NO_ORIGIN.create()
    }

    private fun getPosFromArgument(argument: DefaultPosArgument, source: FabricClientCommandSource): BlockPos {
        val accessor = argument as DefaultPosArgumentAccessor
        val pos = source.player.pos
        return BlockPos(
            accessor.getX().toAbsoluteCoordinate(pos.x).toInt(),
            accessor.getY().toAbsoluteCoordinate(pos.y).toInt(),
            accessor.getZ().toAbsoluteCoordinate(pos.z).toInt()
        )
    }
}