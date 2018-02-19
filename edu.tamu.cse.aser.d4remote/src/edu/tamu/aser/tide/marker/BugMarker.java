/*
 * FindBugs Eclipse Plug-in.
 * Copyright (C) 2003 - 2004, Peter Friese
 * Copyright (C) 2005, University of Maryland
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */

package edu.tamu.aser.tide.marker;

import org.eclipse.jdt.core.IJavaElement;

/**
 * Marker ids for the findbugs.
 *
 * @author Peter Friese
 * @version 1.0
 * @since 13.08.2003
 */
public interface BugMarker {
    /**
     * Marker type for FindBugs warnings. (should be the plugin id concatenated
     * with ".findbugsMarker")
     */
    public static final String NAME = "edu.umd.cs.findbugs.plugin.eclipse.findbugsMarker";

    public static final String TYPE_SCARIEST = "edu.umd.cs.findbugs.plugin.eclipse.findbugsMarkerScariest";

    public static final String TYPE_SCARY = "edu.umd.cs.findbugs.plugin.eclipse.findbugsMarkerScary";

    public static final String TYPE_TROUBLING = "edu.umd.cs.findbugs.plugin.eclipse.findbugsMarkerTroubling";
    public static final String TYPE_OF_CONCERN = "edu.umd.cs.findbugs.plugin.eclipse.findbugsMarkerOfConcern";


    /**
     * Marker attribute recording the bug type (specific bug pattern).
     */
    public static final String BUG_TYPE = "BUGTYPE";

    /**
     * Marker attribute recording the pattern type (more general pattern group).
     */
    public static final String PATTERN_TYPE = "PATTERNTYPE";


    /**
     * Marker attribute recording the bug rank (as integer).
     */
    public static final String RANK = "RANK";

    /**
     * Marker attribute recording the bug priority (==confidence) (as integer).
     */
    public static final String PRIO_AKA_CONFIDENCE = "CONFIDENCE";


    /**
     * Marker attribute recording the unique id of the BugInstance in its
     * BugCollection.
     */
    public static final String UNIQUE_ID = "FINDBUGS_UNIQUE_ID";

    /**
     * Marker attribute recording the unique Java handle identifier, see
     * {@link IJavaElement#getHandleIdentifier()}
     */
    public static final String UNIQUE_JAVA_ID = "UNIQUE_JAVA_ID";

    /**
     * Marker attribute recording the FindBugs detector plugin id
     */
    public static final String DETECTOR_PLUGIN_ID = "DETECTOR_PLUGIN_ID";

    /**
     * Marker attribute recording the primary (first) line of the BugInstance in
     * its BugCollection (in case same bug reported on many lines).
     */
    public static final String PRIMARY_LINE = "PRIMARY_LINE";

    /**
     * Marker attribute recording the name and timestamp of the first version.
     */
    public static final String FIRST_VERSION = "FIRST_VERSION";

    /**
     * @see BugRankCategory
     */
    enum MarkerRank {
         Scariest("buggy-tiny-green.png"),
    	 Scary("buggy-tiny-orange.png");


        private final String icon;

        MarkerRank(String icon) {
            this.icon = icon;
            }


        public String iconName() {
            return icon;
        }
    }
}
