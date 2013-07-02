/* This file is part of Open Shot Timer.
 * Copyright (C) 2009-10 Ariel Weisberg
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.ost;

public class ShotEvent {
    
    /**
     * The number of this shot in the timeline
     */
    public final int m_shotNum;
    
    /**
     * Time the shot occured in milliseconds since the beginning of the timeline
     */
    public final long m_time;
    
    /**
     * Time between this shot and the last shot (or the beginning of the timeline for the first shot).
     * Also in milliseconds
     */
    public final long m_split;
    
    
    public ShotEvent(int shotNum, long time, long prevTime) {
        m_shotNum = shotNum;
        m_time = time;
        m_split = prevTime;
    }
}