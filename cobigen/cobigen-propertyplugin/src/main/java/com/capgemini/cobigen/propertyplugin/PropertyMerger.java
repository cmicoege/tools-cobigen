/*******************************************************************************
 * Copyright © Capgemini 2013. All rights reserved.
 ******************************************************************************/
package com.capgemini.cobigen.propertyplugin;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.capgemini.cobigen.extension.IMerger;
import com.capgemini.cobigen.util.SystemUtil;

/**
 * The {@link PropertyMerger} merges two property files. One being provided as the base file and the second
 * being provided as the file contents of the
 * 
 * @author mbrunnli (11.03.2013)
 * @uthor sbasnet(06.05.2014)
 */
public class PropertyMerger implements IMerger {

    /**
     * Merger Type to be registered
     */
    private String type;

    /**
     * The conflict resolving mode
     */
    private boolean patchOverrides;

    /**
     * Creates a new {@link PropertyMerger}
     * 
     * @param patchOverrides
     *            if <code>true</code>, conflicts will be resolved by using the patch contents<br>
     *            if <code>false</code>, conflicts will be resolved by using the base contents<br>
     * @author sbasnet (26.04.2014)
     * @param type
     */
    public PropertyMerger(String type, boolean patchOverrides) {
        this.type = type;
        this.patchOverrides = patchOverrides;
    }

    /**
     * {@inheritDoc}
     * 
     * @author mbrunnli (08.04.2014)
     */
    @Override
    public String getType() {
        return type;
    }

    /**
     * {@inheritDoc}
     * 
     * @throws IOException
     *             if the base file could not be read or written
     * @author mbrunnli (11.03.2013)
     */
    public String merge(File base, String patch, String targetCharset) throws IOException {
        Properties baseProperties = new Properties();
        baseProperties.load(new InputStreamReader(new FileInputStream(base), targetCharset));
        Properties patchProperties = new Properties();
        patchProperties.load(new ByteArrayInputStream(patch.getBytes()));
        Set<Object> conflicts = getConflictingProperties(baseProperties, patchProperties);
        BufferedReader br =
            new BufferedReader(new InputStreamReader(new FileInputStream(base), targetCharset));
        return concatContents(conflicts, br, patch);
    }

    /**
     * Concatenates the contents of the base files and the patch leaving out conflicting properties of the
     * patch
     * 
     * @param conflicts
     *            a {@link Set} of conflicting properties
     * @param baseFileReader
     *            {@link BufferedReader} reading the base file
     * @param patch
     *            which should be applied
     * @return merged file contents
     * @throws IOException
     *             if the base file could not be read oder accessed
     * @author mbrunnli (11.03.2013)
     */
    private String concatContents(Set<Object> conflicts, BufferedReader baseFileReader, String patch)
        throws IOException {
        String lineSeparator = SystemUtil.LINE_SEPARATOR;

        List<String> recordedComments = new LinkedList<String>();
        Map<String, String> collection = new HashMap<String, String>();
        String line;
        boolean lastLineWasComment = false;
        int count = 0; // count is used below to maintain uniqueness in the hash
        // key-value pair
        while ((line = baseFileReader.readLine()) != null && !line.isEmpty()) {
            line = line.trim();
            // adding key of the respective value to the collection
            if (line.startsWith("#")) {
                collection.put("recordedComments" + count, lineSeparator + line);
                if (lastLineWasComment) {
                    String lastComment = recordedComments.remove(recordedComments.size() - 1);
                    recordedComments.add(lastComment + lineSeparator + line);
                } else {
                    lastLineWasComment = true;
                    recordedComments.add(line);
                }
            } else {
                lastLineWasComment = false;
                if (!line.trim().isEmpty()) {
                    collection.put(line.substring(0, line.indexOf("=")), lineSeparator + line);
                }
            }
            count++;
        }
        baseFileReader.close();
        Pattern p = Pattern.compile("([^=\\s]+)\\s*=.*");
        Matcher m;
        String lastObservedComment = null;
        int observedEmptyLines = 0;
        count = 0;
        for (String patchLine : patch.split(lineSeparator)) {
            m = p.matcher(patchLine);
            if (m.matches()) {
                // no conflicts
                if (!conflicts.contains(m.group(1))) {
                    collection.put(m.group(1), lineSeparator + patchLine);
                    observedEmptyLines = 0;
                } else {
                    if (patchOverrides) { // override the original by patch file
                        // patchLine;
                        collection.put(m.group(1), lineSeparator + patchLine);
                        observedEmptyLines = 0;
                    }
                }
            } else if (patchLine.startsWith("#")) {
                // record comment over multiple lines
                if (lastObservedComment != null) {
                    lastObservedComment += lineSeparator + patchLine;
                    collection.put("lastObservedComment" + count, lineSeparator + lastObservedComment);
                } else {
                    lastObservedComment = patchLine;
                    collection.put("lastObservedComment" + count, lineSeparator + lastObservedComment);
                }
            } else {
                if (lastObservedComment == null && patchLine.trim().isEmpty()) {
                    observedEmptyLines++;
                } else {
                    // check if comment exists in base file and write the
                    // comment if not so
                    if (lastObservedComment != null && !recordedComments.contains(lastObservedComment)) {
                        for (int i = 0; i < observedEmptyLines; i++) {
                            collection.put("_blank" + count, lineSeparator);
                        }
                        collection.put("lastObservedComment" + count, lineSeparator + lastObservedComment);
                    }
                    lastObservedComment = null;
                    observedEmptyLines = 0;

                    if (!patchLine.trim().isEmpty()) {
                        // patchLine;
                        collection.put("patchLineNotEmpty" + count, lineSeparator + patchLine);
                        observedEmptyLines = 0;
                    }
                }
            }
            count++;
        }
        String out = ""; // initializing to use the same variable again
        for (String tempOut : new ArrayList<String>(collection.values())) {
            out += tempOut;
        }
        return out;
    }

    /**
     * Returns all conflicting properties of the two files
     * 
     * @param baseProperties
     *            {@link Properties} defined in the base file
     * @param patchProperties
     *            {@link Properties} defined in the patch
     * @return all conflicting properties of the two files
     * @author mbrunnli (11.03.2013)
     */
    private Set<Object> getConflictingProperties(Properties baseProperties, Properties patchProperties) {
        HashSet<Object> conflicts = new HashSet<Object>();
        for (Object key : baseProperties.keySet()) {
            if (patchProperties.containsKey(key)) {
                conflicts.add(key);
            }
        }
        returnKeyValue(patchProperties);
        return conflicts;
    }

    /**
     * Returns key-value pair of the properties file
     * 
     * @param properties
     * @return key-value
     */
    private Map<String, String> returnKeyValue(Properties properties) {
        Map<String, String> patch = new HashMap<String, String>();
        String key[] = new String[properties.size()];
        String value[] = new String[properties.size()];
        int count = 0;
        for (Object propKey : properties.keySet()) {
            key[count] = propKey + "";
            count++;
        }
        count = 0;
        for (Object propValue : properties.values()) {
            value[count] = propValue + "";
            count++;
        }
        for (int i = 0; i < count; i++) {
            patch.put(key[i], value[i]);
        }
        return patch;
    }
}