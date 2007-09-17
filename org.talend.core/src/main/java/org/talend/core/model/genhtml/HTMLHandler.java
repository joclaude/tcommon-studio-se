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
package org.talend.core.model.genhtml;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URL;
import java.util.Map;

import org.talend.commons.exception.ExceptionHandler;

/**
 * This class is used for transfering XML file to HTML file.
 * 
 * $Id: HTMLGenerator.java 2007-3-7,下午04:42:22 ftang $
 * 
 */
public class HTMLHandler {

    /**
     * This method is used for generating HTML file base on given folder, job name and xsl file name.
     * 
     * @param tempFolderPath a string
     * @param jobName a string
     * @param htmlFileMap
     * @param xslFileName a string
     */
    public static void generateHTMLFile(String tempFolderPath, String xslFilePath, String xmlFilePath,
            String htmlFilePath, Map<String, URL> htmlFileMap) {

        Map<String, URL> nodeHTMLMap = htmlFileMap;
        generateHTMLFile(tempFolderPath, xslFilePath, xmlFilePath, htmlFilePath);

        File originalHtmlFile = new File(htmlFilePath);

        if (!originalHtmlFile.exists()) {
            return;
        }

        BufferedReader mainHTMLReader = null;
        BufferedWriter newMainHTMLWriter = null;
        BufferedReader externalNodeHTMLReader = null;
        File newMainHTMLFile = null;
        try {

            mainHTMLReader = new BufferedReader(new FileReader(originalHtmlFile));

            newMainHTMLFile = new File(htmlFilePath + "temp");

            newMainHTMLWriter = new BufferedWriter(new FileWriter(newMainHTMLFile));
            String lineStr = "";
            while ((lineStr = mainHTMLReader.readLine()) != null) {
                newMainHTMLWriter.write(lineStr);
                for (String key : htmlFileMap.keySet()) {
                    String compareStr = "<!--" + key + "ended-->"; // tMap_1ended-->
                    if (lineStr.indexOf(compareStr) != -1) {
                        File externalNodeHTMLFile = new File(nodeHTMLMap.get(key).getPath());
                        externalNodeHTMLReader = new BufferedReader(new FileReader(externalNodeHTMLFile));
                        String tempLineStr = "";
                        while ((tempLineStr = externalNodeHTMLReader.readLine()) != null) {
                            newMainHTMLWriter.write(tempLineStr);
                        }
                        // resolved the problem:the tmp folder can't be deleted.
                        if (externalNodeHTMLReader != null) {
                            externalNodeHTMLReader.close();
                        }
                    }
                    // htmlFileMap.remove(key);
                }
            }

        } catch (Exception e) {
            ExceptionHandler.process(e);
        } finally {
            try {
                if (mainHTMLReader != null) {
                    mainHTMLReader.close();
                }
                if (newMainHTMLWriter != null) {
                    newMainHTMLWriter.close();
                }
                // if (externalNodeHTMLReader != null) {
                // externalNodeHTMLReader.close();
                // }
            } catch (IOException e) {
                ExceptionHandler.process(e);
            }

            originalHtmlFile.delete();
            boolean isWorked = newMainHTMLFile.renameTo(new File(htmlFilePath));
            // System.out.println("isWorked= " + isWorked);

            // copy(htmlFilePath + "temp", htmlFilePath);
            //            
            // System.out.println("tempFilePath:" + htmlFilePath + "temp");
            // System.out.println("htmlFilePath:" + htmlFilePath);
        }
    }

    /**
     * This method is used for generating HTML file base on given folder, job name and xsl file name.
     * 
     * @param tempFolderPath a string
     * @param jobNameOrComponentName a string
     * @param externalNodeHTMLList
     * @param xslFileName a string
     */
    public static void generateHTMLFile(String tempFolderPath, String xslFilePath, String xmlFilePath,
            String htmlFilePath) {
        FileOutputStream output = null;
        Writer writer = null;
        try {
            File xmlFile = new File(xmlFilePath);
            File xsltFile = new File(xslFilePath);

            javax.xml.transform.Source xmlSource = new javax.xml.transform.stream.StreamSource(xmlFile);
            javax.xml.transform.Source xsltSource = new javax.xml.transform.stream.StreamSource(xsltFile);

            output = new FileOutputStream(htmlFilePath);

            // Note that if the are chinese in the file, should set the encoding
            // type to "UTF-8", this is caused by DOM4J.
            writer = new OutputStreamWriter(output, "UTF-8");

            javax.xml.transform.Result result = new javax.xml.transform.stream.StreamResult(writer);

            // create an instance of TransformerFactory
            javax.xml.transform.TransformerFactory transFact = javax.xml.transform.TransformerFactory.newInstance();

            javax.xml.transform.Transformer trans = transFact.newTransformer(xsltSource);

            trans.transform(xmlSource, result);

        } catch (Exception e) {
            ExceptionHandler.process(e);
        }

        finally {
            try {
                if (output != null) {
                    output.close();
                }
            } catch (Exception e) {
                ExceptionHandler.process(e);
            }
            try {
                if (writer != null) {
                    writer.close();
                }

            } catch (Exception e1) {
                ExceptionHandler.process(e1);
            }

        }
    }

    /**
     * This methos is used for coping file from one place to the other.
     * 
     * @param srcFilePath
     * @param destFilePath
     * @throws Exception
     */
    private static void copy(String srcFilePath, String destFilePath) {
        FileInputStream input = null;
        FileOutputStream output = null;
        try {
            byte[] bytearray = new byte[512];
            int len = 0;
            input = new FileInputStream(srcFilePath);
            output = new FileOutputStream(destFilePath);
            while ((len = input.read(bytearray)) != -1) {
                output.write(bytearray, 0, len);
            }

        } catch (Exception fe) {
            ExceptionHandler.process(fe);
        }

        finally {
            if (input != null) {
                try {
                    input.close();
                } catch (Exception e) {
                    ExceptionHandler.process(e);
                }
            }
            if (output != null) {
                try {
                    output.close();
                } catch (Exception e) {
                    ExceptionHandler.process(e);
                }
            }
        }
    }
}
