/* This file is part of Open Shot Timer.
 * Copyright (C) 2009 Ariel Weisberg
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
import java.util.ArrayList;
import java.nio.ByteOrder;

/**
 * With a .45 I found that it spikes at for 18 milliseconds with a dense number of samples and then drops off. 
 * When the first big sample comes in count it as a shot and then stop listening for .12 seconds.
 *
 */
public class AmplitudeSpikeShotDetector extends ShotDetector {

    private short m_shotDetectionThreshhold = 32000;
    
    private long m_ignoreUntilSample = -1;
    
    private long m_lastShotSample = 0;
    
    private final int m_samplesAboveThresholdRequired;
    
    private int shotCount;
    
    public AmplitudeSpikeShotDetector(
            int sampleRate,
            int sampleSizeInBits,
            int samplesAboveThresholdRequired) {
        super(sampleRate, sampleSizeInBits);
        m_samplesAboveThresholdRequired = samplesAboveThresholdRequired + 1;
        if (sampleSizeInBits != 16) {
            throw new IllegalArgumentException("Only support 16 bit samples");
        }
        m_ignoreUntilSample = (long)(m_samplesPerMillisecond * 1000);
    }
    
    @Override
    protected ShotEvent[] p_processAudio(ByteBuffer buffer) {
        final int length = buffer.remaining();
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        ArrayList<ShotEvent> shotEvents = new ArrayList<ShotEvent>();
        int sampleMax = 0;
        
        if (m_ignoreUntilSample > 0) {
            int bufferLength = (length / m_sampleSizeInBytes);
            if (m_currentSample + bufferLength < m_ignoreUntilSample) {
                return new ShotEvent[0];
            } else {
                buffer.position((int)(m_ignoreUntilSample - m_currentSample));
                m_ignoreUntilSample = -1;
            }
        }
        
        int samplesAboveThreshold = 0;
        while (buffer.hasRemaining()) {
            short sample = (short)Math.abs((int)buffer.getShort());
            sampleMax = Math.max( sample, sampleMax);
            if (sample > m_shotDetectionThreshhold) {
                samplesAboveThreshold++;
                if (samplesAboveThreshold < m_samplesAboveThresholdRequired) {
                    continue;
                }
                samplesAboveThreshold = 0;
                final long currentSample =  m_currentSample + buffer.position();
                m_ignoreUntilSample = currentSample + (long)(m_samplesPerMillisecond * 120);
                final long shotTime = (long)(currentSample / m_samplesPerMillisecond);
                final long split = (long)((currentSample - m_lastShotSample) / m_samplesPerMillisecond);
                m_lastShotSample = currentSample;
                shotEvents.add(new ShotEvent(++shotCount, shotTime, split));
                final int remaining = buffer.remaining();
                final long currentSamplePlusRemaining = currentSample + remaining;
                if( currentSamplePlusRemaining > m_ignoreUntilSample ) {
                    buffer.position((int)(buffer.position() + (m_ignoreUntilSample - currentSample)));
                    m_ignoreUntilSample = -1;
                } else {
                    break;
                }
            }
        }
        return shotEvents.toArray(new ShotEvent[shotEvents.size()]);
    }
    
}