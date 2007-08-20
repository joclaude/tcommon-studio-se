// ============================================================================
//
// Talend Community Edition
//
// Copyright (C) 2006-2007 Talend - www.talend.com
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
//
// ============================================================================
package org.talend.commons.ui.utils;

import org.apache.oro.text.regex.MalformedPatternException;
import org.apache.oro.text.regex.Pattern;
import org.apache.oro.text.regex.Perl5Compiler;
import org.apache.oro.text.regex.Perl5Matcher;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.TypedEvent;
import org.eclipse.swt.widgets.Control;
import org.talend.commons.ui.swt.colorstyledtext.ColorStyledText;

/**
 * DOC amaumont class global comment. Detailled comment <br/>
 * 
 * $Id$
 * 
 */
public class TypedTextCommandExecutor {

    private static final int KEY_CODE_REDO = 121; // 'y'

    private static final int KEY_CODE_UNDO = 122; // 'z'

    private static final int KEY_CODE_PROPOSAL = 27; // ' '

    public static final String PARAMETER_NAME = "PARAMETER_NAME";

    private Key previousKey;

    private String previousText;

    private KeyListener keyListener = new KeyListener() {

        public void keyPressed(KeyEvent e) {
            // keyPressedExecute(e);
        }

        public void keyReleased(KeyEvent e) {
            keyReleasedExecute(e);
        }

    };

    private FocusListener focusListener = new FocusListener() {

        public void focusGained(FocusEvent e) {
            focusGainedExecute(e);
        }

        public void focusLost(FocusEvent e) {
            focusLostExecute(e);
        }

    };

    private MouseListener mouseListener = new MouseAdapter() {

        /*
         * (non-Javadoc)
         * 
         * @see org.eclipse.swt.events.MouseAdapter#mouseUp(org.eclipse.swt.events.MouseEvent)
         */
        @Override
        public void mouseUp(MouseEvent e) {
            if (e.button == 3) {
                mouseUpExecute(e);
            }
        }

    };

    private Pattern patternAlphaNum;

    private Perl5Matcher matcher;

    private ModifyListener modifyListener = new ModifyListener() {

        /*
         * (non-Javadoc)
         * 
         * @see org.eclipse.swt.events.ModifyListener#modifyText(org.eclipse.swt.events.ModifyEvent)
         */
        public void modifyText(ModifyEvent e) {
            mouseUpExecute(e);
        }

    };

    /**
     * DOC amaumont TypedTextCommandExecutor constructor comment.
     * 
     * @param stack
     */
    public TypedTextCommandExecutor() {
        super();
        init();
    }

    /**
     * DOC qiang.zhang Comment method "mouseUpExecute".
     * 
     * @param e
     */
    protected void mouseUpExecute(TypedEvent e) {
        Control control = (Control) e.getSource();
        String currentText = ControlUtils.getText(control);
        previousText2 = previousText;
        activeControl = control.getData(PARAMETER_NAME);
        if (currentText.equals(previousText)) {
            // nothing
        } else if (!currentText.equals(previousText)) {
            addNewCommand(control);
            previousText = currentText;
        }

    }

    /**
     * DOC amaumont Comment method "init".
     */
    private void init() {

        Perl5Compiler compiler = new Perl5Compiler();
        matcher = new Perl5Matcher();
        patternAlphaNum = null;
        try {
            patternAlphaNum = compiler.compile("\\w");
        } catch (MalformedPatternException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    protected void keyPressedExecute(KeyEvent e) {
        // TODO Auto-generated method stub

    }

    protected void keyReleasedExecute(KeyEvent e) {

        Control control = (Control) e.getSource();

        String currentText = ControlUtils.getText(control);
        previousText2 = previousText;
        activeControl = control.getData(PARAMETER_NAME);
        // System.out.println(e);

        boolean alphaNumMatched = matcher.matches(String.valueOf(e.character), patternAlphaNum);
        boolean ctrlKey = (e.stateMask & (SWT.CTRL)) != 0;
        boolean combinedKeys = (e.stateMask & (SWT.CTRL | SWT.ALT | SWT.SHIFT)) != 0;
        boolean undoOrRedo = ctrlKey && (e.keyCode == KEY_CODE_REDO || e.keyCode == KEY_CODE_UNDO);
        boolean proposal = ctrlKey && (e.keyCode == KEY_CODE_PROPOSAL);
        if (combinedKeys && !undoOrRedo && currentText.equals(previousText) || proposal) {
            // nothing
        } else if (!currentText.equals(previousText)) {
            // System.out.println("undoOrRedo===============" + undoOrRedo);
            // System.out.println("ctrlKey===============" + ctrlKey);
            if (undoOrRedo) {
                // nothing
            } else if ((this.previousKey != null && alphaNumMatched && this.previousKey.alphaNumMatched)
            /* || (e.character == ' ' && !"DBTABLE".equals(activeControl)) */) {
                updateCommand(control);
            } else {
                addNewCommand(control);
            }
            previousText = currentText;
            this.previousKey = new Key(e, alphaNumMatched);
        }
    }

    /**
     * DOC amaumont Comment method "updateCommand".
     * 
     * @param commandStack2
     */
    public void updateCommand(Control control) {
        // TODO Auto-generated method stub

    }

    /**
     * 
     * To store previous typed key informations. <br/>
     * 
     * $Id$
     * 
     */
    class Key {

        public char character;

        public int stateMask;

        public int keyCode;

        private boolean alphaNumMatched;

        /**
         * DOC amaumont Key constructor comment.
         * 
         * @param e
         * @param alphaNumMatched
         */
        public Key(KeyEvent e, boolean alphaNumMatched) {
            this.character = e.character;
            this.stateMask = e.stateMask;
            this.keyCode = e.keyCode;
            this.alphaNumMatched = alphaNumMatched;
        }

    }

    /**
     * 
     * Implement your method by overriding.
     */
    public void addNewCommand(Control control) {

    }

    /**
     * DOC amaumont Comment method "register".
     * 
     * @param control
     */
    public void register(Control control) {
        control.addKeyListener(keyListener);
        control.addFocusListener(focusListener);
        control.addMouseListener(mouseListener);
        if (control instanceof ColorStyledText) {
            ((ColorStyledText) control).addModifyListener(modifyListener);
        }
    }

    /**
     * DOC amaumont Comment method "unregister".
     * 
     * @param control
     */
    public void unregister(Control control) {
        control.removeKeyListener(keyListener);
        control.removeFocusListener(focusListener);
        control.removeMouseListener(mouseListener);
        if (control instanceof ColorStyledText) {
            ((ColorStyledText) control).removeModifyListener(modifyListener);
        }
    }

    private void focusGainedExecute(FocusEvent e) {
        previousKey = null;
        previousText = ControlUtils.getText((Control) e.getSource());

    }

    private void focusLostExecute(FocusEvent e) {
        String currentText = ControlUtils.getText((Control) e.getSource());
        if (!currentText.equals(previousText)) {
            updateCommand((Control) e.getSource());
        }
    }

    private static String previousText2 = "";

    public String getPreviousText2() {
        return previousText2;
    }

    private static Object activeControl;

    public Object getActiveControl() {
        return activeControl;
    }

}
