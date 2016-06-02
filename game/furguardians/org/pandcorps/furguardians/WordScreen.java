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
package org.pandcorps.furguardians;

import java.io.*;
import java.util.*;

import org.pandcorps.core.*;
import org.pandcorps.core.img.*;
import org.pandcorps.core.img.Pancolor.*;
import org.pandcorps.furguardians.Player.*;
import org.pandcorps.furguardians.Profile.*;
import org.pandcorps.pandam.*;
import org.pandcorps.pandam.Panput.*;
import org.pandcorps.pandam.event.*;
import org.pandcorps.pandam.event.action.*;
import org.pandcorps.pandax.in.*;
import org.pandcorps.pandax.text.*;

public final class WordScreen extends Panscreen {
    private final static int DIM = 16;
    private final static int SIZE = 4;
    private final static int NUM_WORDS = 4;
    private final static String[] SKIP = { "ETBJ", "RGHS" }; //TODO
    private final static HashMap<Integer, List<String>> dictionary = new HashMap<Integer, List<String>>();
    private static long seed = -1;
    private Panroom room = null;
    private List<Word> words = null;
    private final boolean[] letters = new boolean[26];
    private Letter[][] grid = null;
    private final List<Letter> currentSelection = new ArrayList<Letter>(SIZE * SIZE);
    
    public final static void main(final String[] args) {
        try {
            validateWordFile();
        } catch (final Throwable e) {
            e.printStackTrace();
        }
    }
    
    @Override
    protected final void load() throws Exception {
        room = initMiniZoom(64);
        addCursor(room, 20);
        loadWordFile();
        Mathtil.setSeed(seed);
        initImages();
        initWords();
        //TODO proper exit, games icon, back button, escape key
    }
    
    private final void initImages() {
        if (FurGuardiansGame.greenBlockLetters != null) {
            return;
        }
        final Img[] blStrip = FurGuardiansGame.loadBlockLetterStrip();
        FurGuardiansGame.greenBlockLetters = copyBlockLetters("green", blStrip, new SwapPixelFilter(Channel.Green, Channel.Blue, Channel.Red));
        FurGuardiansGame.whiteBlockLetters = copyBlockLetters("white", blStrip, new SwapPixelFilter(Channel.Green, Channel.Green, Channel.Green));
        FurGuardiansGame.menuOptions64 = Pangine.getEngine().createImage(FurGuardiansGame.PRE_IMG + "options64", FurGuardiansGame.RES + "menu/Options64.png");
        Img.close(blStrip);
    }
    
    private final Panmage[] copyBlockLetters(final String name, final Img[] blStrip, final PixelFilter f) {
        final int size = blStrip.length;
        for (int i = 0; i < size; i++) {
            Imtil.filterImg(blStrip[i], f);
        }
        return FurGuardiansGame.createSheet(name + ".block.letter", null, blStrip);
    }
    
    private final void initWords() {
        destroyAll();
        final List<String> list = new ArrayList<String>(NUM_WORDS);
        final char[][] g = new char[SIZE][SIZE];
        while (true) {
            clearLetters();
            boolean ok = true;
            for (int i = 0; i < NUM_WORDS; i++) {
                final int size;
                if (i == 0) {
                    size = 3;
                } else if (i < 3) {
                    size = 4;
                } else {
                    size = 5;
                }
                final String word = pickWord(size);
                if (word == null) {
                    ok = false;
                    break;
                }
                list.add(word);
            }
            if (ok) {
                if (buildGrid(list, g)) {
                    break;
                }
            }
        }
        buildGrid(g);
        words = new ArrayList<Word>(NUM_WORDS);
        for (final String word : list) {
            new Word(word);
        }
        currentSelection.clear();
        registerMiniInputs(grid[0][0], new WordScreen(), FurGuardiansGame.menuOptions64, Pangine.getEngine().getEffectiveWidth() - 7, 0);
    }
    
    private boolean buildGrid(final List<String> list, final char[][] g) {
        final int gridArea = SIZE * SIZE;
        final int[] scrap = new int[4], scrapGrid = new int[gridArea];
        for (int i = 0; i < 4; i++) {
            scrap[i] = i;
        }
        for (int i = 0; i < gridArea; i++) {
            scrapGrid[i] = i;
        }
        boolean allOk;
        while (true) {
            allOk = true;
            for (final String value : list) {
                final int size = value.length();
                int row = 0, col = 0;
                Mathtil.shuffle(scrapGrid);
                for (int i = 0; i < gridArea; i++) {
                    final int cell = scrapGrid[i];
                    row = cell / SIZE;
                    col = cell % SIZE;
                    if (g[row][col] == 0) {
                        break;
                    }
                }
                boolean wordOk = true;
                for (int i = 0; i < size; i++) {
                    g[row][col] = value.charAt(i);
                    if (i < size - 1) {
                        Mathtil.shuffle(scrap);
                        boolean letterOk = false;
                        for (int j = 0; j < 4; j++) {
                            final int d = scrap[j], nr, nc;
                            if (d == 0) {
                                nr = row + 1;
                                nc = col;
                            } else if (d == 1) {
                                nr = row;
                                nc = col + 1;
                            } else if (d == 2) {
                                nr = row - 1;
                                nc = col;
                            } else {
                                nr = row;
                                nc = col - 1;
                            }
                            if (nr >= 0 && nr < SIZE && nc >= 0 && nc < SIZE && g[nr][nc] == 0) {
                                row = nr;
                                col = nc;
                                letterOk = true;
                                break;
                            }
                        }
                        if (!letterOk) {
                            wordOk = false;
                            break;
                        }
                    }
                }
                if (!wordOk) {
                    allOk = false;
                }
            }
            if (allOk) {
                break;
            } else {
                for (int row = 0; row < SIZE; row++) {
                    for (int col = 0; col < SIZE; col++) {
                        g[row][col] = 0;
                    }
                }
            }
        }
        return !isSkipped(g);
    }
    
    private final void buildGrid(final char[][] g) {
        grid = new Letter[SIZE][SIZE];
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                new Letter(row, col, g[row][col]);
            }
        }
    }
    
    private final boolean isSkipped(final char[][] g) {
        for (final String s : SKIP) {
            if (isSkipped(g, s)) {
                return true;
            }
        }
        return false;
    }
    
    private final boolean isSkipped(final char[][] g, final String s) {
        final int end = SIZE - 1;
        for (int i = 0; i < SIZE; i++) {
            if (isSkipped(g, s, i, 0, 0, 1)) {
                return true;
            } else if (isSkipped(g, s, i, end, 0, -1)) {
                return true;
            } else if (isSkipped(g, s, 0, i, 1, 0)) {
                return true;
            } else if (isSkipped(g, s, end, i, -1, 0)) {
                return true;
            }
        }
        return false;
    }
    
    private final boolean isSkipped(final char[][] g, final String s, int row, int col, final int rowInc, final int colInc) {
        final int size = s.length();
        for (int i = 0; i < size; i++) {
            final char gc = g[row][col];
            final char sc = (char) (s.charAt(i) + 1);
            if (gc != sc) {
                return false;
            }
            row += rowInc;
            col += colInc;
        }
        return true;
    }
    
    @Override
    public final void step() {
        if (currentSelection.size() > 0 && !isTouchActive()) {
            onRelease();
        }
    }
    
    private final boolean isTouchActive() {
        for (final Letter[] row : grid) {
            for (final Letter letter : row) {
                if (letter.button.isActive()) {
                    return true;
                }
            }
        }
        return Pangine.getEngine().getInteraction().TOUCH.isActive();
    }
    
    private final void onRelease() {
        final StringBuilder b = new StringBuilder(currentSelection.size());
        for (final Letter letter : currentSelection) {
            b.append(letter.c);
        }
        final String currentWord = b.toString();
        for (final Word word : words) {
            if (currentWord.equals(word.value)) {
                award(word);
                return;
            }
        }
        clearCurrentSelection();
    }
    
    private final void award(final Word word) {
        for (final Letter letter : currentSelection) {
            letter.use();
        }
        currentSelection.clear();
        if (isVictory()) {
            grid[0][0].register(30, new TimerListener() {
                @Override public final void onTimer(final TimerEvent event) {
                    victory();
                }});
            FurGuardiansGame.playTransition(FurGuardiansGame.musicLevelEnd);
        } else {
            FurGuardiansGame.soundGem.startSound();
        }
        Chartil.set(word.b, word.value);
    }
    
    private final boolean isVictory() {
        for (final Letter[] row : grid) {
            for (final Letter letter : row) {
                if (letter.mode != MODE_USED) {
                    return false;
                }
            }
        }
        return true;
    }
    
    private final void victory() {
        seed = Mathtil.newSeed();
        final Statistics stats = getStatistics();
        if (stats != null) {
            stats.wordMiniGames++;
        }
        FurGuardiansGame.setScreen(new MiniAwardScreen(40, new WordScreen()));
    }
    
    private final void clearCurrentSelection() {
        for (final Letter letter : currentSelection) {
            letter.inactivate();
        }
        currentSelection.clear();
    }
    
    private final void clearLetters() {
        for (int i = 0; i < 26; i++) {
            letters[i] = false;
        }
    }
    
    private final void destroyAll() {
        destroyWords();
        destroyGrid();
    }
    
    private final void destroyWords() {
        if (words == null) {
            return;
        }
        for (final Word word : words) {
            if (word == null) {
                continue;
            }
            Panctor.destroy(word.text);
        }
        words = null;
    }
    
    private final void destroyGrid() {
        if (grid == null) {
            return;
        }
        for (final Letter[] row : grid) {
            if (row == null) {
                continue;
            }
            for (final Letter letter : row) {
                Panctor.destroy(letter);
            }
        }
        grid = null;
    }
    
    private final void loadWordFile() throws Exception {
        if (dictionary.size() > 0) {
            return;
        }
        seed = Mathtil.newSeed();
        BufferedReader in = null;
        try {
            in = openWordFileReader();
            String word;
            while ((word = in.readLine()) != null) {
                final Integer key = Integer.valueOf(word.length());
                List<String> list = dictionary.get(key);
                if (list == null) {
                    list = new ArrayList<String>();
                    dictionary.put(key, list);
                }
                list.add(word.toUpperCase());
            }
        } finally {
            Iotil.close(in);
        }
    }
    
    private final String pickWord(final int size) {
        final List<String> list = dictionary.get(Integer.valueOf(size));
        final int r = Mathtil.randi(0, list.size() - 1), listSize = list.size();
        final boolean d = Mathtil.rand();
        int j = r;
        while (true) {
            final String word = list.get(j);
            boolean ok = true;
            for (int i = 0; i < size; i++) {
                if (letters[getLetterIndex(word, i)]) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                for (int i = 0; i < size; i++) {
                    letters[getLetterIndex(word, i)] = true;
                }
                return word;
            } else if (d) {
                j++;
                if (j >= listSize) {
                    j = 0;
                }
            } else {
                j--;
                if (j < 0) {
                    j = listSize - 1;
                }
            }
            if (j == r) {
                return null;
            }
        }
    }
    
    private final static int getLetterIndex(final String word, final int i) {
        return word.charAt(i) - 'A';
    }
    
    private final static byte MODE_UNUSED = 0;
    private final static byte MODE_ACTIVE = 1;
    private final static byte MODE_USED = 2;
    
    private final class Letter extends Panctor {
        private final int row;
        private final int col;
        private final char c;
        private final TouchButton button;
        private byte mode = MODE_UNUSED;
        
        private Letter(final int row, final int col, final char c) {
            this.row = row;
            this.col = col;
            this.c = c;
            inactivate();
            room.addActor(this);
            final Letter[] gridRow = grid[row];
            Panctor.destroy(gridRow[col]);
            gridRow[col] = this;
            final Pangine engine = Pangine.getEngine();
            final int x = col * DIM, y = engine.getEffectiveHeight() - (row + 1) * DIM;
            getPosition().set(x, y);
            button = new TouchButton(engine.getInteraction(), "Letter." + row + "." + col, x, y, DIM, DIM);
            engine.registerTouchButton(button);
            register(button, new ActionStartListener() {
                @Override public final void onActionStart(final ActionStartEvent event) {
                    onTap();
                }});
        }
        
        private final void inactivate() {
            setMode(MODE_UNUSED, FurGuardiansGame.getBlockLetter(c));
        }
        
        private final void activate() {
            setMode(MODE_ACTIVE, FurGuardiansGame.getImageLetter(FurGuardiansGame.whiteBlockLetters, c));
        }
        
        private final void use() {
            setMode(MODE_USED, FurGuardiansGame.getImageLetter(FurGuardiansGame.greenBlockLetters, c));
            Gem.spark(getPosition(), false);
        }
        
        private final void setMode(final byte mode, final Panmage img) {
            this.mode = mode;
            setView(img);
        }
        
        private final void onTap() {
            if (mode != MODE_UNUSED) {
                return;
            } else if (!(currentSelection.isEmpty() || currentSelection.get(currentSelection.size() - 1).isAdjacentTo(this))) {
                return;
            }
            activate();
            currentSelection.add(this);
        }
        
        private final boolean isAdjacentTo(final Letter letter) {
            final int otherRow = letter.row, otherCol = letter.col;
            if (row == otherRow) {
                return Math.abs(col - otherCol) == 1;
            } else if (col == otherCol) {
                return Math.abs(row - otherRow) == 1;
            }
            return false;
        }
    }
    
    private final class Word {
        private final String value;
        private final Pantext text;
        private final StringBuilder b;
        
        private Word(final String value) {
            this.value = value;
            b = new StringBuilder();
            Chartil.appendMulti(b, '.', value.length());
            text = new Pantext(Pantil.vmid(), FurGuardiansGame.fontTiny, b);
            text.getPosition().set(DIM * SIZE + 1, Pangine.getEngine().getEffectiveHeight() - (words.size() + 1) * DIM + 5);
            room.addActor(text);
            words.add(this);
        }
    }
    
    private final static boolean isVowel(final char _c) {
        final char c = java.lang.Character.toLowerCase(_c);
        return c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u' || c == 'y';
    }
    
    private final static BufferedReader openWordFileReader() {
        return Iotil.getBufferedReader(FurGuardiansGame.RES + "text/words.txt");
    }
    
    private final static void validateWordFile() throws Exception {
        //TODO Separate list of words with 6-8 letters for 5x5 grid must have only 1 vowel and at least one duplicate letter
        BufferedReader in = null;
        try {
            in = openWordFileReader();
            String prev = null, word;
            final Set<java.lang.Character> vowels = new HashSet<java.lang.Character>(3);
            while ((word = in.readLine()) != null) {
                final int size = word.length();
                if (size < 3) {
                    throw new Exception(word + " was less than 3 letters");
                } else if (size > 5) {
                    throw new Exception(word + " was more than 5 letters");
                } else if (prev != null) {
                    if (prev.compareTo(word) >= 0) {
                        throw new Exception(word + " should not follow " + prev);
                    }
                    final char p0 = prev.charAt(0), ex = (char) (p0 + 1), w0 = word.charAt(0);
                    if ((w0 != p0) && (ex != 'x') && (w0 != ex)) {
                        throw new Exception("Letter " + p0 + " was followed by " + w0 + " instead of " + ex);
                    }
                }
                vowels.clear();
                for (int i = 0; i < size; i++) {
                    final char c = word.charAt(i);
                    if ((c < 'a') || (c > 'z')) {
                        throw new Exception(word + " contains " + c);
                    } else if (isVowel(c)) {
                        vowels.add(java.lang.Character.valueOf(c));
                        if (vowels.size() > 2) {
                            throw new Exception(word + " contained " + vowels);
                        }
                    }
                }
                //TODO Check that it's possible to find 3 other words to use with this word
                prev = word;
            }
        } finally {
            Iotil.close(in);
        }
    }
    
    protected final static PlayerContext getPlayerContext() {
        return Coltil.isEmpty(FurGuardiansGame.pcs) ? null : FurGuardiansGame.pcs.get(0);
    }
    
    protected final static Profile getProfile() {
        return PlayerContext.getProfile(getPlayerContext());
    }
    
    protected final static Statistics getStatistics() {
        return PlayerContext.getStatistics(getPlayerContext());
    }
    
    protected final static void addGems(final int n) {
        final PlayerContext pc = getPlayerContext();
        if (pc != null) {
            pc.addGems(n);
        }
    }
    
    protected final static void save() {
        final Profile prf = getProfile();
        if (prf != null) {
            prf.save();
        }
    }
    
    protected final static Panroom initMiniZoom(final int min) {
        final Pangine engine = Pangine.getEngine();
        engine.zoomToMinimum(min);
        engine.setBgColor(Menu.COLOR_BG);
        engine.getAudio().stopMusic();
        return FurGuardiansGame.createRoom(engine.getEffectiveWidth(), engine.getEffectiveHeight());
    }
    
    protected final static Cursor addCursor(final Panlayer room, final int z) {
        final Cursor cursor = FurGuardiansGame.addCursor(room);
        if (cursor != null) {
            cursor.getPosition().setZ(z);
        }
        return cursor;
    }
    
    private final static ActionEndListener newMenuListener(final Panscreen nextScreen) {
        return new ActionEndListener() {
            @Override public final void onActionEnd(final ActionEndEvent event) {
                goMiniMenu(nextScreen, "Play", true);
            }};
    }
    
    protected final static void registerMiniInputs(final Panctor actor, final Panscreen nextScreen, final Panmage menuImg, final int menuX, final int menuY) {
        final Pangine engine = Pangine.getEngine();
        final Panteraction interaction = engine.getInteraction();
        Player.registerCaptureScreen(actor);
        actor.register(interaction.BACK, newMenuListener(nextScreen));
        final TouchButton button = new TouchButton(interaction, actor.getLayer(), "mini.menu", menuX, menuY, 0, menuImg, null, true);
        engine.registerTouchButton(button);
        actor.register(button, newMenuListener(nextScreen));
    }
    
    protected final static void goMiniMenu(final Panscreen nextScreen, final String nextLabel, final boolean quitNeeded) {
        FurGuardiansGame.setScreen(new MiniMenuScreen(nextScreen, nextLabel, quitNeeded));
    }
    
    protected final static class MiniAwardScreen extends Panscreen {
        private final int award;
        private final Panscreen nextScreen;
        
        protected MiniAwardScreen(final int award, final Panscreen nextScreen) {
            this.award = award;
            this.nextScreen = nextScreen;
        }
        
        @Override
        protected final void load() {
            final PlayerContext pc = getPlayerContext();
            if (pc == null) {
                goNext();
                return;
            }
            final Panroom room = initMiniZoom(128);
            final Pangine engine = Pangine.getEngine();
            final int w = engine.getEffectiveWidth(), h = engine.getEffectiveHeight();
            final Gem gem = Menu.PlayerScreen.addHudGems(room, pc, (w - 72) / 2, (h - 16) / 2);
            addGems(award);
            save();
            gem.register(new ActionEndListener() {
                @Override public final void onActionEnd(final ActionEndEvent event) {
                    goNext();
                }});
            gem.register(90, new TimerListener() {
                @Override public final void onTimer(final TimerEvent event) {
                    goNext();
                }});
        }
        
        private final void goNext() {
            goMiniMenu(nextScreen, "Next", false);
        }
    }
    
    protected final static class MiniMenuScreen extends Panscreen {
        private final Panscreen nextScreen;
        private final String nextLabel;
        private final boolean quitNeeded;
        
        protected MiniMenuScreen(final Panscreen nextScreen, final String nextLabel, final boolean quitNeeded) {
            this.nextScreen = nextScreen;
            this.nextLabel = nextLabel;
            this.quitNeeded = quitNeeded;
        }
        
        @Override
        protected final void load() {
            final Panroom room = initMiniZoom(128);
            addCursor(room, 20);
            final Pangine engine = Pangine.getEngine();
            final int numButtons = quitNeeded ? 3 : 2;
            final int w = FurGuardiansGame.MENU_W;
            final int x = (engine.getEffectiveWidth() - (w * numButtons)) / 2;
            final int y = (engine.getEffectiveHeight() - FurGuardiansGame.MENU_H) / 2;
            Menu.PlayerScreen.newFormButton(room, "Next", x, y, FurGuardiansGame.menuRight, nextLabel, new Runnable() {
                @Override public final void run() {
                    goNext();
                }});
            Menu.PlayerScreen.newFormButton(room, "Menu", x + w, y, FurGuardiansGame.menuOptions, "Menu", new Runnable() {
                @Override public final void run() {
                    FurGuardiansGame.goMiniGames(getPlayerContext());
                }});
            if (quitNeeded) {
                Menu.PlayerScreen.newFormButton(room, "Quit", x + (w * 2), y, FurGuardiansGame.menuOff, "Quit", new Runnable() {
                    @Override public final void run() {
                        engine.exit();
                    }});
            }
        }
        
        private final void goNext() {
            FurGuardiansGame.setScreen(nextScreen);
        }
    }
}
