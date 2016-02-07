/*
 * Copyright 2013 Tomasz Konopka.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package bamfo.gui.decorations;

import java.awt.Component;
import java.awt.Graphics;
import javax.swing.ImageIcon;

/**
 * This is an extension of an image icon. It can display a base icon together
 * with small decorations in the corners.
 *
 * The decorations are the "small" icons from the Fugue icons set.
 * http://p.yusukekamiyamane.com/
 *
 * Objects of the class can display at most four decoration at any one time.
 *
 * The decoration icons are all loaded as static variables (the icons are small,
 * and presumably an application would display many of them so it is worth
 * loading them into memory instead of fetching them every time).
 *
 *
 *
 *
 * Note: the coding here is a bit brute-force. Maybe there's a more elegant way
 * of connecting between user-requested decorations and the loaded files?
 *
 * Note: The DecoratedImageIcon does not overload the functions reporting icon
 * size. Thus, in some cases the decorations may end up being drawn far
 * off-center and can then looked clipped. In such cases, it may be helpful to
 * set an empty border on the component being drawn to.
 *
 *
 *
 * @author tomasz
 *
 */
public class DecoratedImageIcon extends ImageIcon {

    // helper to specify what overlays are available
    public static enum Decoration {

        asterisk, asterisk_yellow,
        block,
        ligh_bulb_off, light_bulb_on,
        cross, cross_circle, cross_white,
        document,
        exclamation_red, exclamation_yellow, exclamation_white,
        fire, gear,
        information,
        lock, lightning,
        minus, minus_circle, minus_white,
        none,
        paperclip, pencil, prohibition,
        plus, plus_circle, plus_white,
        question, question_white,
        spectrum, sticky,
        tick, tick_circle, tick_white
    };
    // specify position of the overlays
    public static int TOPLEFT = 0;
    public static int TOPRIGHT = 1;
    public static int BOTTOMLEFT = 2;
    public static int BOTTOMRIGHT = 3;
    public static int CENTER = 4;
    // images of the icons that will be used as overlays    
    private final static ImageIcon asterisk = getIcon("asterisk-small.png");
    private final static ImageIcon asterisk_yellow = getIcon("asterisk-small-yellow.png");
    private final static ImageIcon block = getIcon("block-small.png");
    private final static ImageIcon cross = getIcon("cross-small.png");
    private final static ImageIcon cross_circle = getIcon("cross-small-circle.png");
    private final static ImageIcon cross_white = getIcon("cross-small-white.png");
    private final static ImageIcon document = getIcon("blue-document-small.png");
    private final static ImageIcon light_bulb_off = getIcon("light-bulb-small-off.png");
    private final static ImageIcon light_bulb_on = getIcon("light-bulb-small.png");
    private final static ImageIcon exclamation_red = getIcon("exclamation-small-red.png");
    private final static ImageIcon exclamation_yellow = getIcon("exclamation-small.png");
    private final static ImageIcon fire = getIcon("fire-small.png");
    private final static ImageIcon gear = getIcon("gear-small.png");
    private final static ImageIcon information = getIcon("information-small.png");
    private final static ImageIcon lock = getIcon("lock-small.png");
    private final static ImageIcon lightning = getIcon("lightning-small.png");
    private final static ImageIcon minus = getIcon("minus-small.png");
    private final static ImageIcon minus_white = getIcon("minus-small-white.png");
    private final static ImageIcon minus_circle = getIcon("minus-small-circle.png");
    private final static ImageIcon paperclip = getIcon("paper-clip-small.png");
    private final static ImageIcon pencil = getIcon("pencil-small.png");
    private final static ImageIcon prohibition = getIcon("prohibition-small.png");
    private final static ImageIcon plus_white = getIcon("plus-small-white.png");
    private final static ImageIcon plus_circle = getIcon("plus-small-circle.png");
    private final static ImageIcon plus = getIcon("plus-small.png");
    private final static ImageIcon question = getIcon("question-small.png");
    private final static ImageIcon question_white = getIcon("question-small-white.png");
    private final static ImageIcon spectrum = getIcon("spectrum-small.png");
    private final static ImageIcon sticky = getIcon("sticky-note-small.png");
    private final static ImageIcon tick = getIcon("tick-small.png");
    private final static ImageIcon tick_circle = getIcon("tick-small-circle.png");
    private final static ImageIcon tick_white = getIcon("tick-small-white.png");
    //private final static ImageIcon tick = getIcon("tick-small.png");
    // An instance of the class will have one main icon and several overlays
    // the main icon and associated information is found in ImageIcon
    // Here, the definition for which decorations to place in the corners.    
    private ImageIcon[] decorations = new ImageIcon[5];
    private final int shift;

    /**
     * Shortcut to get an icon with a given name from the Bamfo.icons resource
     *
     * @param name
     *
     * file name of icon
     *
     * @return
     *
     * the image of the icon
     */
    private static ImageIcon getIcon(String name) {
        return (new ImageIcon(DecoratedImageIcon.class.getResource(name)));
    }

    /**
     * Creates a decorated icon from a simple icon. Decorations will be shifted
     * by some default value.
     *
     * @param base
     */
    public DecoratedImageIcon(ImageIcon base) {
        this(base, 5);
    }

    /**
     * Creates a decorated icon from an existing icon. Decorations will appear
     * shifter by some offset from the main icon.
     *
     * @param base
     *
     * @param shift
     *
     */
    public DecoratedImageIcon(ImageIcon base, int shift) {
        super(base.getImage());
        clearDecorations();
        this.shift = shift;
    }

    /**
     * Requests to place a decoration in one corner of the icon. If a decoration
     * was already set at the same position, the old decoration is overwritten
     * and
     *
     * @param decoration
     *
     * @param position
     *
     * this should be a number between 0 and 3. Best to user definitions
     * TOPLEFT, TOPRIGHT, etc. specified in the class.
     *
     *
     */
    public void setDecoration(Decoration decoration, int position) {
        // check that position is a legal one
        if (position < 0 || position > 4) {
            return;
        }

        switch (decoration) {
            case asterisk:
                decorations[position] = asterisk;
                break;
            case asterisk_yellow:
                decorations[position] = asterisk_yellow;
                break;
            case block:
                decorations[position] = block;
                break;
            case cross:
                decorations[position] = cross;
                break;
            case cross_circle:
                decorations[position] = cross_circle;
                break;
            case cross_white:
                decorations[position] = cross_white;
                break;
            case document:
                decorations[position] = document;
                break;
            case exclamation_red:
                decorations[position] = exclamation_red;
                break;
            case exclamation_yellow:
                decorations[position] = exclamation_yellow;
                break;
            case fire:
                decorations[position] = fire;
                break;
            case gear:
                decorations[position] = gear;
                break;
            case information:
                decorations[position] = information;
                break;
            case ligh_bulb_off:
                decorations[position] = light_bulb_off;
                break;
            case light_bulb_on:
                decorations[position] = light_bulb_on;
                break;
            case lightning:
                decorations[position] = lightning;
                break;
            case lock:
                decorations[position] = lock;
                break;
            case minus:
                decorations[position] = minus;
                break;
            case minus_circle:
                decorations[position] = minus_circle;
                break;
            case minus_white:
                decorations[position] = minus_white;
                break;
            case none:
                // this is an important case which allows to rest just one position
                decorations[position] = null;
                break;
            case paperclip:
                decorations[position] = paperclip;
                break;            
            case pencil:
                decorations[position] = pencil;
                break;
            case prohibition:
                decorations[position] = prohibition;
                break;
            case plus:
                decorations[position] = plus;
                break;
            case plus_white:
                decorations[position] = plus_white;
                break;
            case plus_circle:
                decorations[position] = plus_circle;
                break;
            case question:
                decorations[position] = question;
                break;
            case question_white:
                decorations[position] = question_white;
                break;
            case spectrum:
                decorations[position] = spectrum;
                break;
            case sticky:
                decorations[position] = sticky;
                break;
            case tick:
                decorations[position] = tick;
                break;
            case tick_circle:
                decorations[position] = tick_circle;
                break;
            case tick_white:
                decorations[position] = tick_white;
                break;
            default:
                break;
        }

    }

    /**
     * Resets the icon to one without any decorations.
     *
     */
    public final void clearDecorations() {
        decorations[0] = null;
        decorations[1] = null;
        decorations[2] = null;
        decorations[3] = null;
    }

    /**
     * Paints the icon onto some component. This invokes the paint method from
     * the super ImageIcon component. Then draws the decorations.
     *
     * @param c
     * @param g
     * @param x
     * @param y
     */
    @Override
    public synchronized void paintIcon(Component c, Graphics g, int x, int y) {
        // paint the main icon        
        super.paintIcon(c, g, x, y);

        // now paint the decorations in each of the corners
        if (decorations[TOPLEFT] != null) {
            decorations[TOPLEFT].paintIcon(c, g, x - shift, y - shift);
        }
        if (decorations[TOPRIGHT] != null) {
            decorations[TOPRIGHT].paintIcon(c, g, x + shift, y - shift);
        }
        if (decorations[BOTTOMLEFT] != null) {
            decorations[BOTTOMLEFT].paintIcon(c, g, x - shift, y + shift);
        }
        if (decorations[BOTTOMRIGHT] != null) {
            decorations[BOTTOMRIGHT].paintIcon(c, g, x + shift, y + shift);
        }
        if (decorations[CENTER] != null) {
            decorations[CENTER].paintIcon(c, g, x, y);
        }

    }
}
