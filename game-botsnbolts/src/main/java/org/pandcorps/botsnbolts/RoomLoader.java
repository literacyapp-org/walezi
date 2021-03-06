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

import java.lang.reflect.Constructor;
import java.util.*;

import org.pandcorps.botsnbolts.BlockPuzzle.*;
import org.pandcorps.botsnbolts.Enemy.*;
import org.pandcorps.botsnbolts.ShootableDoor.*;
import org.pandcorps.core.*;
import org.pandcorps.core.img.*;
import org.pandcorps.core.seg.*;
import org.pandcorps.pandam.*;
import org.pandcorps.pandax.tile.*;
import org.pandcorps.pandax.tile.Tile.*;

public abstract class RoomLoader {
    private final static Map<BotCell, BotRoom> rooms = new HashMap<BotCell, BotRoom>();
    private final static List<Enemy> enemies = new ArrayList<Enemy>();
    private final static List<ShootableDoor> doors = new ArrayList<ShootableDoor>();
    private final static List<TileAnimator> animators = new ArrayList<TileAnimator>();
    private final static Map<Character, Tile> tiles = new HashMap<Character, Tile>();
    private static int row = 0;
    
    private static BotRoom room = null;
    
    protected final static void setRoom(final BotRoom room) {
        RoomLoader.room = room;
    }
    
    protected abstract Panroom newRoom();
    
    /*
    TODO
    Validation
    no overlapping cells
    no ladders between left side of one long room and right side of another
    */
    protected final static class ScriptRoomLoader extends RoomLoader {
        @Override
        protected final Panroom newRoom() {
            final Pangine engine = Pangine.getEngine();
            final Panroom room = BotsnBoltsGame.BotsnBoltsScreen.newRoom(RoomLoader.room.w * BotsnBoltsGame.GAME_W);
            row = BotsnBoltsGame.tm.getHeight() - 1;
            SegmentStream in = null;
            try {
                Segment seg;
                in = SegmentStream.openLocation(BotsnBoltsGame.RES + "/level/" + RoomLoader.room.roomId + ".txt");
                seg = in.readRequire("CTX"); // Context
                BotsnBoltsGame.BotsnBoltsScreen.loadTileImage(seg.getValue(0));
                //TODO Add bg image
                engine.setBgColor(toColor(seg.getField(2)));
                while ((seg = in.read()) != null) {
                    final String name = seg.getName();
                    if ("ANM".equals(name)) { // Animator
                        anm(seg);
                    } else if ("PUT".equals(name)) { // Put
                        put(seg);
                    } else if ("M".equals(name)) { // Map
                        m(seg);
                    } else if ("RCT".equals(name)) { // Rectangle
                        rct(seg.intValue(0), seg.intValue(1), seg.intValue(2), seg.intValue(3), seg, 4);
                    } else if ("ROW".equals(name)) { // Row
                        row(seg);
                    } else if ("COL".equals(name)) { // Column
                        col(seg);
                    } else if ("TIL".equals(name)) { // Tile
                        til(seg);
                    } else if ("BOX".equals(name)) { // Power-up Box
                        box(seg.intValue(0), seg.intValue(1));
                    } else if ("ENM".equals(name)) { // Enemy
                        enm(seg.intValue(0), seg.intValue(1), seg.getValue(2));
                    } else if ("SHP".equals(name)) { // Shootable Block Puzzle
                        shp(seg);
                    } else if ("TMP".equals(name)) { // Timed Block Puzzle
                        tmp(in);
                    } else if ("HDP".equals(name)) { // Hidden Block Puzzle
                        hdp(seg);
                    } else if ("SPP".equals(name)) { // Spike Block Puzzle
                        spp(seg);
                    } else if ("LDR".equals(name)) { // Ladder
                        ldr(seg.intValue(0), seg.intValue(1), seg.intValue(2));
                    } else if ("BRR".equals(name)) { // Barrier
                        brr(seg.intValue(0), seg.intValue(1), seg.getValue(2));
                    } else if ("DOR".equals(name)) { // Door
                        dor(seg.intValue(0), seg.intValue(1), seg.getValue(2));
                    } else if ("DEF".equals(name)) { // Definition
                    }
                }
                return room;
            } catch (final Exception e) {
                throw Pantil.toRuntimeException(e);
            } finally {
                Iotil.close(in);
            }
        }
    }
    
    private final static void anm(final Segment seg) {
        final TileMap tm = BotsnBoltsGame.tm;
        Tile tile = null;
        final byte b = seg.byteValue(0);
        final boolean bg = seg.booleanValue(1);
        final int period = seg.intValue(2);
        final List<Field> fields = seg.getRepetitions(3);
        final int size = fields.size();
        final TileFrame[] frames = new TileFrame[size];
        for (int i = 0; i < size; i++) {
            final Field field = fields.get(i);
            final TileMapImage image = getTileMapImage(field, 0);
            final int duration = field.intValue(2);
            frames[i] = new TileFrame(image, duration);
            if (i == 0) {
                final TileMapImage background, foreground;
                if (bg) {
                    background = image;
                    foreground = null;
                } else {
                    background = null;
                    foreground = image;
                }
                tile = tm.getTile(background, foreground, b);
            }
        }
        animators.add(new TileAnimator(tile, bg, period, frames));
    }
    
    private final static void put(final Segment seg) {
        tiles.put(seg.toCharacter(0), getTile(seg, 1));
    }
    
    private final static void m(final Segment seg) {
        final TileMap tm = BotsnBoltsGame.tm;
        final String value = seg.getValue(0);
        final int size = Chartil.size(value);
        for (int i = 0; i < size; i++) {
            tm.setTile(i, row, tiles.get(Character.valueOf(value.charAt(i))));
        }
        row--;
    }
    
    private final static Tile getTile(final Segment seg, final int tileOffset) {
        final TileMapImage bg = getTileMapImage(seg, tileOffset), fg = getTileMapImage(seg, tileOffset + 2);
        final byte b = seg.getByte(tileOffset + 4, Tile.BEHAVIOR_OPEN);
        return (bg == null && fg == null && b == Tile.BEHAVIOR_OPEN) ? null : BotsnBoltsGame.tm.getTile(bg, fg, b);
    }
    
    private final static void rct(final int x, final int y, final int w, final int h, final Segment seg, final int tileOffset) throws Exception {
        final TileMap tm = BotsnBoltsGame.tm;
        final Tile tile = getTile(seg, tileOffset);
        for (int i = 0; i < w; i++) {
            final int currX = x + i;
            for (int j = 0; j < h; j++) {
                final int currY = y + j;
                tm.setTile(currX, currY, tile);
            }
        }
    }
    
    private final static void row(final Segment seg) throws Exception {
        rct(0, seg.intValue(0), BotsnBoltsGame.tm.getWidth(), 1, seg, 1);
    }
    
    private final static void col(final Segment seg) throws Exception {
        rct(seg.intValue(0), 0, 1, BotsnBoltsGame.tm.getHeight(), seg, 1);
    }
    
    private final static void til(final Segment seg) throws Exception {
        rct(seg.intValue(0), seg.intValue(1), 1, 1, seg, 2);
    }
    
    private final static void box(final int x, final int y) {
        enemies.add(new PowerBox(x, y));
    }
    
    private final static void enm(final int x, final int y, final String enemyType) throws Exception {
        enemies.add(getEnemyConstructor(enemyType).newInstance(Integer.valueOf(x), Integer.valueOf(y)));
    }
    
    private final static Map<String, Constructor<? extends Enemy>> enemyTypes = new HashMap<String, Constructor<? extends Enemy>>();
    
    @SuppressWarnings("unchecked")
    private final static Constructor<? extends Enemy> getEnemyConstructor(final String enemyType) throws Exception {
        Constructor<? extends Enemy> constructor = enemyTypes.get(enemyType);
        if (constructor != null) {
            return constructor;
        }
        for (final Class<?> c : Enemy.class.getDeclaredClasses()) {
            final String name = c.getName();
            if (name.endsWith(enemyType) && name.charAt(name.length() - enemyType.length() - 1) == '$') {
                constructor = (Constructor<? extends Enemy>) c.getDeclaredConstructor(Integer.TYPE, Integer.TYPE);
                enemyTypes.put(enemyType, constructor);
                return constructor;
            }
        }
        throw new IllegalArgumentException("Unrecognized enemyType " + enemyType);
    }
    
    private final static void shp(final Segment seg) {
        new ShootableBlockPuzzle(getTileIndexArray(seg, 0), getTileIndexArray(seg, 1));
    }
    
    private final static int[] getTileIndexArray(final Segment seg, final int fieldIndex) {
        final TileMap tm = BotsnBoltsGame.tm;
        final List<Field> reps = seg.getRepetitions(fieldIndex);
        final int size = reps.size();
        final int[] indices = new int[size];
        for (int i = 0; i < size; i++) {
            final Field f = reps.get(i);
            indices[i] = tm.getIndex(f.intValue(0), f.intValue(1));
        }
        return indices;
    }
    
    private final static void tmp(final SegmentStream in) throws Exception {
        final List<int[]> steps = new ArrayList<int[]>();
        Segment seg;
        while ((seg = in.readIf("TMS")) != null) {
            steps.add(getTileIndexArray(seg, 0));
        }
        new TimedBlockPuzzle(steps);
    }
    
    private final static void hdp(final Segment seg) {
        new HiddenBlockPuzzle(getTileIndexArray(seg, 0));
    }
    
    private final static void spp(final Segment seg) {
        new SpikeBlockPuzzle(getTileIndexArray(seg, 0), getTileIndexArray(seg, 1));
    }
    
    private final static void ldr(final int x, final int y, final int h) {
        final TileMap tm = BotsnBoltsGame.tm;
        final int end = h - 1;
        for (int j = 0; j < h; j++) {
            final byte b;
            if (j == end) {
                if (j < (tm.getHeight() - 1)) {
                    b = BotsnBoltsGame.TILE_LADDER_TOP;
                } else {
                    b = BotsnBoltsGame.TILE_LADDER;
                }
            } else {
                b = BotsnBoltsGame.TILE_LADDER;
            }
            tm.setForeground(x, y + j, BotsnBoltsGame.ladder, b);
        }
    }
    
    private final static void brr(final int x, final int y, final String doorType) {
        new ShootableBarrier(x, y, ShootableDoor.getShootableDoorDefinition(doorType));
    }
    
    private final static void dor(final int x, final int y, final String doorType) {
        if ("Boss".equals(doorType)) {
            new BossDoor(x, y);
        } else {
            doors.add(new ShootableDoor(x, y, ShootableDoor.getShootableDoorDefinition(doorType)));
        }
    }
    
    private final static TileMapImage getTileMapImage(final Record seg, final int imageOffset) {
        final int imgX = seg.getInt(imageOffset, -1);
        if (imgX < 0) {
            return null;
        }
        return BotsnBoltsGame.imgMap[seg.intValue(imageOffset + 1)][imgX];
    }
    
    private final static Pancolor toColor(final Field fld) {
        return fld == null ? FinPancolor.BLACK : new FinPancolor(fld.shortValue(0), fld.shortValue(1), fld.shortValue(2));
    }
    
    protected final static void onChangeFinished() {
        final Panlayer layer = BotsnBoltsGame.tm.getLayer();
        for (final Enemy enemy : enemies) {
            layer.addActor(enemy);
        }
        for (final ShootableDoor door : doors) {
            door.closeDoor();
        }
        clearChangeFinished();
    }
    
    protected final static void step() {
        for (final TileAnimator animator : animators) {
            animator.step();
        }
    }
    
    private final static void clearChangeFinished() {
        enemies.clear();
        doors.clear();
    }
    
    protected final static void clear() {
        clearChangeFinished();
        animators.clear();
        tiles.clear();
    }
    
    protected final static void loadRooms() {
        SegmentStream in = null;
        try {
            in = SegmentStream.openLocation(BotsnBoltsGame.RES + "/level/Rooms.txt");
            Segment seg;
            while ((seg = in.read()) != null) {
                final int x = seg.intValue(0), y = seg.intValue(1), w = seg.intValue(2);
                final BotRoom room = new BotRoom(x, y, w, seg.getValue(3));
                for (int i = 0; i < w; i++) {
                    rooms.put(new BotCell(x + i, y), room);
                }
            }
        } catch (final Exception e) {
            throw Pantil.toRuntimeException(e);
        } finally {
            Iotil.close(in);
        }
    }
    
    protected final static BotRoom getRoom(final int x, final int y) {
        return rooms.get(new BotCell(x, y));
    }
    
    protected final static BotRoomCell getAdjacentRoom(final Player player, final int dirX, final int dirY) {
        final int x, y;
        if (dirX < 0) {
            x = room.x - 1;
            y = room.y;
        } else if (dirX > 0) {
            x = room.x + room.w;
            y = room.y;
        } else {
            x = (player.getPosition().getX() < BotsnBoltsGame.GAME_W) ? room.x : (room.x + room.w - 1);
            y = room.y + dirY;
        }
        final BotCell cell = new BotCell(x, y);
        final BotRoom room = rooms.get(cell);
        if (room == null) {
            return null;
        }
        return new BotRoomCell(room, cell);
    }
    
    protected final static class TileAnimator {
        private final Tile tile;
        private final boolean bg;
        private final int period;
        private final TileFrame[] frames;
        
        protected TileAnimator(final Tile tile, final boolean bg, final int period, final TileFrame[] frames) {
            this.tile = tile;
            this.bg = bg;
            this.period = period;
            this.frames = frames;
        }
        
        protected final void step() {
            final long index = Pangine.getEngine().getClock() % period;
            int limit = 0;
            for (final TileFrame frame : frames) {
                limit += frame.duration;
                if (index < limit) {
                    if (bg) {
                        tile.setBackground(frame.image);
                    } else {
                        tile.setForeground(frame.image);
                    }
                    break;
                }
            }
        }
    }
    
    protected final static class TileFrame {
        private final Object image;
        private final int duration;
        
        protected TileFrame(final Object image, final int duration) {
            this.image = image;
            this.duration = duration;
        }
    }
    
    protected final static class BotRoom {
        protected final int x;
        protected final int y;
        protected final int w;
        protected final String roomId;
        
        protected BotRoom(final int x, final int y, final int w, final String roomId) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.roomId = roomId;
        }
    }
    
    protected final static class BotCell {
        protected final int x;
        protected final int y;
        
        protected BotCell(final int x, final int y) {
            this.x = x;
            this.y = y;
        }
        
        @Override
        public final int hashCode() {
            return (y * 10000) + x;
        }
        
        @Override
        public final boolean equals(final Object o) {
            if (!(o instanceof BotCell)) {
                return false;
            }
            final BotCell c = (BotCell) o;
            return (x == c.x) && (y == c.y);
        }
    }
    
    protected final static class BotRoomCell {
        protected final BotRoom room;
        protected final BotCell cell;
        
        protected BotRoomCell(final BotRoom room, final BotCell cell) {
            this.room = room;
            this.cell = cell;
        }
    }
    
    protected final static class DemoRoomLoader extends RoomLoader {
        @Override
        protected final Panroom newRoom() {
            return BotsnBoltsGame.BotsnBoltsScreen.newDemoRoom();
        }
    }
}
