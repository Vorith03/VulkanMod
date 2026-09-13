package net.vulkanmod.render.chunk.voxel;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.vulkanmod.Initializer;
import net.vulkanmod.render.vertex.VertexUtil;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

/** CPU-only oracle for the canonical full-face subset; no production meshing path calls this. */
final class CanonicalCubeLightingSmokeTest {
    private static final BlockPos ORIGIN = new BlockPos(0, 64, 0);
    private static final BlockState SOURCE_STATE = Blocks.STONE.defaultBlockState();

    private CanonicalCubeLightingSmokeTest() {}

    static void verify() {
        BlockState qualifiedState = firstQualifiedState();
        require(GpuTerrainModelRegistry.isFullCubeGeometry(qualifiedState, false),
                "Vanilla lighting mode must retain a known qualified cube");
        require(!GpuTerrainModelRegistry.isFullCubeGeometry(qualifiedState, true),
                "Forge experimental lighting must fail GPU cube qualification closed");

        ReflectedAmbientOcclusion renderer = new ReflectedAmbientOcclusion();
        int comparisons = 0;
        for(Direction face : Direction.values()) {
            LightingLevel open = new LightingLevel();
            compare(renderer.calculate(open, face), calculateReference(open, face), face, "open");
            require(open.maxSampleDistance() == 2,
                    "Canonical AO must prove its two-block sample radius");
            comparisons += 4;

            int[][] blockedPairs = { { 0, 2 }, { 0, 3 }, { 1, 2 }, { 1, 3 } };
            for(int[] pair : blockedPairs) {
                LightingLevel mixed = new LightingLevel();
                Direction[] tangents = tangents(face);
                BlockPos faceCenter = ORIGIN.relative(face);
                for(int blocker : pair) {
                    mixed.setState(faceCenter.relative(tangents[blocker]).relative(face),
                            Blocks.STONE.defaultBlockState());
                }
                String fixture = "blocked-" + pair[0] + pair[1];
                compare(renderer.calculate(mixed, face), calculateReference(mixed, face),
                        face, fixture);
                require(mixed.maxSampleDistance() == 2,
                        "Mixed canonical AO must remain inside the proven sample radius");
                comparisons += 4;
            }
        }

        ModelBlockRenderer.clearCache();
        Initializer.LOGGER.info(
                "VULKANMOD_GPU_TERRAIN_LIGHTING_ORACLE_OK: {} exact packed color/light vertex comparisons across six open and mixed-occluder canonical faces; two-block sample radius; Forge experimental lighting fail-closed",
                comparisons);
    }

    private static FaceResult calculateReference(LightingLevel level, Direction face) {
        Direction[] tangent = tangents(face);
        BlockPos faceCenter = ORIGIN.relative(face);
        float[] sideBrightness = new float[4];
        int[] sideLight = new int[4];
        boolean[] open = new boolean[4];
        for(int i = 0; i < 4; ++i) {
            BlockPos side = faceCenter.relative(tangent[i]);
            sideBrightness[i] = level.shadeBrightness(side);
            sideLight[i] = level.lightColor(side);
            open[i] = level.lightPasses(side.relative(face));
        }

        Sample diagonal02 = diagonal(level, faceCenter, tangent, sideBrightness, sideLight,
                open, 0, 2);
        Sample diagonal03 = diagonal(level, faceCenter, tangent, sideBrightness, sideLight,
                open, 0, 3);
        Sample diagonal12 = diagonal(level, faceCenter, tangent, sideBrightness, sideLight,
                open, 1, 2);
        Sample diagonal13 = diagonal(level, faceCenter, tangent, sideBrightness, sideLight,
                open, 1, 3);
        float centerBrightness = level.shadeBrightness(faceCenter);
        int centerLight = level.lightColor(faceCenter);

        float[] cornerBrightness = {
                average(sideBrightness[3], sideBrightness[0], diagonal03.brightness, centerBrightness),
                average(sideBrightness[2], sideBrightness[0], diagonal02.brightness, centerBrightness),
                average(sideBrightness[2], sideBrightness[1], diagonal12.brightness, centerBrightness),
                average(sideBrightness[3], sideBrightness[1], diagonal13.brightness, centerBrightness)
        };
        int[] cornerLight = {
                blend(sideLight[3], sideLight[0], diagonal03.light, centerLight),
                blend(sideLight[2], sideLight[0], diagonal02.light, centerLight),
                blend(sideLight[2], sideLight[1], diagonal12.light, centerLight),
                blend(sideLight[3], sideLight[1], diagonal13.light, centerLight)
        };

        int[] colors = new int[4];
        int[] lights = new int[4];
        int[] remap = vertexRemap(face);
        float shade = level.getShade(face, true);
        for(int corner = 0; corner < 4; ++corner) {
            int vertex = remap[corner];
            float value = cornerBrightness[corner] * shade;
            colors[vertex] = VertexUtil.packColor(value, value, value, 1.0f);
            lights[vertex] = cornerLight[corner];
        }
        return new FaceResult(colors, lights);
    }

    private static Sample diagonal(LightingLevel level, BlockPos faceCenter,
                                   Direction[] tangent, float[] sideBrightness,
                                   int[] sideLight, boolean[] open, int first, int second) {
        // Vanilla deliberately substitutes the first tangent sample for every
        // blocked diagonal, including the two diagonals rooted at tangent 1.
        if(!open[first] && !open[second])
            return new Sample(sideBrightness[0], sideLight[0]);
        BlockPos diagonal = faceCenter.relative(tangent[first]).relative(tangent[second]);
        return new Sample(level.shadeBrightness(diagonal), level.lightColor(diagonal));
    }

    private static float average(float a, float b, float c, float d) {
        return (a + b + c + d) * 0.25f;
    }

    private static int blend(int a, int b, int c, int center) {
        if(a == 0) a = center;
        if(b == 0) b = center;
        if(c == 0) c = center;
        return (a + b + c + center >> 2) & 0x00ff00ff;
    }

    private static Direction[] tangents(Direction face) {
        return switch(face) {
            case DOWN -> new Direction[] { Direction.WEST, Direction.EAST,
                    Direction.NORTH, Direction.SOUTH };
            case UP -> new Direction[] { Direction.EAST, Direction.WEST,
                    Direction.NORTH, Direction.SOUTH };
            case NORTH -> new Direction[] { Direction.UP, Direction.DOWN,
                    Direction.EAST, Direction.WEST };
            case SOUTH -> new Direction[] { Direction.WEST, Direction.EAST,
                    Direction.DOWN, Direction.UP };
            case WEST -> new Direction[] { Direction.UP, Direction.DOWN,
                    Direction.NORTH, Direction.SOUTH };
            case EAST -> new Direction[] { Direction.DOWN, Direction.UP,
                    Direction.NORTH, Direction.SOUTH };
        };
    }

    /** Values are the target vertex for canonical corner 0, 1, 2 and 3. */
    private static int[] vertexRemap(Direction face) {
        return switch(face) {
            case DOWN, SOUTH -> new int[] { 0, 1, 2, 3 };
            case UP -> new int[] { 2, 3, 0, 1 };
            case NORTH, WEST -> new int[] { 3, 0, 1, 2 };
            case EAST -> new int[] { 1, 2, 3, 0 };
        };
    }

    private static BlockState firstQualifiedState() {
        for(Block block : BuiltInRegistries.BLOCK) {
            for(BlockState state : block.getStateDefinition().getPossibleStates()) {
                if(GpuTerrainModelRegistry.getFullCubeTemplate(state) != null)
                    return state;
            }
        }
        throw new AssertionError("Startup lighting oracle requires a qualified cube state");
    }

    private static void compare(FaceResult actual, FaceResult expected,
                                Direction face, String fixture) {
        for(int vertex = 0; vertex < 4; ++vertex) {
            if(actual.colors[vertex] != expected.colors[vertex]
                    || actual.lights[vertex] != expected.lights[vertex]) {
                throw new AssertionError("Canonical lighting mismatch for " + fixture + " "
                        + face + " vertex " + vertex
                        + ": expected color/light=" + expected.colors[vertex] + "/"
                        + expected.lights[vertex] + " actual=" + actual.colors[vertex] + "/"
                        + actual.lights[vertex]);
            }
        }
    }

    private static final class ReflectedAmbientOcclusion {
        private final Constructor<?> constructor;
        private final Method calculate;
        private final Field brightness;
        private final Field lightmap;

        private ReflectedAmbientOcclusion() {
            try {
                Class<?> type = findType();
                constructor = type.getDeclaredConstructor();
                constructor.setAccessible(true);
                calculate = findCalculate(type);
                calculate.setAccessible(true);
                brightness = findArrayField(type, float[].class);
                lightmap = findArrayField(type, int[].class);
                brightness.setAccessible(true);
                lightmap.setAccessible(true);
            } catch(ReflectiveOperationException error) {
                throw new IllegalStateException("Cannot bind Minecraft ambient-occlusion oracle", error);
            }
        }

        private FaceResult calculate(LightingLevel level, Direction face) {
            try {
                ModelBlockRenderer.clearCache();
                level.resetSampleDistance();
                Object oracle = constructor.newInstance();
                BitSet shapeFlags = new BitSet(3);
                shapeFlags.set(0); // Canonical unit face lies on the block boundary.
                calculate.invoke(oracle, level, SOURCE_STATE, ORIGIN, face,
                        new float[Direction.values().length * 2], shapeFlags, true);
                float[] rendererBrightness = (float[]) brightness.get(oracle);
                int[] rendererLight = (int[]) lightmap.get(oracle);
                int[] colors = new int[4];
                int[] lights = new int[4];
                for(int vertex = 0; vertex < 4; ++vertex) {
                    float value = rendererBrightness[vertex];
                    colors[vertex] = VertexUtil.packColor(value, value, value, 1.0f);
                    lights[vertex] = rendererLight[vertex];
                }
                return new FaceResult(colors, lights);
            } catch(InstantiationException | IllegalAccessException | InvocationTargetException error) {
                Throwable cause = error instanceof InvocationTargetException invocation
                        ? invocation.getCause() : error;
                throw new IllegalStateException("Minecraft ambient-occlusion oracle failed", cause);
            }
        }

        private static Class<?> findType() {
            for(Class<?> type : ModelBlockRenderer.class.getDeclaredClasses()) {
                try {
                    findCalculate(type);
                    findArrayField(type, float[].class);
                    findArrayField(type, int[].class);
                    return type;
                } catch(NoSuchMethodException | NoSuchFieldException ignored) {
                }
            }
            throw new IllegalStateException("Minecraft ambient-occlusion implementation not found");
        }

        private static Method findCalculate(Class<?> type) throws NoSuchMethodException {
            Class<?>[] signature = { BlockAndTintGetter.class, BlockState.class,
                    BlockPos.class, Direction.class, float[].class, BitSet.class,
                    boolean.class };
            for(Method method : type.getDeclaredMethods()) {
                if(Arrays.equals(method.getParameterTypes(), signature))
                    return method;
            }
            throw new NoSuchMethodException(type.getName());
        }

        private static Field findArrayField(Class<?> type, Class<?> fieldType)
                throws NoSuchFieldException {
            for(Field field : type.getDeclaredFields()) {
                if(field.getType() == fieldType)
                    return field;
            }
            throw new NoSuchFieldException(type.getName() + " " + fieldType.getName());
        }
    }

    private static final class LightingLevel implements BlockAndTintGetter {
        private final Map<BlockPos, BlockState> states = new HashMap<>();
        private int maxSampleDistance;

        private LightingLevel() {
            states.put(ORIGIN, SOURCE_STATE);
        }

        private void setState(BlockPos pos, BlockState state) {
            states.put(pos.immutable(), state);
        }

        private void resetSampleDistance() {
            maxSampleDistance = 0;
        }

        private int maxSampleDistance() {
            return maxSampleDistance;
        }

        private void record(BlockPos pos) {
            maxSampleDistance = Math.max(maxSampleDistance,
                    Math.max(Math.abs(pos.getX() - ORIGIN.getX()),
                            Math.max(Math.abs(pos.getY() - ORIGIN.getY()),
                                    Math.abs(pos.getZ() - ORIGIN.getZ()))));
        }

        private float shadeBrightness(BlockPos pos) {
            BlockState state = getBlockState(pos);
            return state.getShadeBrightness(this, pos);
        }

        private int lightColor(BlockPos pos) {
            return LevelRenderer.getLightColor(this, getBlockState(pos), pos);
        }

        private boolean lightPasses(BlockPos pos) {
            BlockState state = getBlockState(pos);
            return !state.isViewBlocking(this, pos) || state.getLightBlock(this, pos) == 0;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            record(pos);
            return states.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public float getShade(Direction direction, boolean shade) {
            if(!shade)
                return 1.0f;
            return switch(direction) {
                case DOWN -> 0.53f;
                case UP -> 0.97f;
                case NORTH -> 0.79f;
                case SOUTH -> 0.83f;
                case WEST -> 0.61f;
                case EAST -> 0.67f;
            };
        }

        @Override
        public LevelLightEngine getLightEngine() {
            throw new UnsupportedOperationException("Fixture overrides direct brightness lookup");
        }

        @Override
        public int getBrightness(LightLayer layer, BlockPos pos) {
            record(pos);
            int value = pos.getX() * 3 + pos.getY() * 5 + pos.getZ() * 7;
            return Math.floorMod(value + (layer == LightLayer.SKY ? 11 : 3), 16);
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver resolver) {
            return 0xffffff;
        }

        @Override
        public int getHeight() {
            return 384;
        }

        @Override
        public int getMinBuildHeight() {
            return -64;
        }
    }

    private record Sample(float brightness, int light) {}
    private record FaceResult(int[] colors, int[] lights) {}

    private static void require(boolean condition, String message) {
        if(!condition)
            throw new AssertionError(message);
    }
}
