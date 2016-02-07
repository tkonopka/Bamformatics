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
package bamfo.gui.components;

import java.awt.Graphics;
import java.awt.Rectangle;
import java.util.regex.Pattern;
import javax.swing.ImageIcon;
import javax.swing.JTextField;
import javax.swing.event.DocumentListener;

/**
 * An object that contains an icon and a textbox. The textbox is used to accept
 * regular expressions.
 *
 * @author tomasz
 */
public class BamfoRegexPanel extends javax.swing.JPanel {

    private ImageIcon exclamationIcon = new ImageIcon(getClass().getResource("/bamfo/gui/icons/exclamation.png"));
    RegexTextField regexTextField = new RegexTextField();

    /**
     * Creates new form BamfoRegexPanel
     */
    public BamfoRegexPanel(DocumentListener doclistener) {
        initComponents();

        // create a custom text field and put it in the panel next to the magnifier label
        regexTextField.setMinimumSize(new java.awt.Dimension(120, 24));
        regexTextField.setText("");
        regexTextField.setPreferredSize(new java.awt.Dimension(150, 24));
        add(regexTextField);

        // add the listener to the text field
        regexTextField.getDocument().addDocumentListener(doclistener);
    }

    /**
     * Custom text field. When the item is painted, the value of the text field
     * is checked. If the regex is invalid, a warning icon is painted in the
     * corner.
     */
    class RegexTextField extends JTextField {

        public RegexTextField() {
            super();
        }

        @Override
        public void paintComponent(Graphics g) {
            super.paintComponent(g);

            if (getPattern() == null) {
                Rectangle r = this.getBounds();
                exclamationIcon.paintIcon(this, g, r.width - 24, 5);
            }
        }
    }

    /**
     * Get the current regex pattern defined by this panel.
     *
     * @return
     *
     * a Pattern object if there is a valid regex expression. null if there is
     * none.
     *
     */
    public Pattern getPattern() {
        String regex = regexTextField.getText();
        Pattern pattern;
        if (regex.isEmpty()) {
            return Pattern.compile(".");
        }
        try {
            pattern = Pattern.compile(regex);
        } catch (Exception ex) {
            return null;
        }
        return pattern;
    }

    public void setPattern(String pattern) {
        regexTextField.setText(pattern);
    }

    /**
     * Get a universal pattern based on string in the panel. A universal pattern
     * is when substring separated by a space are considered as different
     * pattern that should be concatenated with an OR
     *
     * @return
     */
    public Pattern getUniversalPattern() {
        String regex = regexTextField.getText();
        if (regex.isEmpty()) {
            return Pattern.compile(".");
        }
        String[] regexa = regex.split(" ");
        StringBuilder universalpattern = new StringBuilder();
        universalpattern.append(regexa[0]);
        for (int i = 1; i < regexa.length; i++) {
            if (regexa[i].length() > 0) {
                universalpattern.append("|").append(regexa[i]);
            }
        }
        Pattern pattern;
        try {
            pattern = Pattern.compile(universalpattern.toString());
        } catch (Exception ex) {
            return null;
        }
        return pattern;
    }

    /**
     * Get the current regex pattern defined by this panel.
     *
     * @return
     *
     * a Pattern object if there is a valid regex expression. null if there is
     * none.
     *
     * 
     * @deprecated
     * use getUniversalPattern() instead
     * 
     */
    @Deprecated
    private Pattern[] getPatternArray() {

        String regex = regexTextField.getText();
        if (regex.isEmpty()) {
            Pattern[] pattern = new Pattern[1];
            try {
                pattern[0] = Pattern.compile(".");
            } catch (Exception ex) {
                return null;
            }
            return pattern;
        }

        String[] regexa = regex.split(" ");
        Pattern[] pattern = new Pattern[regexa.length];
        for (int i = 0; i < regexa.length; i++) {
            try {
                pattern[i] = Pattern.compile(regexa[i]);
            } catch (Exception ex) {
                return null;
            }
        }
        return pattern;
    }

    /**
     *
     * @return
     *
     * true if the pattern in this regex panel is trivial ("" or ".") false
     * otherwise
     */
    public boolean isTrivial() {
        String regex = regexTextField.getText();
        if (regex.isEmpty() || regex.equals(".")) {
            return true;
        }
        return false;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jLabel1 = new javax.swing.JLabel();

        setMaximumSize(new java.awt.Dimension(196, 25));
        setMinimumSize(new java.awt.Dimension(196, 25));
        setPreferredSize(new java.awt.Dimension(196, 25));
        setLayout(new javax.swing.BoxLayout(this, javax.swing.BoxLayout.LINE_AXIS));

        jLabel1.setIcon(new javax.swing.ImageIcon(getClass().getResource("/bamfo/gui/icons/magnifier.png"))); // NOI18N
        jLabel1.setMaximumSize(new java.awt.Dimension(22, 16));
        jLabel1.setMinimumSize(new java.awt.Dimension(22, 16));
        jLabel1.setPreferredSize(new java.awt.Dimension(22, 16));
        add(jLabel1);
    }// </editor-fold>//GEN-END:initComponents
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel jLabel1;
    // End of variables declaration//GEN-END:variables
}
