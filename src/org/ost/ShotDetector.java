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

import java.nio.ByteBuffer;

/**
 * Base class for different shot detection algorithms.
 *
 */
public abstract class ShotDetector {
    
    /**
     * Number of samples per second
     */
    protected final int m_sampleRate;
    
    /**
     * Size of the audio samples in bits
     */
    protected final int m_sampleSizeInBits;
    
    /**
     * Size of the audio samples in bytes
     */
    protected final int m_sampleSizeInBytes;
    
    /**
     * Number of audio samples per millisecond at the current sample rate
     */
    protected final double m_samplesPerMillisecond;
    
    /**
     * Current sample in the timeline that was last received from the capturer.
     */
    protected long m_currentSample = 0;
    /**
     * 
     * @param format Format of the audio that will be provided to this detector
     * @param configProperties Application config properties
     */
    
    public ShotDetector(int sampleRate, int sampleSizeInBits) {
        m_sampleRate = sampleRate;
        m_sampleSizeInBits = sampleSizeInBits;
        m_sampleSizeInBytes = m_sampleSizeInBits / 8;
        m_samplesPerMillisecond = (m_sampleRate / 1000);
    }
    
    public final ShotEvent[] processAudio(final ByteBuffer b) {
        final int length = b.remaining();
        final ShotEvent events[] = p_processAudio(b);
        m_currentSample += (length / m_sampleSizeInBytes);
        return events;
    }
    
    abstract protected ShotEvent[] p_processAudio(final ByteBuffer b);

}