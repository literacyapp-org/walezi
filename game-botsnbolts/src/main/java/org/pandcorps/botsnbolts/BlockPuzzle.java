/*
Copyright (c) 2009-2016, Andrew M. Martin
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following
conditions are met:

 * Redistributions of source code must retain the above copyright notice, this list of conditions and the following
   disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
   disclaimer in the documentation and/or other materials provided with the distribution.
 * Neither the name of Pandam nor the names of its contributors may be used to endorse or promote products derived from this
   software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
POSSIBILITY OF SUCH DAMAGE.
*/
package org.pandcorps.botsnbolts;

import java.util.*;

import org.pandcorps.botsnbolts.Enemy.*;
import org.pandcorps.botsnbolts.Player.*;
import org.pandcorps.botsnbolts.ShootableDoor.*;
import org.pandcorps.pandam.*;
import org.pandcorps.pandam.event.*;
import org.pandcorps.pandax.tile.*;

public abstract class BlockPuzzle {
    protected final TileMap tm;
    protected final Panmage[] blockImgs;
    
    protected BlockPuzzle(final Panmage[] blockImgs) {
        tm = BotsnBoltsGame.tm; // Remember original TileMap even after room change
        this.blockImgs = blockImgs;
    }
    
    protected final void fade(final int[] indicesToFadeOut, final int[] indicesToFadeIn) {
        fade(indicesToFadeOut, indicesToFadeIn, 1);
    }
    
    protected final void fade(final int[] indicesToFadeOut, final int[] indicesToFadeIn, final int step) {
        if (tm != BotsnBoltsGame.tm) {
            return;
        }
        final int numImgs = blockImgs.length;
        setTiles(indicesToFadeOut, step, true);
        setTiles(indicesToFadeIn, numImgs - step, false);
        if (step < numImgs) {
            Pangine.getEngine().addTimer(tm, 1, new TimerListener() {
                @Override public final void onTimer(final TimerEvent event) {
                    fade(indicesToFadeOut, indicesToFadeIn, step + 1);
                }});
        } else {
            onFadeEnd();
        }
    }
    
    //@OverrideMe
    protected void onFadeEnd() {
    }
    
    private final void setTiles(final int[] tileIndices, final int imgIndex, final boolean fadingOut) {
        if (tileIndices == null) {
            return;
        }
        final Panmage img;
        final byte b;
        if (imgIndex < blockImgs.length) {
            img = blockImgs[imgIndex];
            b = BotsnBoltsGame.TILE_FLOOR;
        } else {
            img = null;
            b = Tile.BEHAVIOR_OPEN;
        }
        for (final int index : tileIndices) {
            if (fadingOut && (Tile.getBehavior(tm.getTile(index)) == Tile.BEHAVIOR_OPEN)) {
                continue;
            }
            tm.setForeground(index, img, b);
        }
    }
    
    protected final static class TimedBlockPuzzle extends BlockPuzzle {
        private final List<int[]> steps;
        private int currentStepIndex = 0;
        
        protected TimedBlockPuzzle(final List<int[]> steps) {
            super(BotsnBoltsGame.blockTimed);
            this.steps = steps;
            schedule();
        }
        
        private final void schedule() {
            Pangine.getEngine().addTimer(tm, 30, new TimerListener() {
                @Override public final void onTimer(final TimerEvent event) {
                    fade();
                }});
        }
        
        private final void fade() {
            final int numSteps = steps.size();
            int previousStepIndex = currentStepIndex - 2;
            if (previousStepIndex < 0) {
                previousStepIndex += numSteps;
            }
            fade(steps.get(previousStepIndex), steps.get(currentStepIndex));
            currentStepIndex++;
            if (currentStepIndex >= numSteps) {
                currentStepIndex = 0;
            }
            schedule();
        }
    }
    
    protected abstract static class BinaryBlockPuzzle extends BlockPuzzle {
        protected int[] enabledIndices;
        protected int[] disabledIndices;
        
        protected BinaryBlockPuzzle(final Panmage[] blockImgs, final int[] initiallyEnabledIndices, final int[] initiallyDisabledIndices) {
            super(blockImgs);
            enabledIndices = initiallyEnabledIndices;
            disabledIndices = initiallyDisabledIndices;
            init();
            fade(null, enabledIndices);
        }
        
        //@OverrideMe
        protected void init() {
        }
        
        protected final void fade() {
            onfadeStart();
            fade(enabledIndices, disabledIndices);
            final int[] tmpIndices = enabledIndices;
            enabledIndices = disabledIndices;
            disabledIndices = tmpIndices;
        }
        
        //@OverrideMe
        protected void onfadeStart() {
        }
    }
    
    protected final static class ShootableBlockPuzzle extends BinaryBlockPuzzle {
        private List<ShootableBlock> blocks = null;
        
        protected ShootableBlockPuzzle(final int[] initiallyEnabledIndices, final int[] initiallyDisabledIndices) {
            super(BotsnBoltsGame.blockCyan, initiallyEnabledIndices, initiallyDisabledIndices);
        }
        
        @Override
        protected final void init() {
            blocks = new ArrayList<ShootableBlock>(Math.max(enabledIndices.length, disabledIndices.length));
        }
        
        @Override
        protected final void onfadeStart() {
            Panctor.destroy(blocks);
        }
        
        @Override
        protected final void onFadeEnd() {
            for (final int index : enabledIndices) {
                blocks.add(new ShootableBlock(this, index));
            }
        }
    }
    
    protected final static class ShootableBlock extends Panctor implements CollisionListener {
        private final ShootableBlockPuzzle puzzle;
        
        protected ShootableBlock(final ShootableBlockPuzzle puzzle, final int index) {
            this.puzzle = puzzle;
            final Panple pos = getPosition();
            puzzle.tm.savePosition(pos, index);
            puzzle.tm.getLayer().addActor(this);
            setVisible(false);
            setView(puzzle.blockImgs[0]);
        }

        @Override
        public final void onCollision(final CollisionEvent event) {
            final Collidable collider = event.getCollider();
            if (collider instanceof Projectile) {
                puzzle.fade();
                ((Projectile) collider).burst();
                collider.destroy();
            }
        }
    }
    
    protected final static class ButtonBlockPuzzle extends BinaryBlockPuzzle {
        protected ButtonBlockPuzzle(final int[] initiallyEnabledIndices, final int[] initiallyDisabledIndices) {
            super(null, initiallyEnabledIndices, initiallyDisabledIndices); //TODO blockImgs
        }
    }
    
    protected final static class BlockShootableButtonHandler implements ShootableButtonHandler {
        private final ButtonBlockPuzzle puzzle;
        
        protected BlockShootableButtonHandler(final ButtonBlockPuzzle puzzle) {
            this.puzzle = puzzle;
        }
        
        @Override
        public final void onShootButton() {
            puzzle.fade();
        }
    }
    
    // Blocks fade in when Player approaches; fade out when Player leaves
    protected final static class HiddenBlockPuzzle extends Panctor implements StepListener {
        private final Map<Integer, Integer> indices;
        private final Set<Integer> activeIndices = new HashSet<Integer>();
        
        protected HiddenBlockPuzzle(final int[] indices) {
            this.indices = new HashMap<Integer, Integer>(indices.length);
            final TileMap tm = BotsnBoltsGame.tm;
            for (final int index : indices) {
                this.indices.put(Integer.valueOf(tm.getColumn(index)), Integer.valueOf(index));
                tm.setBehavior(index, Tile.BEHAVIOR_SOLID);
            }
            tm.getLayer().addActor(this);
        }

        @Override
        public final void onStep(final StepEvent event) {
            final Player player = PlayerContext.getPlayer(BotsnBoltsGame.pc);
            if (player == null) {
                return;
            }
            final Panmage[] blockImgs = BotsnBoltsGame.blockCyan; //TODO different color
            final TileMap tm = BotsnBoltsGame.tm;
            final int col = tm.getContainerColumn(player.getPosition().getX());
            for (final Integer activeIndex : activeIndices) {
                tm.setForeground(activeIndex.intValue(), null);
            }
            activeIndices.clear();
            for (int i = 0; i < 4; i++) {
                for (int j = ((i == 0) ? 0 : 1); j < 2; j++) {
                    final int mult = (j == 0) ? 1 : -1;
                    final Integer tileIndex = indices.get(Integer.valueOf(col + (mult * i)));
                    if (tileIndex == null) {
                        continue;
                    }
                    tm.setForeground(tileIndex.intValue(), blockImgs[i]);
                    activeIndices.add(tileIndex);
                }
            }
        }
    }
    
    protected final static class SpikeBlockPuzzle extends Panctor implements StepListener {
        private final static int vel = 3;
        private final static int timeAdd = (15 / vel) + 1;
        private final static int timeWait = timeAdd + 30;
        private final static int timeSub = timeWait + (15 / vel);
        private SpikeBlock[] verticalBlocks;
        private SpikeBlock[] horizontalBlocks;
        private int timer = 0;
        
        protected SpikeBlockPuzzle(final int[] initiallyVerticalIndices, final int[] initiallyHorizontalIndices) {
            verticalBlocks = setTiles(initiallyVerticalIndices, 1);
            horizontalBlocks = setTiles(initiallyHorizontalIndices, 0);
            BotsnBoltsGame.tm.getLayer().addActor(this);
        }
        
        private final SpikeBlock[] setTiles(final int[] tileIndices, final int baseRot) {
            final int size = tileIndices.length;
            final SpikeBlock[] blocks = new SpikeBlock[size];
            for (int i = 0; i < size; i++) {
                blocks[i] = new SpikeBlock(tileIndices[i], baseRot);
            }
            return blocks;
        }

        @Override
        public final void onStep(final StepEvent event) {
            timer++;
            if (timer < timeAdd) {
                moveSpikes(verticalBlocks, 0, vel);
                moveSpikes(horizontalBlocks, vel, 0);
            } else if (timer < timeWait) {
                // Do nothing; just keep the Spikes out
            } else if (timer < timeSub) {
                moveSpikes(verticalBlocks, 0, -vel);
                moveSpikes(horizontalBlocks, -vel, 0);
            } else {
                rotateSpikes(verticalBlocks, -1);
                rotateSpikes(horizontalBlocks, 1);
                final SpikeBlock[] tmp = verticalBlocks;
                verticalBlocks = horizontalBlocks;
                horizontalBlocks = tmp;
                timer = 0;
            }
        }
        
        private final void moveSpikes(final SpikeBlock[] blocks, final int x, final int y) {
            for (final SpikeBlock block : blocks) {
                block.moveSpikes(x, y);
            }
        }
        
        private final void rotateSpikes(final SpikeBlock[] blocks, final int amtRot) {
            for (final SpikeBlock block : blocks) {
                block.rotateSpikes(amtRot);
            }
        }
    }
    
    protected final static class SpikeBlock {
        private final Spike positiveSpike;
        private final Spike negativeSpike;
        
        protected SpikeBlock(final int tileIndex, final int baseRot) {
            BotsnBoltsGame.tm.setForeground(tileIndex, BotsnBoltsGame.blockSpike, Tile.BEHAVIOR_SOLID);
            positiveSpike = new Spike(tileIndex, baseRot);
            negativeSpike = new Spike(tileIndex, baseRot + 2);
        }
        
        protected final void moveSpikes(final int x, final int y) {
            positiveSpike.getPosition().add(x, y);
            negativeSpike.getPosition().add(-x, -y);
        }
        
        protected final void rotateSpikes(final int amtRot) {
            positiveSpike.rotate(amtRot);
            negativeSpike.rotate(amtRot);
        }
    }
    
    protected final static class Spike extends TileUnawareEnemy {
        private final float baseX;
        private final float baseY;
        
        protected Spike(final int tileIndex, final int rot) {
            super(BotsnBoltsGame.tm.getColumn(tileIndex), BotsnBoltsGame.tm.getRow(tileIndex), 1);
            final Panple pos = getPosition();
            pos.setZ(BotsnBoltsGame.DEPTH_BG);
            baseX = pos.getX();
            baseY = pos.getY();
            setDirection(rot);
            setView(BotsnBoltsGame.spike);
        }
        
        protected final void setDirection(final int rot) {
            setRot(rot);
            final int offX, offY;
            switch (rot) {
                case 1 :
                    offX = 7;
                    offY = 8;
                    break;
                case 2 :
                    offX = 7;
                    offY = 7;
                    break;
                case 3 :
                    offX = 8;
                    offY = 7;
                    break;
                default :
                    offX = 8;
                    offY = 8;
                    break;
            }
            getPosition().set(baseX + offX, baseY + offY);
        }
        
        protected final void rotate(final int amtRot) {
            setDirection(getRot() + amtRot);
        }

        @Override
        protected final void onShot(final Projectile prj) {
        }
        
        @Override
        protected final void award(final PowerUp powerUp) {
        }
    }
    
    /*
    Will react when Player stands on it.
    Might shoot flames.
    Might have other possible behavior.
    Might also have different colored blocks that use same behavior(s) based on a timer instead of pressure
    */
    protected final static class PressureBlock { //TODO
    }
    
    protected final static class ConveyorBelt { //TODO
    }
}
