// ============================================================================
//
// Copyright (C) 2006-2007 Talend Inc. - www.talend.com
//
// This source code is available under agreement available at
// %InstallDIR%\features\org.talend.rcp.branding.%PRODUCTNAME%\%PRODUCTNAME%license.txt
//
// You should have received a copy of the agreement
// along with this program; if not, write to Talend SA
// 9 rue Pages 92150 Suresnes, France
//
// ============================================================================
package org.talend.core.ui.proposal;

import java.text.MessageFormat;

import org.eclipse.jface.fieldassist.IContentProposal;
import org.talend.core.i18n.Messages;

/**
 * DOC qwei class global comment. Detailled comment
 */
public class JavaGlobalUtils {

    public static IContentProposal[] getProposals() {
        IContentProposal[] cp = new IContentProposal[] { new JavaGlobalVariableProposal("projectName", "Project Name"),
                new JavaGlobalVariableProposal("jobName", "Job Name"), };
        return cp;
    }

    /**
     * 
     * DOC ggu PerlGlobalUtils class global comment. Detailled comment <br/>
     * 
     */
    static class JavaGlobalVariableProposal implements IContentProposal {

        private String name;

        private String desc;

        private String code;

        private String display;

        public JavaGlobalVariableProposal(String name, String desc) {

            this.name = name;
            this.desc = desc;

            this.code = name;
            this.display = "global." + name;

        }

        /*
         * (non-Javadoc)
         * 
         * @see org.eclipse.jface.fieldassist.IContentProposal#getContent()
         */
        public String getContent() {

            return code;
        }

        /*
         * (non-Javadoc)
         * 
         * @see org.eclipse.jface.fieldassist.IContentProposal#getCursorPosition()
         */
        public int getCursorPosition() {

            return getContent().length();
        }

        /*
         * (non-Javadoc)
         * 
         * @see org.eclipse.jface.fieldassist.IContentProposal#getDescription()
         */
        public String getDescription() {

            String message = Messages.getString("JavaGlobalVariableProposal.Description"); //$NON-NLS-1$
            message += Messages.getString("JavaGlobalVariableProposal.VariableName"); //$NON-NLS-1$

            MessageFormat format = new MessageFormat(message);
            Object[] args = new Object[] { desc, code };
            return format.format(args);
        }

        /*
         * (non-Javadoc)
         * 
         * @see org.eclipse.jface.fieldassist.IContentProposal#getLabel()
         */
        public String getLabel() {

            return display;
        }

    }
}
