// ============================================================================
//
// Copyright (C) 2006-2013 Talend Inc. - www.talend.com
//
// This source code is available under agreement available at
// %InstallDIR%\features\org.talend.rcp.branding.%PRODUCTNAME%\%PRODUCTNAME%license.txt
//
// You should have received a copy of the agreement
// along with this program; if not, write to Talend SA
// 9 rue Pages 92150 Suresnes, France
//
// ============================================================================
package org.talend.designer.core;

import org.talend.core.model.process.IProcess;
import org.talend.core.model.properties.Item;
import org.talend.core.model.repository.IRepositoryViewObject;

/**
 * This interface provides some methods to do the conversion between process and another process.
 */
public interface IProcessConvertService {

    public IProcess getProcessFromItem(Item item, boolean loadScreenshots);

    /**
     * Converts the current child process extends process to process. For example, convert m/r process to common
     * process.Added by Marvin Wang on Feb 18, 2013.
     * 
     * @param item
     * @param repViewObject
     * @return
     */
    Item convertToProcess(Item item, IRepositoryViewObject repViewObject);

    /**
     * Converts process to the current process which extends process. Added by Marvin Wang on Feb 18, 2013.
     * 
     * @param item
     * @param repViewObject
     * @return
     */
    Item convertFromProcess(Item item, IRepositoryViewObject repViewObject);

    /**
     * Returns a boolean value to indicate if the original item is delelted. Added by Marvin Wang on Jan 31, 2013.
     * 
     * @return
     */
    boolean isOriginalItemDeleted();

    /**
     * Returns a boolean value to indicate if the new item is converted sucessfully. Added by Marvin Wang on Jan 31,
     * 2013.
     * 
     * @return
     */
    boolean isNewItemCreated();

    /**
     * Returns the type of the instance of {@link ProcessConverterType}. Added by Marvin Wang on Feb 18, 2013.
     * 
     * @return
     */
    ProcessConverterType getConverterType();

}
