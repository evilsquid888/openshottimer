package org.ost;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;

public class ShotDetectorTest {

    private File testFiles[] = new File[] {
            new File("test/samples/9_shots_bigendian.raw"),
            new File("test/samples/9_shots_bigendian_2.raw"),
            new File("test/samples/android_rec_edited.raw") };
    
    private int numExpectedShots[] = new int[] { 9, 9, 17 };
    
    private BufferedInputStream inputStreams[];
    
    @org.junit.Before
    public void openStreams() throws FileNotFoundException {
        int inputStreamCounter = 0;
        inputStreams = new BufferedInputStream[testFiles.length];
        for (File file : testFiles) {
            if (!file.canRead()) {
                fail("Couldn't open test file " + file.getPath());
            }
            
            FileInputStream inputStream = new FileInputStream(file);
            BufferedInputStream bis = new BufferedInputStream(inputStream);
            inputStreams[inputStreamCounter++] = bis;
        }
    }
    
    @org.junit.After
    public void closeStreams() throws IOException {
        for (BufferedInputStream bis : inputStreams) {
            if (bis != null) {
                bis.close();
            }
        }
    }
    
    @org.junit.Test
    public void testShotDetector() throws IOException {
        int inputStreamIndex = 0;
        for (BufferedInputStream bis : inputStreams) {
            AmplitudeSpikeShotDetector sd =
                new AmplitudeSpikeShotDetector( 44100, 16, 5);
            byte readBytes[] = new byte[1024];
            int shotEvents = 0;
            int read = 0;
            while((read = bis.read(readBytes)) != -1) {
                shotEvents += sd.processAudio(ByteBuffer.wrap(readBytes, 0, read)).length;
            }
            assertEquals(numExpectedShots[inputStreamIndex++], shotEvents);
        }
    }
}
