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

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.Context;
import android.os.Bundle;
import android.view.View.OnClickListener;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.CheckBox;
import android.app.AlertDialog;
import android.media.AudioManager;
import android.content.DialogInterface;
import java.util.ArrayList;
import java.io.StringWriter;
import java.io.PrintWriter;

public class OpenShotTimer extends Activity implements ExceptionHandler {

    private ShotString m_string; 

    private final ArrayList<View> m_shotEventViews = new ArrayList<View>();
    
    public static final String PREFS_NAME = "OST";
    
    private SharedPreferences m_settings;
    
    private long startTime = -1;
    
    private final long maxStringTime = 600000;
    
    enum Preferences {
        RANDOM_START,
        SENSITIVITY,
        BUZZER_VOLUME,
        BUZZER_DELAY,
    };
    
    private final Thread stringKiller = new Thread() {
        @Override
        public void run() {
            while(true) {
                try {
                    Thread.sleep(20000);
                } catch (InterruptedException e) {
                    handleException(e);
                }
                if (startTime == -1) {
                    continue;
                }
                if (System.currentTimeMillis() - startTime > maxStringTime) {
                    try {
                        m_string.end();
                    } catch (Throwable t) {
                        handleException(t);
                    }
                }
            }
        }
    };

    private void constructShotString() throws Exception {
        m_string = new ShotString(
                this,
                this,
                m_settings,
                (AudioManager)getSystemService(Context.AUDIO_SERVICE));
        m_string.addShotEventListener(new ShotEventListener() {

            @Override
            public void shotDetected(final ShotDetector detector,
                    final ShotEvent event) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            handleShotDetected(event);
                        } catch (Throwable t) {
                            handleException(t);
                        }
                    }

                    private void handleShotDetected(final ShotEvent event) {
                        System.out.println("Recieved shot " + event.m_shotNum
                                + " at " + event.m_time + " with split "
                                + event.m_split);
                        final TableRow r = new TableRow(OpenShotTimer.this);
                        r.setLayoutParams(new LayoutParams(
                                LayoutParams.FILL_PARENT,
                                LayoutParams.WRAP_CONTENT));

                        final TextView shotNumberView = new TextView(
                                OpenShotTimer.this);
                        shotNumberView.setText(Integer
                                .toString(event.m_shotNum));
                        r.addView(shotNumberView);

                        final TextView shotTimeView = new TextView(
                                OpenShotTimer.this);
                        final StringWriter shotTimeSW = new StringWriter();
                        final PrintWriter shotTimePW = new PrintWriter(
                                shotTimeSW);
                        shotTimePW.printf("%.2f", event.m_time / 1000.0)
                                .flush();
                        final String shotTime = shotTimeSW.toString();
                        shotTimeView.setText(shotTime);
                        r.addView(shotTimeView);

                        final TextView shotSplitView = new TextView(
                                OpenShotTimer.this);
                        final StringWriter shotSplitSW = new StringWriter();
                        final PrintWriter shotSplitPW = new PrintWriter(
                                shotSplitSW);
                        shotSplitPW.printf("%.2f", event.m_split / 1000.0)
                                .flush();
                        final String shotSplit = shotSplitSW.toString();
                        shotSplitView.setText(shotSplit);
                        r.addView(shotSplitView);

                        m_shotEventViews.add(r);

                        final ViewGroup shotTable = (ViewGroup) findViewById(R.id.ShotTable);
                        shotTable.addView(r);
                    }
                });
            }
        });
    }

    public void handleException(final Throwable t) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final AlertDialog.Builder b = new AlertDialog.Builder(
                        OpenShotTimer.this);
                final StringWriter sw = new StringWriter();
                final PrintWriter pw = new PrintWriter(sw);
                t.printStackTrace(pw);
                pw.flush();
                b.setMessage(sw.toString());
                b.setNeutralButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        finish();
                    }

                });
                b.show();
            }
        });
        
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        m_settings = getSharedPreferences(PREFS_NAME, 0);
        try {
            setContentView(R.layout.main);
            
            constructShotString();

            constructQuitButton();

            constructBuzzButton();

            constructEndButton();
            
            constructOptionsMenu();
            stringKiller.start();
        } catch (Throwable t) {
            handleException(t);
        }
    }

    private void constructEndButton() {
        findViewById(R.id.EndButton).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startTime = -1;
                    m_string.end();
                } catch (Throwable t) {
                    handleException(t);
                }
            }
        });
    }

    private void constructBuzzButton() {
        findViewById(R.id.BuzzButton).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startTime = System.currentTimeMillis();
                    final ViewGroup shotTable = (ViewGroup) findViewById(R.id.ShotTable);
                    for (final View shotEventView : m_shotEventViews) {
                        shotTable.removeView(shotEventView);
                    }
                    m_shotEventViews.clear();
                    if (!m_string.start()) {
                        final AlertDialog.Builder b = new AlertDialog.Builder(
                                OpenShotTimer.this);
                        b
                                .setMessage("Unable to access audio hardware. A fake audio stream will be used"
                                        + " for test");
                        final AlertDialog d = b.show();
                        findViewById(R.id.BuzzButton).postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                d.dismiss();
                            }
                        }, 3500);
                    }
                } catch (Throwable t) {
                    handleException(t);
                }
            }
        });
    }

    private void constructQuitButton() {
        findViewById(R.id.QuitButton).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startTime = -1;
                    m_string.close();
                    finish();
                } catch (Throwable t) {
                    handleException(t);
                }
            }
        });
    }
    
    private void constructOptionsMenu() {
        final TextView svv = (TextView)findViewById(R.id.SystemVolumeValue);
        svv.setText(Integer.toString(m_string.getSystemStreamVolume()));
        
        final SeekBar systemVolumeBar = (SeekBar)findViewById(R.id.SystemVolumeBar);
        systemVolumeBar.setMax(m_string.getSystemStreamMaxVolume());
        systemVolumeBar.setProgress(m_string.getSystemStreamVolume());
        
        systemVolumeBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    m_string.setSystemStreamVolume(progress);
                    svv.setText(Integer.toString(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
            
        });
        
        final TextView bvv = (TextView)findViewById(R.id.BuzzerVolumeValue);
        bvv.setText(Integer.toString(m_string.getBuzzerVolume()));
        
        final SeekBar volumeBar = (SeekBar)findViewById(R.id.BuzzerVolumeBar);
        volumeBar.setProgress(m_string.getBuzzerVolume());
        
        volumeBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    m_string.setBuzzerVolume(progress);
                    bvv.setText(Integer.toString(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
            
        });
        
        final TextView dv = (TextView)findViewById(R.id.DelayValue);
        dv.setText(Integer.toString(m_string.getBuzzerDelay()));
        
        final SeekBar delayBar = (SeekBar)findViewById(R.id.BuzzerDelayBar);
        delayBar.setProgress(m_string.getBuzzerDelay());
        
        delayBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    m_string.setBuzzerDelay(progress);
                    dv.setText(Integer.toString(progress));
                    
                    final CheckBox randomStartBox = (CheckBox)findViewById(R.id.RandomStart);
                    if (progress > 2) {
                        randomStartBox.setEnabled(true);
                        if (randomStartBox.isChecked()) {
                            m_string.setRandomStart(true);
                        }
                    } else {
                        randomStartBox.setEnabled(false);
                        m_string.setRandomStart(false);
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
            
        });
        
        final TextView sv = (TextView)findViewById(R.id.SensitivityValue);
        sv.setText(Integer.toString(m_string.getSensitivity()));
        
        final SeekBar sensitivityBar = (SeekBar)findViewById(R.id.SensitivityBar);
        sensitivityBar.setProgress(m_string.getSensitivity());
        
        sensitivityBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    m_string.setSensitivity(progress);
                    sv.setText(Integer.toString(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
            
        });
        
        final CheckBox randomStartBox = (CheckBox)findViewById(R.id.RandomStart);
        randomStartBox.setChecked(m_string.getRandomStart());
        if (m_string.getBuzzerDelay() > 2) {
            randomStartBox.setEnabled(true);
        } else {
            randomStartBox.setEnabled(false);
        }
    }
}