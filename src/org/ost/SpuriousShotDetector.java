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
import java.util.Timer;
import java.util.TimerTask;
import java.util.Random;

/**
 * A shot detector that generates a shot a 3 - 10 seconds
 *
 */
public class SpuriousShotDetector extends ShotDetector {
    
    private final Timer m_timer = new Timer(true);
    private final Random m_random = new Random();
    
    private int m_shotIndex = 0;
    private long m_lastShotTime = 0;
    
    private long m_sampleCount;
    
    private volatile TimerTask m_currentTask = null;
    
    private final Runnable m_runnable = new Runnable() {
        @Override
        public void run() {
            if (m_shouldContinue) {
                m_shouldGenerateShot = true;
                final long delay = (long)(m_random.nextDouble() * 3000.0);
                m_currentTask = new TimerTask() {
                    @Override
                    public void run() {
                        m_runnable.run();
                    }
                };
                m_timer.schedule(m_currentTask, delay);
            }
        }
    };
    
    private volatile boolean m_shouldGenerateShot = false;
    private volatile boolean m_shouldContinue = true;
    
    public SpuriousShotDetector(int sampleRate, int sampleSizeInBits) {
        super(sampleRate, sampleSizeInBits);
        if (sampleSizeInBits != 16) {
            throw new IllegalArgumentException("Only support 16 bit samples");
        }
    }
    
    public void stop() {
        if (m_currentTask != null) {
            m_currentTask.cancel();
        }
        m_shouldContinue = false;
        m_currentTask = null;
        m_shotIndex = 0;
        m_lastShotTime = 0;
        m_sampleCount = 0;
    }
    
    public void start() {
        m_shouldContinue = true;
        final long delay = (long)(m_random.nextDouble() * 1000.0);
        m_currentTask = new TimerTask() {
            @Override
            public void run() {
                m_runnable.run();
            }
        };
        m_timer.schedule(m_currentTask, delay);
    }

    @Override
    protected ShotEvent[] p_processAudio(ByteBuffer b) {
        ShotEvent retval[] = null;
        m_sampleCount += b.remaining() / 2;
        if (m_shouldGenerateShot) {
            m_shouldGenerateShot = false;
            final long shotTime = (long)(m_sampleCount / m_samplesPerMillisecond);
            retval = new ShotEvent[] {
                    new ShotEvent( 
                            m_shotIndex++, 
                            shotTime,
                            shotTime - m_lastShotTime)
                };
            m_lastShotTime = shotTime;
            
        } else {
            retval = new ShotEvent[0];
        }
        return retval;
    }

}
