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

import java.util.ArrayList;
import java.util.Random;
import java.nio.ByteBuffer;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.SoundPool;
import android.media.MediaRecorder.AudioSource;
import android.media.AudioManager;
import android.content.Context;
import android.content.SharedPreferences;
import org.ost.OpenShotTimer.Preferences;

public class ShotString {
    
    private static class Pair {
        public final int a;
        public final int b;
        public Pair(int a, int b) {
            this.a = a;
            this.b = b;
        }
    }
    
    private final Random m_random = new Random(System.currentTimeMillis());
    
    private ByteBuffer m_readBuffer = ByteBuffer.allocateDirect(4096);
    
    private final AudioManager m_audioManager;
    
    private int m_systemStreamVolume = 100;
    
    private final int m_systemStream = AudioManager.STREAM_ALARM;
    
    public void setSystemStreamVolume(int volume) {
        m_systemStreamVolume = volume;
        m_audioManager.setStreamVolume(m_systemStream, volume, 0);
    }
    
    public int getSystemStreamVolume() {
        return m_systemStreamVolume;
    }
    
    public int getSystemstream() {
        return m_systemStream;
    }
    
    public int getSystemStreamMaxVolume() {
        return m_audioManager.getStreamMaxVolume(m_systemStream);
    }
    
    private final SoundPool m_pool = new SoundPool(1, AudioManager.STREAM_ALARM, 0);
    
    private int m_buzzerVolume = 100;
    
    public int getBuzzerVolume() { return m_buzzerVolume; }
    public void setBuzzerVolume(int volume) { 
        m_buzzerVolume = volume;
        m_settings.edit().putInt(Preferences.BUZZER_VOLUME.name(), volume).commit();
    }
    
    private int m_buzzerDelay = 0;
    
    public int getBuzzerDelay() { return m_buzzerDelay; }
    public void setBuzzerDelay(int delay) { 
        m_buzzerDelay = delay;
        m_settings.edit().putInt(Preferences.BUZZER_DELAY.name(), delay).commit();
    }
    
    private int m_sensitivity = 4;
    
    public int getSensitivity() { return m_sensitivity; }
    public void setSensitivity(int sensitivity) { 
        m_sensitivity = sensitivity;
        m_settings.edit().putInt(Preferences.SENSITIVITY.name(), sensitivity).commit();
    }
    
    private boolean m_randomStart = false;
    public boolean getRandomStart() { return m_randomStart; }
    public void setRandomStart(boolean randomStart) { 
        m_randomStart = randomStart;
        m_settings.edit().putBoolean(Preferences.RANDOM_START.name(), randomStart).commit();
    }
    
    /**
     * List of parties interested in new ShotEvents
     */
    private final ArrayList<ShotEventListener> m_shotEventListeners = new ArrayList<ShotEventListener>();
    
    private int m_sampleRate = 8000;
    
    private int m_minBufferSize = -1;

    private ShotDetector m_shotDetector = null;
    
    private AudioRecord m_record = null;
    
    private AudioPuller m_audioPuller = null;
    
    private Thread m_audioPullerThread = null;
    
    private final ExceptionHandler m_exceptionHandler;
    
    private final int m_buzzerId;
    
    private final SharedPreferences m_settings;
    
    private class AudioPuller implements Runnable {
        private volatile boolean m_shouldContinue = true;
        private volatile boolean m_fakeIt = false;
        
        @Override
        public void run() {
            try {
                if (m_fakeIt) {
                    if (doBuzz()) {
                        fakeAudioLoop();
                    }
                } else {
                    if (doBuzz()) {
                        realAudioLoop();
                    } else {
                        m_record.release();
                        m_record = null;
                    }
                }
                
                synchronized (ShotString.this) {
                    m_audioPuller = null;
                    m_audioPullerThread = null;
                    ShotString.this.notifyAll();
                }
            } catch (Throwable t) {
                m_exceptionHandler.handleException(t);
            }
        }
        
        private final boolean doBuzz() {
            final float buzzerVolume = m_buzzerVolume / (float)100.0;
            if (m_buzzerDelay > 0) {
                long sleepQuantity = m_buzzerDelay * 1000;
                
                if (m_randomStart && m_buzzerDelay > 2) {
                    int delay = m_random.nextInt((m_buzzerDelay - 2) * 1000);
                    sleepQuantity = 2000 + delay;
                }
                
                try {
                    Thread.sleep(sleepQuantity);
                } catch (InterruptedException e) {
                    return false;
                }
            }
            m_pool.play(m_buzzerId, buzzerVolume, buzzerVolume, 1, 3, (float)1.0);
            return true;
        }
        
        private void realAudioLoop() {
            m_record.startRecording();
            while (m_shouldContinue) {
                m_readBuffer.clear();
                final int read = m_record.read(m_readBuffer, m_readBuffer.remaining());
                if (read < 0) {
                    throw new RuntimeException("AudioRecord returned and error on read");
                }
                m_readBuffer.position(0);
                m_readBuffer.limit(read);
                ShotEvent events[] = m_shotDetector.processAudio(m_readBuffer);
                for (ShotEvent e : events) {
                    for (ShotEventListener l : m_shotEventListeners) {
                        l.shotDetected(m_shotDetector, e);
                    }
                }
                m_readBuffer.clear();
            }
            
            m_record.stop();
            m_record.release();
            m_record = null;
        }
        
        private void fakeAudioLoop() {
            final double samplesPerMillisecond = (m_sampleRate / 1000.0);
            long millisecondsToSleep = (long)((m_readBuffer.capacity() / 2) / samplesPerMillisecond);
            m_readBuffer.limit(m_readBuffer.capacity());
            while (m_shouldContinue) {
                try {
                    Thread.sleep(millisecondsToSleep);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                ShotEvent events[] = m_shotDetector.processAudio(m_readBuffer);
                for (ShotEvent e : events) {
                    for (ShotEventListener l : m_shotEventListeners) {
                        l.shotDetected(m_shotDetector, e);
                    }
                }
                m_readBuffer.position(0);
            }
        }
    }
    
    public ShotString(
            ExceptionHandler handler,
            Context context,
            SharedPreferences settings,
            AudioManager manager) throws Exception {
        assert(handler != null);
        m_settings = settings;
        m_audioManager = manager;
        
        m_systemStreamVolume = manager.getStreamVolume(m_systemStream);
        m_randomStart = m_settings.getBoolean(Preferences.RANDOM_START.name(), false);
        m_sensitivity = m_settings.getInt(Preferences.SENSITIVITY.name(), 4);
        m_buzzerVolume = m_settings.getInt(Preferences.BUZZER_VOLUME.name(), 100);
        m_buzzerDelay = m_settings.getInt(Preferences.BUZZER_DELAY.name(), 0);
        
        final Pair p = probeHardware();
        if (p.a > 0) {
            m_sampleRate = p.a;
            m_minBufferSize = p.b;
            m_readBuffer = ByteBuffer.allocateDirect(m_minBufferSize);
        }
        m_exceptionHandler = handler;
        System.out.println("Going to request sample rate " + m_sampleRate + " with minBufferSize " + m_minBufferSize);
        m_buzzerId = m_pool.load(context, R.raw.buzz, 1);
    }

    public synchronized boolean start() {
        terminateAudioSystem();
        
        boolean success = true;
        
        if (m_shotDetector != null) {
            if (m_shotDetector instanceof SpuriousShotDetector) {
                ((SpuriousShotDetector)m_shotDetector).stop();
            }
        }
        
        int recordState = AudioRecord.ERROR;
        if (m_minBufferSize > 0) {
            m_record = new AudioRecord(
                    AudioSource.MIC,
                    m_sampleRate,
                    AudioFormat.CHANNEL_CONFIGURATION_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    m_minBufferSize * 5);
            recordState = m_record.getState();
        }
        
        m_audioPuller = new AudioPuller();
        if (recordState != AudioRecord.STATE_INITIALIZED){
            System.out.println("Failed to initialized AudioRecord, error code is " + recordState);
            m_audioPuller.m_fakeIt = true;
            success = false;
            m_shotDetector = new SpuriousShotDetector(m_sampleRate, 16);
            ((SpuriousShotDetector)m_shotDetector).start();
        } else {
            m_shotDetector = new AmplitudeSpikeShotDetector(m_sampleRate, 16, m_sensitivity);
        }
        
        m_audioPullerThread = new Thread(m_audioPuller);
        m_audioPullerThread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {

            @Override
            public void uncaughtException(Thread thread, Throwable ex) {
                m_exceptionHandler.handleException(ex);
            }
        
        });
        m_audioPullerThread.start();
        
        return success;
    }
    
    private synchronized void terminateAudioSystem() {
        if (m_audioPuller != null) {
            m_audioPuller.m_shouldContinue = false;
            m_audioPullerThread.interrupt();
            while (m_audioPuller != null) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
    
    public synchronized void end() {
        terminateAudioSystem();
        
        if (m_shotDetector != null) {
            if (m_shotDetector instanceof SpuriousShotDetector) {
                ((SpuriousShotDetector)m_shotDetector).stop();
            }
        }
    }
    
    public synchronized void close() {
        terminateAudioSystem();
        m_pool.release();
        if (m_shotDetector != null) {
            if (m_shotDetector instanceof SpuriousShotDetector) {
                ((SpuriousShotDetector)m_shotDetector).stop();
            }
        }
    }
    
    public void addShotEventListener(ShotEventListener listener) {
        m_shotEventListeners.add(listener);
    }
    
    public void removeShotEventListener(ShotEventListener listener) {
        m_shotEventListeners.remove(listener);
    }
    
    private Pair probeHardware() throws Exception {
        final ByteBuffer b = ByteBuffer.allocateDirect(1024);
        
        final int sampleRates[] = new int [] { 44800, 44100, 22000, 22050, 11025, 11000, 8000 };
        
        final ArrayList<Pair> usable16SampleRates = new ArrayList<Pair>();
        for (final int rate : sampleRates) {
            final int minBufferSize = AudioRecord.getMinBufferSize( 
                    rate, 
                    AudioFormat.CHANNEL_CONFIGURATION_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            if (minBufferSize > 0) {
                usable16SampleRates.add(new Pair(rate, minBufferSize));
            }
        }
        
        for (final Pair p : usable16SampleRates) {
            final AudioRecord r =
                new AudioRecord(
                        AudioSource.MIC,
                        p.a,
                        AudioFormat.CHANNEL_CONFIGURATION_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        p.b);
            if (r.getState() == AudioRecord.STATE_INITIALIZED) {
                r.startRecording();
                b.clear();
                final int read = r.read(b, b.remaining());
                if (read == b.capacity()) {
                    r.release();
                    return p;
                }
            }
            
            r.release();
        }
        return new Pair(-1, -1);
    }
}
