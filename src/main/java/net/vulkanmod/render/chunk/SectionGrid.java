package net.vulkanmod.render.chunk;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.vulkanmod.render.chunk.util.CircularIntList;
import net.vulkanmod.render.chunk.util.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

public class SectionGrid {

    private final WorldRenderer worldRenderer;
    protected final Level level;
    protected int gridHeight;
    protected int gridWidth;
    public RenderSection[] chunks;
    final ChunkAreaManager chunkAreaManager;

    private int prevSecX;
    private int prevSecZ;

    public SectionGrid(WorldRenderer worldRenderer, Level level, int viewDistance) {
        this.worldRenderer = worldRenderer;
        this.level = level;
        this.setViewDistance(viewDistance);
        this.createChunks();
        this.chunkAreaManager = new ChunkAreaManager(this.gridWidth, this.gridHeight, this.level.getMinBuildHeight());

        this.prevSecX = Integer.MIN_VALUE;
        this.prevSecZ = Integer.MIN_VALUE;
    }

    protected void createChunks() {
        if (!Minecraft.getInstance().isSameThread()) {
            throw new IllegalStateException("createChunks called from wrong thread: " + Thread.currentThread().getName());
        } else {
            int i = this.gridWidth * this.gridHeight * this.gridWidth;
            this.chunks = new RenderSection[i];

            for(int j = 0; j < this.gridWidth; ++j) {
                for(int k = 0; k < this.gridHeight; ++k) {
                    for(int l = 0; l < this.gridWidth; ++l) {
                        int i1 = this.getChunkIndex(j, k, l);
                        RenderSection renderSection = new RenderSection(i1, j * 16, k * 16, l * 16);
                        renderSection.setWorldRenderer(this.worldRenderer);
                        this.chunks[i1] = renderSection;
                    }
                }
            }
            this.setYNeighbours();
        }
    }

    public void releaseAllBuffers() {
        for(RenderSection section : this.chunks) {
            section.release();
        }
        this.chunkAreaManager.releaseAllBuffers();
    }

    private int getChunkIndex(int x, int y, int z) {
        return (z * this.gridHeight + y) * this.gridWidth + x;
    }

    protected void setViewDistance(int radius) {
        int i = radius * 2 + 1;
        this.gridWidth = i;
        this.gridHeight = this.level.getSectionsCount();
        this.gridWidth = i;
    }

    public void repositionCamera(double x, double z) {

        int i = Mth.floor(x);
        int j = Mth.floor(z);

        this.chunkAreaManager.repositionAreas(i, j);

        int deltaX = Mth.clamp((i >> 4) - this.prevSecX, -this.gridWidth, this.gridWidth);
        int deltaZ = Mth.clamp((j >> 4) - this.prevSecZ, - this.gridWidth, this.gridWidth);

        int xAbsChunkIndex = (i >> 4) - this.gridWidth / 2;
        int xStart = Math.floorMod(xAbsChunkIndex, this.gridWidth); // needs positive modulo
        int zAbsChunkIndex = (j >> 4) - this.gridWidth / 2;
        int zStart = Math.floorMod(zAbsChunkIndex, this.gridWidth);

        CircularIntList xList = new CircularIntList(this.gridWidth, xStart);
        CircularIntList zList = new CircularIntList(this.gridWidth, zStart);
        CircularIntList.OwnIterator xIterator = xList.iterator();
        CircularIntList.OwnIterator zIterator = zList.iterator();

        int xRangeStart;
        int xRangeEnd;
        int xComplStart;
        int xComplEnd;
        if(deltaX >= 0) {
            xRangeStart = this.gridWidth - deltaX;
            xRangeEnd = this.gridWidth - 1;
            xComplStart = 0;
            xComplEnd = xRangeStart - 1;
        } else {
            xRangeStart = 0;
            xRangeEnd = - deltaX - 1;
            xComplStart = xRangeEnd + 1;
            xComplEnd = this.gridWidth - 1;
        }

        int zRangeStart;
        int zRangeEnd;
        if(deltaZ >= 0) {
            zRangeStart = this.gridWidth - deltaZ;
            zRangeEnd = this.gridWidth - 1;
        } else {
            zRangeStart = 0;
            zRangeEnd = - deltaZ - 1;
        }

        CircularIntList.RangeIterator xRangeIterator = xList.rangeIterator(xRangeStart, xRangeEnd);
        CircularIntList.RangeIterator xComplIterator = xList.rangeIterator(xComplStart, xComplEnd);
        CircularIntList.RangeIterator zRangeIterator = zList.rangeIterator(zRangeStart, zRangeEnd);

        xAbsChunkIndex = (i >> 4) - this.gridWidth / 2 + xRangeStart;
        for(int xRelativeIndex; xRangeIterator.hasNext(); xAbsChunkIndex++) {
            xRelativeIndex = xRangeIterator.next();
            int x1 = (xAbsChunkIndex << 4);

            zIterator.restart();
            zAbsChunkIndex = (j >> 4) - (this.gridWidth >> 1);

            for(int zRelativeIndex; zIterator.hasNext(); zAbsChunkIndex++) {
                zRelativeIndex = zIterator.next();
                int z1 = (zAbsChunkIndex << 4);

                for (int yRel = 0;
                     yRel < this.gridHeight; ++yRel) {
                    int y1 = this.level.getMinBuildHeight() + (yRel << 4);
                    RenderSection renderSection = this.chunks[this.getChunkIndex(xRelativeIndex, yRel, zRelativeIndex)];

                    renderSection.setOrigin(x1, y1, z1);

                    this.unsetNeighbours(renderSection);

                    this.setNeighbours(renderSection, xList, zList, xRangeIterator.getCurrentIndex(), zIterator.getCurrentIndex(),
                            xRelativeIndex, yRel, zRelativeIndex);

                    this.setChunkArea(renderSection, x1, y1, z1);
                }
            }
        }

        xAbsChunkIndex = (i >> 4) - this.gridWidth / 2 + xComplStart;
        for(int xRelativeIndex; xComplIterator.hasNext(); xAbsChunkIndex++) {
            xRelativeIndex = xComplIterator.next();
            int x1 = (xAbsChunkIndex << 4);

            zRangeIterator.restart();
            zAbsChunkIndex = (j >> 4) - (this.gridWidth >> 1) + zRangeStart;

            for(int zRelativeIndex; zRangeIterator.hasNext(); zAbsChunkIndex++) {
                zRelativeIndex = zRangeIterator.next();
                int z1 = (zAbsChunkIndex << 4);

                for(int yRel = 0;
                    yRel < this.gridHeight; ++yRel) {
                    int y1 = this.level.getMinBuildHeight() + (yRel << 4);
                    RenderSection renderSection = this.chunks[this.getChunkIndex(xRelativeIndex, yRel, zRelativeIndex)];

                    renderSection.setOrigin(x1, y1, z1);

                    this.unsetNeighbours(renderSection);

                    this.setNeighbours(renderSection, xList, zList, xComplIterator.getCurrentIndex(), zRangeIterator.getCurrentIndex(),
                            xRelativeIndex, yRel, zRelativeIndex);

                    this.setChunkArea(renderSection, x1, y1, z1);

                }
            }
        }

        this.prevSecX = i >> 4;
        this.prevSecZ = j >> 4;
    }

    private void setNeighbours(RenderSection section, CircularIntList xList, CircularIntList zList,
                               int xIdx, int zIdx, int x, int y, int z) {

        int eastX = xList.getNext(xIdx);
        int westX = xList.getPrevious(xIdx);
        int northZ = zList.getPrevious(zIdx);
        int southZ = zList.getNext(zIdx);

        if(eastX != -1) {
            RenderSection neighbour = this.chunks[getChunkIndex(eastX, y, z)];
            section.setNeighbour(Direction.EAST.ordinal(), neighbour);
            neighbour.setNeighbour(Direction.WEST.ordinal(), section);
        }

        if(westX != -1) {
            RenderSection neighbour = this.chunks[getChunkIndex(westX, y, z)];
            section.setNeighbour(Direction.WEST.ordinal(), neighbour);
            neighbour.setNeighbour(Direction.EAST.ordinal(), section);
        }

        if(northZ != -1) {
            RenderSection neighbour = this.chunks[getChunkIndex(x, y, northZ)];
            section.setNeighbour(Direction.NORTH.ordinal(), neighbour);
            neighbour.setNeighbour(Direction.SOUTH.ordinal(), section);
        }

        if(southZ != -1) {
            RenderSection neighbour = this.chunks[getChunkIndex(x, y, southZ)];
            section.setNeighbour(Direction.SOUTH.ordinal(), neighbour);
            neighbour.setNeighbour(Direction.NORTH.ordinal(), section);
        }
    }

    private void unsetNeighbours(RenderSection section) {

        for(Direction dir : Util.XZ_DIRECTIONS) {
            RenderSection neighbour = section.getNeighbour(dir.ordinal());
            if(neighbour != null)
                neighbour.setNeighbour(dir.getOpposite().ordinal(), null);
        }

    }

    private void setYNeighbours() {
        for(int j = 0; j < this.gridWidth; ++j) {
            for(int k = 0; k < this.gridHeight; ++k) {
                for(int l = 0; l < this.gridWidth; ++l) {
                    int i1 = this.getChunkIndex(j, k, l);
                    this.setYNeighbours(this.chunks[i1], j, k, l);
                }
            }
        }
    }

    private void setYNeighbours(RenderSection section, int x, int y, int z) {
        if(y != this.gridHeight - 1) {
            RenderSection neighbour = this.chunks[getChunkIndex(x, y + 1, z)];
            section.setNeighbour(Direction.UP.ordinal(), neighbour);
            neighbour.setNeighbour(Direction.DOWN.ordinal(), section);
        }

        if(y != 0) {
            RenderSection neighbour = this.chunks[getChunkIndex(x , y - 1, z)];
            section.setNeighbour(Direction.DOWN.ordinal(), neighbour);
            neighbour.setNeighbour(Direction.UP.ordinal(), section);
        }
    }

    private void setChunkArea(RenderSection section, int x, int y, int z) {
        ChunkArea chunkArea = this.chunkAreaManager.getChunkArea(section, x, y, z);

        section.setChunkArea(chunkArea);
    }

    public void setDirty(int sectionX, int sectionY, int sectionZ, boolean p_110863_) {
        int i = Math.floorMod(sectionX, this.gridWidth);
        int j = Math.floorMod(sectionY - this.level.getMinSection(), this.gridHeight);
        int k = Math.floorMod(sectionZ, this.gridWidth);
        RenderSection renderSection = this.chunks[this.getChunkIndex(i, j, k)];
        renderSection.setDirty(p_110863_);
    }

    @Nullable
    public RenderSection getSectionAtBlockPos(BlockPos blockPos) {
        return this.getSectionAtBlockPos(blockPos.getX(), blockPos.getY(), blockPos.getZ());
    }

    public RenderSection getSectionAtBlockPos(int x, int y, int z) {
        int i = x >> 4;
        int j = (y - this.level.getMinBuildHeight()) >> 4;
        int k = z >> 4;

        return this.getSectionAtSectionPos(i, j, k);
    }

    public RenderSection getSectionAtSectionPos(int i, int j, int k) {
        if (j >= 0 && j < this.gridHeight) {
            i = Math.floorMod(i, this.gridWidth);
            k = Math.floorMod(k, this.gridWidth);
            return this.chunks[this.getChunkIndex(i, j, k)];
        } else {
            return null;
        }
    }

    public List<RenderSection> getRenderSectionsAt(int x, int z) {
        ObjectArrayList<RenderSection> list = new ObjectArrayList<>(24);

        int i = Math.floorMod(x, this.gridWidth);
        int k = Math.floorMod(z, this.gridWidth);

        for(int y1 = 0; y1 < gridHeight; ++y1) {
            list.add(this.chunks[this.getChunkIndex(i, y1, k)]);
        }

        return list;
    }

    public void updateFrustumVisibility(VFrustum frustum) {
        this.chunkAreaManager.updateFrustumVisibility(frustum);
    }
}
