/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.media.misc.cts;

import static android.media.MediaMetadataRetriever.OPTION_CLOSEST;
import static android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC;
import static android.media.MediaMetadataRetriever.OPTION_NEXT_SYNC;
import static android.media.MediaMetadataRetriever.OPTION_PREVIOUS_SYNC;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.media.MediaDataSource;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.cts.CodecUtils;
import android.media.cts.TestMediaDataSource;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresDevice;
import android.util.Log;
import android.view.Display;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.MediaUtils;
import com.android.compatibility.common.util.Preconditions;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Presubmit
@SmallTest
@RequiresDevice
@AppModeFull(reason = "No interaction with system server")
@RunWith(AndroidJUnit4.class)
public class MediaMetadataRetrieverTest {
    private static final String TAG = "MediaMetadataRetrieverTest";
    private static final boolean SAVE_BITMAP_OUTPUT = false;
    private static final String TEST_MEDIA_FILE = "retriever_test.3gp";

    static final String mInpPrefix = WorkDir.getMediaDirString();
    protected MediaMetadataRetriever mRetriever;
    private PackageManager mPackageManager;

    protected static final int SLEEP_TIME = 1000;
    private static int BORDER_WIDTH = 16;
    private static Color COLOR_BLOCK =
            Color.valueOf(1.0f, 1.0f, 1.0f);
    private static Color[] COLOR_BARS = {
            Color.valueOf(0.0f, 0.0f, 0.0f),
            Color.valueOf(0.0f, 0.0f, 0.64f),
            Color.valueOf(0.0f, 0.64f, 0.0f),
            Color.valueOf(0.0f, 0.64f, 0.64f),
            Color.valueOf(0.64f, 0.0f, 0.0f),
            Color.valueOf(0.64f, 0.0f, 0.64f),
            Color.valueOf(0.64f, 0.64f, 0.0f),
    };
    private boolean mIsAtLeastR = ApiLevelUtil.isAtLeast(Build.VERSION_CODES.R);
    private boolean mIsAtLeastS = ApiLevelUtil.isAtLeast(Build.VERSION_CODES.S);

    private Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }

    @Before
    public void setUp() throws Exception {
        mRetriever = new MediaMetadataRetriever();
        mPackageManager = getContext().getPackageManager();
    }

    @After
    public void tearDown() throws Exception {
        mRetriever.release();
        File file = new File(Environment.getExternalStorageDirectory(), TEST_MEDIA_FILE);
        if (file.exists()) {
            file.delete();
        }
    }

    protected AssetFileDescriptor getAssetFileDescriptorFor(final String res)
            throws FileNotFoundException {
        File inpFile = new File(mInpPrefix + res);
        Preconditions.assertTestFileExists(mInpPrefix + res);
        ParcelFileDescriptor parcelFD =
                ParcelFileDescriptor.open(inpFile, ParcelFileDescriptor.MODE_READ_ONLY);
        return new AssetFileDescriptor(parcelFD, 0, parcelFD.getStatSize());
    }

    protected void setDataSourceFd(final String res) {
        try {
            AssetFileDescriptor afd = getAssetFileDescriptorFor(res);
            mRetriever.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
        } catch (Exception e) {
            fail("Unable to open file");
        }
    }

    protected TestMediaDataSource setDataSourceCallback(final String res) {
        TestMediaDataSource ds = null;
        try {
            AssetFileDescriptor afd = getAssetFileDescriptorFor(res);
            ds = TestMediaDataSource.fromAssetFd(afd);
            mRetriever.setDataSource(ds);
        } catch (Exception e) {
            fail("Unable to open file");
        }
        return ds;
    }

    protected TestMediaDataSource getFaultyDataSource(final String res, boolean throwing) {
        TestMediaDataSource ds = null;
        try {
            AssetFileDescriptor afd = getAssetFileDescriptorFor(res);
            ds = TestMediaDataSource.fromAssetFd(afd);
            if (throwing) {
                ds.throwFromReadAt();
            } else {
                ds.returnFromReadAt(-2);
            }
        } catch (Exception e) {
            fail("Unable to open file");
        }
        return ds;
    }

    @Test
    public void testExceptionWhileClosingMediaDataSource() throws IOException {
        MediaDataSource backingMediaDataSource =
                TestMediaDataSource.fromAssetFd(
                        getAssetFileDescriptorFor("audio_with_metadata.mp3"));
        MediaDataSource mediaDataSource = new MediaDataSource() {
            @Override
            public int readAt(long position, byte[] buffer, int offset, int size)
                    throws IOException {
                return backingMediaDataSource.readAt(position, buffer, offset, size);
            }

            @Override
            public long getSize() throws IOException {
                return backingMediaDataSource.getSize();
            }

            @Override
            public void close() throws IOException {
                backingMediaDataSource.close();
                throw new IOException();
            }
        };
        mRetriever.setDataSource(mediaDataSource);
        try {
            mRetriever.release();
            fail("Expected IOException not thrown.");
        } catch (IOException e) {
            // Expected.
        }
    }

    @Test
    public void testAudioMetadata() {
        setDataSourceCallback("audio_with_metadata.mp3");

        assertEquals("Title was other than expected",
            "Chimey Phone",
            mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE));

        assertEquals("Artist was other than expected",
            "Some artist",
            mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST));

        assertNull("Album artist was unexpectedly present",
            mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST));

        assertNull("Author was unexpectedly present",
            mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR));

        assertNull("Composer was unexpectedly present",
            mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER));

        assertEquals("Number of tracks was other than expected",
            "1",
            mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_NUM_TRACKS));

        assertEquals("Has audio was other than expected",
            "yes",
            mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO));

        assertEquals("Mime type was other than expected",
            "audio/mpeg",
            mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE));
    }

    @Test
    public void test3gppMetadata() {
        setDataSourceCallback("testvideo.3gp");

        assertEquals("Title was other than expected",
                "Title", mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE));

        assertEquals("Artist was other than expected",
                "UTF16LE エンディアン ",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST));

        assertEquals("Album was other than expected",
                "Test album",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM));

        assertNull("Album artist was unexpectedly present",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST));

        assertNull("Author was unexpectedly present",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR));

        assertNull("Composer was unexpectedly present",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER));

        assertEquals("Track number was other than expected",
                "10",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER));

        assertNull("Disc number was unexpectedly present",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER));

        assertNull("Compilation was unexpectedly present",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPILATION));

        assertEquals("Year was other than expected",
                "2013", mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR));

        assertEquals("Date was other than expected",
                "19040101T000000.000Z",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE));

        assertEquals("Bitrate was other than expected",
                "365018",  // = 504045 (file size in byte) * 8e6 / 11047000 (duration in us)
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE));

        assertNull("Capture frame rate was unexpectedly present",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE));

        assertEquals("Duration was other than expected",
                "11047",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));

        assertEquals("Number of tracks was other than expected",
                "4",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_NUM_TRACKS));

        assertEquals("Has audio was other than expected",
                "yes",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO));

        assertEquals("Has video was other than expected",
                "yes",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO));

        assertEquals("Video frame count was other than expected",
                "172",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT));

        assertEquals("Video height was other than expected",
                "288",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));

        assertEquals("Video width was other than expected",
                "352",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));

        assertEquals("Video rotation was other than expected",
                "0",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));

        assertEquals("Mime type was other than expected",
                "video/mp4",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE));

        assertNull("Location was unexpectedly present",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION));

        assertNull("EXIF length was unexpectedly present",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_EXIF_LENGTH));

        assertNull("EXIF offset was unexpectedly present",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_EXIF_OFFSET));

        assertNull("Writer was unexpectedly present",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_WRITER));
    }

    @Test
    public void testID3v2Metadata() {
        setDataSourceFd(
                "video_480x360_mp4_h264_500kbps_25fps_aac_stereo_128kbps_44100hz_id3v2.mp4");

        assertEquals("Title was other than expected",
                "Title", mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE));

        assertEquals("Artist was other than expected",
                "UTF16LE エンディアン ",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST));

        assertEquals("Album was other than expected",
                "Test album",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM));

        assertNull("Album artist was unexpectedly present",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST));

        assertNull("Author was unexpectedly present",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR));

        assertNull("Composer was unexpectedly present",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER));

        assertEquals("Track number was other than expected",
                "10",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER));

        assertNull("Disc number was unexpectedly present",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER));

        assertNull("Compilation was unexpectedly present",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPILATION));

        assertEquals("Year was other than expected",
                "2013", mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR));

        assertEquals("Date was other than expected",
                "19700101T000000.000Z",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE));

        assertEquals("Bitrate was other than expected",
                "499895",  // = 624869 (file size in byte) * 8e6 / 10000000 (duration in us)
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE));

        assertNull("Capture frame rate was unexpectedly present",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE));

        assertEquals("Duration was other than expected",
                "10000",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));

        assertEquals("Number of tracks was other than expected",
                "2",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_NUM_TRACKS));

        assertEquals("Has audio was other than expected",
                "yes",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO));

        assertEquals("Has video was other than expected",
                "yes",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO));

        assertEquals("Video frame count was other than expected",
                "240",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT));

        assertEquals("Video height was other than expected",
                "360",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));

        assertEquals("Video width was other than expected",
                "480",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));

        assertEquals("Video rotation was other than expected",
                "0",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));

        assertEquals("Mime type was other than expected",
                "video/mp4",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE));

        assertNull("Location was unexpectedly present",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION));

        assertNull("EXIF length was unexpectedly present",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_EXIF_LENGTH));

        assertNull("EXIF offset was unexpectedly present",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_EXIF_OFFSET));

        assertNull("Writer was unexpectedly present",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_WRITER));
    }

    @Test
    public void testID3v2Unsynchronization() {
        setDataSourceFd("testmp3_4.mp3");
        assertEquals("Mime type was other than expected",
                "audio/mpeg",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE));
    }

    @Test
    public void testID3v240ExtHeader() {
        setDataSourceFd("sinesweepid3v24ext.mp3");
        assertEquals("Mime type was other than expected",
                "audio/mpeg",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE));
        assertEquals("Title was other than expected",
                "sinesweep",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE));
        assertNotNull("no album art",
                mRetriever.getEmbeddedPicture());
    }

    @Test
    public void testID3v230ExtHeader() {
        setDataSourceFd("sinesweepid3v23ext.mp3");
        assertEquals("Mime type was other than expected",
                "audio/mpeg",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE));
        assertEquals("Title was other than expected",
                "sinesweep",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE));
        assertNotNull("no album art",
                mRetriever.getEmbeddedPicture());
    }

    @Test
    public void testID3v230ExtHeaderBigEndian() {
        setDataSourceFd("sinesweepid3v23extbe.mp3");
        assertEquals("Mime type was other than expected",
                "audio/mpeg",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE));
        assertEquals("Title was other than expected",
                "sinesweep",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE));
        assertNotNull("no album art",
                mRetriever.getEmbeddedPicture());
    }

    @Test
    public void testMp4AlbumArt() {
        setDataSourceFd("swirl_128x128_h264_albumart.mp4");
        assertEquals("Mime type was other than expected",
                "video/mp4",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE));
        assertNotNull("no album art",
                mRetriever.getEmbeddedPicture());
    }

    @Test
    public void testGenreParsing() {
        if (!MediaUtils.check(mIsAtLeastR, "test needs Android 11")) return;
        Object [][] genres = {
            { "id3test0.mp3", null },
            { "id3test1.mp3", "Country" },
            { "id3test2.mp3", "Classic Rock, Android" },
            { "id3test3.mp3", null },
            { "id3test4.mp3", "Classic Rock, (Android)" },
            { "id3test5.mp3", null },
            { "id3test6.mp3", "Funk, Grunge, Hip-Hop" },
            { "id3test7.mp3", null },
            { "id3test8.mp3", "Disco" },
            { "id3test9.mp3", "Cover" },
            { "id3test10.mp3", "Pop, Remix" },
            { "id3test11.mp3", "Remix" },
        };
        for (Object [] genre: genres) {
            setDataSourceFd((String)genre[0] /* resource id */);
            assertEquals("Unexpected genre: ",
                    genre[1] /* genre */,
                    mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE));
        }
    }

    @Test
    public void testBitsPerSampleAndSampleRate() {
        setDataSourceFd("testwav_16bit_44100hz.wav");

        assertEquals("Bits per sample was other than expected",
                "16",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE));

        assertEquals("Sample rate was other than expected",
                "44100",
                mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE));

    }

    @Test
    public void testGetEmbeddedPicture() {
        setDataSourceFd("largealbumart.mp3");

        assertNotNull("couldn't retrieve album art", mRetriever.getEmbeddedPicture());
    }

    @Test
    public void testAlbumArtInOgg() throws Exception {
        setDataSourceFd("sinesweepoggalbumart.ogg");
        assertNotNull("couldn't retrieve album art from ogg", mRetriever.getEmbeddedPicture());
    }

    @Test
    public void testSetDataSourcePath() {
        copyMediaFile();
        File file = new File(Environment.getExternalStorageDirectory(), TEST_MEDIA_FILE);
        try {
            mRetriever.setDataSource(file.getAbsolutePath());
        } catch (Exception ex) {
            fail("Failed setting data source with path, caught exception:" + ex);
        }
    }

    @Test
    public void testSetDataSourceUri() {
        copyMediaFile();
        File file = new File(Environment.getExternalStorageDirectory(), TEST_MEDIA_FILE);
        try {
            Uri uri = Uri.parse(file.getAbsolutePath());
            mRetriever.setDataSource(getContext(), uri);
        } catch (Exception ex) {
            fail("Failed setting data source with Uri, caught exception:" + ex);
        }
    }

    @Test
    public void testSetDataSourceNullPath() {
        try {
            mRetriever.setDataSource((String)null);
            fail("Expected IllegalArgumentException.");
        } catch (IllegalArgumentException ex) {
            // Expected, test passed.
        }
    }

    @Test
    public void testSetDataSourceNullUri() {
        try {
            mRetriever.setDataSource(getContext(), (Uri)null);
            fail("Expected IllegalArgumentException.");
        } catch (IllegalArgumentException ex) {
            // Expected, test passed.
        }
    }

    @Test
    public void testNullMediaDataSourceIsRejected() {
        try {
            mRetriever.setDataSource((MediaDataSource)null);
            fail("Expected IllegalArgumentException.");
        } catch (IllegalArgumentException ex) {
            // Expected, test passed.
        }
    }

    @Test
    public void testMediaDataSourceIsClosedOnRelease() throws Exception {
        TestMediaDataSource dataSource = setDataSourceCallback("testvideo.3gp");
        mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
        mRetriever.release();
        assertTrue(dataSource.isClosed());
    }

    @Test
    public void testRetrieveFailsIfMediaDataSourceThrows() throws Exception {
        TestMediaDataSource ds = getFaultyDataSource("testvideo.3gp", true /* throwing */);
        try {
            mRetriever.setDataSource(ds);
            fail("Failed to throw exceptions");
        } catch (RuntimeException e) {
            assertTrue(mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) == null);
        }
    }

    @Test
    public void testRetrieveFailsIfMediaDataSourceReturnsAnError() throws Exception {
        TestMediaDataSource ds = getFaultyDataSource("testvideo.3gp", false /* throwing */);
        try {
            mRetriever.setDataSource(ds);
            fail("Failed to throw exceptions");
        } catch (RuntimeException e) {
            assertTrue(mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) == null);
        }
    }

    private void testThumbnail(final String res, int targetWdith, int targetHeight) {
        testThumbnail(res, null /*outPath*/, targetWdith, targetHeight);
    }

    private void testThumbnail(final String res, String outPath, int targetWidth,
            int targetHeight) {
        Stopwatch timer = new Stopwatch();

        setDataSourceFd(res);

        if (!MediaUtils.hasCodecForResourceAndDomain(mInpPrefix + res, "video/")) {
            MediaUtils.skipTest("no video codecs for resource: " + mInpPrefix + res);
            return;
        }

        timer.start();
        Bitmap thumbnail = mRetriever.getFrameAtTime(-1 /* timeUs (any) */);
        timer.end();
        timer.printDuration("getFrameAtTime");

        assertNotNull(thumbnail);

        // Verifies bitmap width and height.
        assertEquals("Video width was other than expected", Integer.toString(targetWidth),
            mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
        assertEquals("Video height was other than expected", Integer.toString(targetHeight),
            mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));

        // save output file if needed
        if (outPath != null) {
            FileOutputStream out = null;
            try {
                out = new FileOutputStream(outPath);
            } catch (FileNotFoundException e) {
                fail("Can't open output file");
            }

            thumbnail.compress(Bitmap.CompressFormat.PNG, 100, out);

            try {
                out.flush();
                out.close();
            } catch (IOException e) {
                fail("Can't close file");
            }
        }
    }

    @Test
    public void testThumbnailH264() {
        testThumbnail(
                "bbb_s4_1280x720_mp4_h264_mp31_8mbps_30fps_aac_he_mono_40kbps_44100hz.mp4",
                1280,
                720);
    }

    @Test
    public void testThumbnailH263() {
        testThumbnail("video_176x144_3gp_h263_56kbps_12fps_aac_mono_24kbps_11025hz.3gp", 176, 144);
    }

    @Test
    public void testThumbnailMPEG4() {
        testThumbnail(
                "video_1280x720_mp4_mpeg4_1000kbps_25fps_aac_stereo_128kbps_44100hz.mp4",
                1280,
                720);
    }

    @Test
    public void testThumbnailVP8() {
        testThumbnail(
                "bbb_s1_640x360_webm_vp8_2mbps_30fps_vorbis_5ch_320kbps_48000hz.webm",
                640,
                360);
    }

    @Test
    public void testThumbnailVP9() {
        testThumbnail(
                "bbb_s1_640x360_webm_vp9_0p21_1600kbps_30fps_vorbis_stereo_128kbps_48000hz.webm",
                640,
                360);
    }

    @Test
    public void testThumbnailHEVC() {
        testThumbnail(
                "bbb_s1_720x480_mp4_hevc_mp3_1600kbps_30fps_aac_he_6ch_240kbps_48000hz.mp4",
                720,
                480);
    }

    @Test
    public void testThumbnailVP9Hdr() {
        if (!MediaUtils.check(mIsAtLeastR, "test needs Android 11")) return;

        DisplayManager displayManager = getContext().getSystemService(DisplayManager.class);
        int numberOfSupportedHdrTypes =
            displayManager.getDisplay(Display.DEFAULT_DISPLAY).getHdrCapabilities()
                .getSupportedHdrTypes().length;

        if (numberOfSupportedHdrTypes == 0) {
            MediaUtils.skipTest("No supported HDR display type");
            return;
        }

        testThumbnail("video_1280x720_vp9_hdr_static_3mbps.mkv", 1280, 720);
    }

    @Test
    public void testThumbnailAV1Hdr() {
        if (!MediaUtils.check(mIsAtLeastR, "test needs Android 11")) return;

        DisplayManager displayManager = getContext().getSystemService(DisplayManager.class);
        int numberOfSupportedHdrTypes =
            displayManager.getDisplay(Display.DEFAULT_DISPLAY).getHdrCapabilities()
                .getSupportedHdrTypes().length;

        if (numberOfSupportedHdrTypes == 0) {
            MediaUtils.skipTest("No supported HDR display type");
            return;
        }

        testThumbnail("video_1280x720_av1_hdr_static_3mbps.webm", 1280, 720);
    }

    @Test
    public void testThumbnailHDR10() {
        if (!MediaUtils.check(mIsAtLeastR, "test needs Android 11")) return;

        testThumbnail("video_1280x720_hevc_hdr10_static_3mbps.mp4", 1280, 720);
    }

    private void testThumbnailWithRotation(final String res, int targetRotation) {
        Stopwatch timer = new Stopwatch();

        setDataSourceFd(res);

        if (!MediaUtils.hasCodecForResourceAndDomain(mInpPrefix + res, "video/")) {
            MediaUtils.skipTest("no video codecs for resource: " + mInpPrefix + res);
            return;
        }

        assertEquals("Video rotation was other than expected", Integer.toString(targetRotation),
            mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));

        timer.start();
        Bitmap thumbnail = mRetriever.getFrameAtTime(-1 /* timeUs (any) */);
        timer.end();
        timer.printDuration("getFrameAtTime");

        verifyVideoFrameRotation(thumbnail, targetRotation);
    }

    @Test
    public void testThumbnailWithRotation() {
        String[] res = {"video_h264_mpeg4_rotate_0.mp4", "video_h264_mpeg4_rotate_90.mp4",
                "video_h264_mpeg4_rotate_180.mp4", "video_h264_mpeg4_rotate_270.mp4"};
        int[] targetRotations = {0, 90, 180, 270};
        for (int i = 0; i < res.length; i++) {
            testThumbnailWithRotation(res[i], targetRotations[i]);
        }
    }

    /**
     * The following tests verifies MediaMetadataRetriever.getFrameAtTime behavior.
     *
     * We use a simple stream with binary counter at the top to check which frame
     * is actually captured. The stream is 30fps with 600 frames in total. It has
     * I/P/B frames, with I interval of 30. Due to the encoding structure, pts starts
     * at 66666 (instead of 0), so we have I frames at 66666, 1066666, ..., etc..
     *
     * For each seek option, we check the following five cases:
     *     1) frame time falls right on a sync frame
     *     2) frame time is near the middle of two sync frames but closer to the previous one
     *     3) frame time is near the middle of two sync frames but closer to the next one
     *     4) frame time is shortly before a sync frame
     *     5) frame time is shortly after a sync frame
     */
    @Test
    public void testGetFrameAtTimePreviousSync() {
        int[][] testCases = {
                { 2066666, 60 }, { 2500000, 60 }, { 2600000, 60 }, { 3000000, 60 }, { 3200000, 90}};
        testGetFrameAtTime(OPTION_PREVIOUS_SYNC, testCases);
    }

    @Test
    public void testGetFrameAtTimeNextSync() {
        int[][] testCases = {
                { 2066666, 60 }, { 2500000, 90 }, { 2600000, 90 }, { 3000000, 90 }, { 3200000, 120}};
        testGetFrameAtTime(OPTION_NEXT_SYNC, testCases);
    }

    @Test
    public void testGetFrameAtTimeClosestSync() {
        int[][] testCases = {
                { 2066666, 60 }, { 2500000, 60 }, { 2600000, 90 }, { 3000000, 90 }, { 3200000, 90}};
        testGetFrameAtTime(OPTION_CLOSEST_SYNC, testCases);
    }

    @Test
    public void testGetFrameAtTimeClosest() {
        int[][] testCases = {
                { 2066666, 60 }, { 2500001, 73 }, { 2599999, 76 }, { 3016000, 88 }, { 3184000, 94}};
        testGetFrameAtTime(OPTION_CLOSEST, testCases);
    }

    @Test
    public void testGetFrameAtTimePreviousSyncEditList() {
        if (!MediaUtils.check(mIsAtLeastR, "test needs Android 11")) return;
        int[][] testCases = {
                { 2000000, 60 }, { 2433334, 60 }, { 2533334, 60 }, { 2933334, 60 }, { 3133334, 90}};
        testGetFrameAtTimeEditList(OPTION_PREVIOUS_SYNC, testCases);
    }

    @Test
    public void testGetFrameAtTimeNextSyncEditList() {
        if (!MediaUtils.check(mIsAtLeastR, "test needs Android 11")) return;
        int[][] testCases = {
                { 2000000, 60 }, { 2433334, 90 }, { 2533334, 90 }, { 2933334, 90 }, { 3133334, 120}};
        testGetFrameAtTimeEditList(OPTION_NEXT_SYNC, testCases);
    }

    @Test
    public void testGetFrameAtTimeClosestSyncEditList() {
        if (!MediaUtils.check(mIsAtLeastR, "test needs Android 11")) return;
        int[][] testCases = {
                { 2000000, 60 }, { 2433334, 60 }, { 2533334, 90 }, { 2933334, 90 }, { 3133334, 90}};
        testGetFrameAtTimeEditList(OPTION_CLOSEST_SYNC, testCases);
    }

    @Test
    public void testGetFrameAtTimeClosestEditList() {
        if (!MediaUtils.check(mIsAtLeastR, "test needs Android 11")) return;
        int[][] testCases = {
                { 2000000, 60 }, { 2433335, 73 }, { 2533333, 76 }, { 2949334, 88 }, { 3117334, 94}};
        testGetFrameAtTimeEditList(OPTION_CLOSEST, testCases);
    }

    @Test
    public void testGetFrameAtTimePreviousSyncEmptyNormalEditList() {
        if (!MediaUtils.check(mIsAtLeastR, "test needs Android 11")) return;
        int[][] testCases = {
                { 2133000, 60 }, { 2566334, 60 }, { 2666334, 60 }, { 3100000, 60 }, { 3266000, 90}};
        testGetFrameAtTimeEmptyNormalEditList(OPTION_PREVIOUS_SYNC, testCases);
    }

    @Test
    public void testGetFrameAtTimeNextSyncEmptyNormalEditList() {
        if (!MediaUtils.check(mIsAtLeastR, "test needs Android 11")) return;
        int[][] testCases = {{ 2000000, 60 }, { 2133000, 60 }, { 2566334, 90 }, { 3100000, 90 },
                { 3200000, 120}};
        testGetFrameAtTimeEmptyNormalEditList(OPTION_NEXT_SYNC, testCases);
    }

    @Test
    public void testGetFrameAtTimeClosestSyncEmptyNormalEditList() {
        if (!MediaUtils.check(mIsAtLeastR, "test needs Android 11")) return;
        int[][] testCases = {
                { 2133000, 60 }, { 2566334, 60 }, { 2666000, 90 }, { 3133000, 90 }, { 3200000, 90}};
        testGetFrameAtTimeEmptyNormalEditList(OPTION_CLOSEST_SYNC, testCases);
    }

    @Test
    public void testGetFrameAtTimeClosestEmptyNormalEditList() {
        if (!MediaUtils.check(mIsAtLeastR, "test needs Android 11")) return;
        int[][] testCases = {
                { 2133000, 60 }, { 2566000, 73 }, { 2666000, 76 }, { 3066001, 88 }, { 3255000, 94}};
        testGetFrameAtTimeEmptyNormalEditList(OPTION_CLOSEST, testCases);
    }

    private void testGetFrameAtTime(int option, int[][] testCases) {
        testGetFrameAt(testCases, (r) -> {
            List<Bitmap> bitmaps = new ArrayList<>();
            for (int i = 0; i < testCases.length; i++) {
                bitmaps.add(r.getFrameAtTime(testCases[i][0], option));
            }
            return bitmaps;
        });
    }

    private void testGetFrameAtTimeEditList(int option, int[][] testCases) {
        MediaMetadataRetriever.BitmapParams params = new MediaMetadataRetriever.BitmapParams();
        params.setPreferredConfig(Bitmap.Config.ARGB_8888);

        testGetFrameAtEditList(testCases, (r) -> {
            List<Bitmap> bitmaps = new ArrayList<>();
            for (int i = 0; i < testCases.length; i++) {
                Bitmap bitmap = r.getFrameAtTime(testCases[i][0], option, params);
                assertEquals(Bitmap.Config.ARGB_8888, params.getActualConfig());
                bitmaps.add(bitmap);
            }
            return bitmaps;
        });
    }

    private void testGetFrameAtTimeEmptyNormalEditList(int option, int[][] testCases) {
        MediaMetadataRetriever.BitmapParams params = new MediaMetadataRetriever.BitmapParams();
        params.setPreferredConfig(Bitmap.Config.ARGB_8888);

        testGetFrameAtEmptyNormalEditList(testCases, (r) -> {
            List<Bitmap> bitmaps = new ArrayList<>();
            for (int i = 0; i < testCases.length; i++) {
                Bitmap bitmap = r.getFrameAtTime(testCases[i][0], option, params);
                assertEquals(Bitmap.Config.ARGB_8888, params.getActualConfig());
                bitmaps.add(bitmap);
            }
            return bitmaps;
        });
    }

    @Test
    public void testGetFrameAtIndex() {
        int[][] testCases = { { 60, 60 }, { 73, 73 }, { 76, 76 }, { 88, 88 }, { 94, 94} };

        testGetFrameAt(testCases, (r) -> {
            List<Bitmap> bitmaps = new ArrayList<>();
            for (int i = 0; i < testCases.length; i++) {
                bitmaps.add(r.getFrameAtIndex(testCases[i][0]));
            }
            return bitmaps;
        });

        MediaMetadataRetriever.BitmapParams params = new MediaMetadataRetriever.BitmapParams();
        params.setPreferredConfig(Bitmap.Config.RGB_565);
        assertEquals("Failed to set preferred config",
                Bitmap.Config.RGB_565, params.getPreferredConfig());

        testGetFrameAt(testCases, (r) -> {
            List<Bitmap> bitmaps = new ArrayList<>();
            for (int i = 0; i < testCases.length; i++) {
                Bitmap bitmap = r.getFrameAtIndex(testCases[i][0], params);
                assertEquals(Bitmap.Config.RGB_565, params.getActualConfig());
                bitmaps.add(bitmap);
            }
            return bitmaps;
        });
    }

    @Test
    public void testGetFramesAtIndex() {
        int[][] testCases = { { 27, 27 }, { 28, 28 }, { 29, 29 }, { 30, 30 }, { 31, 31} };

        testGetFrameAt(testCases, (r) -> {
            return r.getFramesAtIndex(testCases[0][0], testCases.length);
        });

        MediaMetadataRetriever.BitmapParams params = new MediaMetadataRetriever.BitmapParams();
        params.setPreferredConfig(Bitmap.Config.RGB_565);
        assertEquals("Failed to set preferred config",
                Bitmap.Config.RGB_565, params.getPreferredConfig());

        testGetFrameAt(testCases, (r) -> {
            List<Bitmap> bitmaps = r.getFramesAtIndex(testCases[0][0], testCases.length, params);
            assertEquals(Bitmap.Config.RGB_565, params.getActualConfig());
            return bitmaps;
        });
    }

    private void testGetFrameAt(int[][] testCases,
            Function<MediaMetadataRetriever, List<Bitmap>> bitmapRetriever) {
        testGetFrameAt("binary_counter_320x240_30fps_600frames.mp4",
                testCases, bitmapRetriever);
    }

    private void testGetFrameAtEditList(int[][] testCases,
            Function<MediaMetadataRetriever, List<Bitmap>> bitmapRetriever) {
        testGetFrameAt("binary_counter_320x240_30fps_600frames_editlist.mp4",
                testCases, bitmapRetriever);
    }

    private void testGetFrameAtEmptyNormalEditList(int[][] testCases,
            Function<MediaMetadataRetriever, List<Bitmap>> bitmapRetriever) {
        testGetFrameAt("binary_counter_320x240_30fps_600frames_empty_normal_editlist_entries.mp4",
                testCases, bitmapRetriever);
    }

    private void testGetFrameAt(final String res, int[][] testCases,
            Function<MediaMetadataRetriever, List<Bitmap>> bitmapRetriever) {

        setDataSourceFd(res);

        if (!MediaUtils.hasCodecForResourceAndDomain(mInpPrefix + res, "video/")
            && mPackageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            MediaUtils.skipTest("no video codecs for resource: " + mInpPrefix + res + " on watch");
            return;
        }

        List<Bitmap> bitmaps = bitmapRetriever.apply(mRetriever);
        for (int i = 0; i < testCases.length; i++) {
            verifyVideoFrame(bitmaps.get(i), testCases[i]);
        }
    }

    private void verifyVideoFrame(Bitmap bitmap, int[] testCase) {
        try {
            assertTrue("Failed to get bitmap for " + testCase[0], bitmap != null);
            assertEquals("Counter value incorrect for " + testCase[0],
                    testCase[1], CodecUtils.readBinaryCounterFromBitmap(bitmap));

            if (SAVE_BITMAP_OUTPUT) {
                CodecUtils.saveBitmapToFile(bitmap, "test_" + testCase[0] + ".jpg");
            }
        } catch (Exception e) {
            fail("Exception getting bitmap: " + e);
        }
    }

    private void verifyVideoFrameRotation(Bitmap bitmap, int targetRotation) {
        try {
            assertTrue("Failed to get bitmap for " + targetRotation + " degrees", bitmap != null);
            assertTrue("Frame incorrect for " + targetRotation + " degrees",
                CodecUtils.VerifyFrameRotationFromBitmap(bitmap, targetRotation));

            if (SAVE_BITMAP_OUTPUT) {
                CodecUtils.saveBitmapToFile(bitmap, "test_rotation_" + targetRotation + ".jpg");
            }
        } catch (Exception e) {
            fail("Exception getting bitmap: " + e);
        }
    }

    /**
     * The following tests verifies MediaMetadataRetriever.getScaledFrameAtTime behavior.
     */
    @Test
    public void testGetScaledFrameAtTimeWithInvalidResolutions() {
        String[] resources = {"binary_counter_320x240_30fps_600frames.mp4",
                "binary_counter_320x240_30fps_600frames_editlist.mp4",
                "bbb_s4_1280x720_mp4_h264_mp31_8mbps_30fps_aac_he_mono_40kbps_44100hz.mp4",
                "video_176x144_3gp_h263_56kbps_12fps_aac_mono_24kbps_11025hz.3gp",
                "video_1280x720_mp4_mpeg4_1000kbps_25fps_aac_stereo_128kbps_44100hz.mp4",
                "bbb_s1_640x360_webm_vp8_2mbps_30fps_vorbis_5ch_320kbps_48000hz.webm",
                "bbb_s1_640x360_webm_vp9_0p21_1600kbps_30fps_vorbis_stereo_128kbps_48000hz.webm",
                "bbb_s1_720x480_mp4_hevc_mp3_1600kbps_30fps_aac_he_6ch_240kbps_48000hz.mp4",
                "video_1280x720_vp9_hdr_static_3mbps.mkv",
                "video_1280x720_av1_hdr_static_3mbps.webm",
                "video_1280x720_hevc_hdr10_static_3mbps.mp4"};
        int[][] resolutions = {{0, 120}, {-1, 0}, {-1, 120}, {140, -1}, {-1, -1}};
        int[] options =
                {OPTION_CLOSEST, OPTION_CLOSEST_SYNC, OPTION_NEXT_SYNC, OPTION_PREVIOUS_SYNC};

        for (String res : resources) {
            setDataSourceFd(res);
            if (!MediaUtils.hasCodecForResourceAndDomain(mInpPrefix + res, "video/")
                    && mPackageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)) {
                MediaUtils.skipTest("no video codecs for resource: " + mInpPrefix + res +
                        " on watch");
                continue;
            }

            for (int i = 0; i < resolutions.length; i++) {
                int width = resolutions[i][0];
                int height = resolutions[i][1];
                for (int option : options) {
                    try {
                        Bitmap bitmap = mRetriever.getScaledFrameAtTime(
                                2066666 /*timeUs*/, option, width, height);
                        fail("Failed to receive exception");
                    } catch (IllegalArgumentException e) {
                        // Expect exception
                    }
                }
            }
        }
    }

    private void testGetScaledFrameAtTime(int scaleToWidth, int scaleToHeight,
            int expectedWidth, int expectedHeight, Bitmap.Config config) {
        if (!MediaUtils.check(mIsAtLeastR, "test needs Android 11")) return;
        MediaMetadataRetriever.BitmapParams params = null;
        Bitmap bitmap = null;
        if (config != null) {
            params = new MediaMetadataRetriever.BitmapParams();
            params.setPreferredConfig(config);
            bitmap = mRetriever.getScaledFrameAtTime(
                    2066666 /*timeUs */, OPTION_CLOSEST, scaleToWidth, scaleToHeight, params);
        } else {
            bitmap = mRetriever.getScaledFrameAtTime(
                    2066666 /*timeUs */, OPTION_CLOSEST, scaleToWidth, scaleToHeight);
        }
        if (bitmap == null) {
            fail("Failed to get scaled bitmap");
        }
        if (SAVE_BITMAP_OUTPUT) {
            CodecUtils.saveBitmapToFile(bitmap, String.format("test_%dx%d.jpg",
                    expectedWidth, expectedHeight));
        }
        if (config != null) {
            assertEquals("Actual config is wrong", config, params.getActualConfig());
        }
        assertEquals("Bitmap width is wrong", expectedWidth, bitmap.getWidth());
        assertEquals("Bitmap height is wrong", expectedHeight, bitmap.getHeight());
    }

    @Test
    public void testGetScaledFrameAtTime() {
        String res = "binary_counter_320x240_30fps_600frames.mp4";
        setDataSourceFd(res);
        if (!MediaUtils.hasCodecForResourceAndDomain(mInpPrefix + res, "video/")
            && mPackageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            MediaUtils.skipTest("no video codecs for resource: " + mInpPrefix + res + " on watch");
            return;
        }

        MediaMetadataRetriever.BitmapParams params = new MediaMetadataRetriever.BitmapParams();

        // Test desided size of 160 x 120. Return should be 160 x 120
        testGetScaledFrameAtTime(160, 120, 160, 120, Bitmap.Config.ARGB_8888);

        // Test scaled up bitmap to 640 x 480. Return should be 640 x 480
        testGetScaledFrameAtTime(640, 480, 640, 480, Bitmap.Config.ARGB_8888);

        // Test scaled up bitmap to 320 x 120. Return should be 160 x 120
        testGetScaledFrameAtTime(320, 120, 160, 120, Bitmap.Config.RGB_565);

        // Test scaled up bitmap to 160 x 240. Return should be 160 x 120
        testGetScaledFrameAtTime(160, 240, 160, 120, Bitmap.Config.RGB_565);

        // Test scaled the video with aspect ratio
        res = "binary_counter_320x240_720x240_30fps_600frames.mp4";
        setDataSourceFd(res);

        testGetScaledFrameAtTime(330, 240, 330, 110, null);
    }

    @Test
    public void testGetImageAtIndex() throws Exception {
        if (!MediaUtils.hasDecoder(MediaFormat.MIMETYPE_VIDEO_HEVC)) {
            MediaUtils.skipTest("no video decoders for HEVC");
            return;
        }

        testGetImage("heifwriter_input.heic", 1920, 1080, "image/heif", 0 /*rotation*/,
                4 /*imageCount*/, 3 /*primary*/, true /*useGrid*/, true /*checkColor*/);
    }

    @Test
    public void testGetImageAtIndexAvif() throws Exception {
        if (!MediaUtils.check(mIsAtLeastS, "test needs Android 12")) return;
        if (!MediaUtils.canDecodeVideo(MediaFormat.MIMETYPE_VIDEO_AV1, 1920, 1080, 30)) {
            MediaUtils.skipTest("No AV1 codec for 1080p");
            return;
        }
        testGetImage("sample.avif", 1920, 1080, "image/avif", 0 /*rotation*/,
                1 /*imageCount*/, 0 /*primary*/, false /*useGrid*/, true /*checkColor*/);
    }

    @Test
    public void testGetImageAtIndexAvifGrid() throws Exception {
        if (!MediaUtils.check(mIsAtLeastS, "test needs Android 12")) return;
        if (!MediaUtils.canDecodeVideo(MediaFormat.MIMETYPE_VIDEO_AV1, 512, 512, 30)) {
            MediaUtils.skipTest("No AV1 codec for 512p");
            return;
        }
        testGetImage("sample_grid2x4.avif", 1920, 1080, "image/avif", 0 /*rotation*/,
                1 /*imageCount*/, 0 /*primary*/, true /*useGrid*/, true /*checkColor*/);
    }

    /**
     * Determines if two color values are approximately equal.
     */
    private static boolean approxEquals(Color expected, Color actual) {
        final float MAX_DELTA = 0.025f;
        return (Math.abs(expected.red() - actual.red()) <= MAX_DELTA)
            && (Math.abs(expected.green() - actual.green()) <= MAX_DELTA)
            && (Math.abs(expected.blue() - actual.blue()) <= MAX_DELTA);
    }

    private static Rect getColorBarRect(int index, int width, int height) {
        int barWidth = (width - BORDER_WIDTH * 2) / COLOR_BARS.length;
        return new Rect(BORDER_WIDTH + barWidth * index, BORDER_WIDTH,
                BORDER_WIDTH + barWidth * (index + 1), height - BORDER_WIDTH);
    }

    private static Rect getColorBlockRect(int index, int width, int height) {
        int blockCenterX = (width / 5) * (index % 4 + 1);
        return new Rect(blockCenterX - width / 10, height / 6,
                        blockCenterX + width / 10, height / 3);
    }

    private void testGetImage(
            final String res, int width, int height, String mimeType, int rotation,
            int imageCount, int primary, boolean useGrid, boolean checkColor)
                    throws Exception {
        Stopwatch timer = new Stopwatch();
        MediaExtractor extractor = null;
        AssetFileDescriptor afd = null;
        InputStream inputStream = null;
        Preconditions.assertTestFileExists(mInpPrefix + res);

        try {
            setDataSourceFd(res);

            // Verify image related meta keys.
            String hasImage = mRetriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_HAS_IMAGE);
            assertTrue("No images found in res " + res, "yes".equals(hasImage));
            assertEquals("Wrong width", width,
                    Integer.parseInt(mRetriever.extractMetadata(
                            MediaMetadataRetriever.METADATA_KEY_IMAGE_WIDTH)));
            assertEquals("Wrong height", height,
                    Integer.parseInt(mRetriever.extractMetadata(
                            MediaMetadataRetriever.METADATA_KEY_IMAGE_HEIGHT)));
            assertEquals("Wrong rotation", rotation,
                    Integer.parseInt(mRetriever.extractMetadata(
                            MediaMetadataRetriever.METADATA_KEY_IMAGE_ROTATION)));
            assertEquals("Wrong image count", imageCount,
                    Integer.parseInt(mRetriever.extractMetadata(
                            MediaMetadataRetriever.METADATA_KEY_IMAGE_COUNT)));
            assertEquals("Wrong primary index", primary,
                    Integer.parseInt(mRetriever.extractMetadata(
                            MediaMetadataRetriever.METADATA_KEY_IMAGE_PRIMARY)));
            assertEquals("Wrong mime type", mimeType,
                    mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE));

            if (checkColor) {
                Bitmap bitmap = null;
                // For each image in the image collection, check the 7 color bars' color.
                // Also check the position of the color block, which should move left-to-right
                // with the index.
                for (int imageIndex = 0; imageIndex < imageCount; imageIndex++) {
                    timer.start();
                    bitmap = mRetriever.getImageAtIndex(imageIndex);
                    assertNotNull("Failed to retrieve image at index " + imageIndex, bitmap);
                    timer.end();
                    timer.printDuration("getImageAtIndex");

                    for (int barIndex = 0; barIndex < COLOR_BARS.length; barIndex++) {
                        Rect r = getColorBarRect(barIndex, width, height);
                        assertTrue("Color bar " + barIndex +
                                " for image " + imageIndex + " doesn't match",
                                approxEquals(COLOR_BARS[barIndex], Color.valueOf(
                                        bitmap.getPixel(r.centerX(), r.centerY()))));
                    }

                    Rect r = getColorBlockRect(imageIndex, width, height);
                    assertTrue("Color block for image " + imageIndex + " doesn't match",
                            approxEquals(COLOR_BLOCK, Color.valueOf(
                                    bitmap.getPixel(r.centerX(), height - r.centerY()))));
                    bitmap.recycle();
                }

                // Check the color block position on the primary image.
                Rect r = getColorBlockRect(primary, width, height);

                timer.start();
                bitmap = mRetriever.getPrimaryImage();
                timer.end();
                timer.printDuration("getPrimaryImage");

                assertTrue("Color block for primary image doesn't match",
                        approxEquals(COLOR_BLOCK, Color.valueOf(
                                bitmap.getPixel(r.centerX(), height - r.centerY()))));
                bitmap.recycle();

                // Check the color block position on the bitmap decoded by BitmapFactory.
                // This should match the primary image as well.
                inputStream = new FileInputStream(mInpPrefix + res);
                bitmap = BitmapFactory.decodeStream(inputStream);
                assertTrue("Color block for bitmap decoding doesn't match",
                        approxEquals(COLOR_BLOCK, Color.valueOf(
                                bitmap.getPixel(r.centerX(), height - r.centerY()))));
                bitmap.recycle();
            }

            // Check the grid configuration related keys.
            if (useGrid) {
                extractor = new MediaExtractor();
                afd = getAssetFileDescriptorFor(res);
                extractor.setDataSource(
                        afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                MediaFormat format = extractor.getTrackFormat(0);
                int tileWidth = format.getInteger(MediaFormat.KEY_TILE_WIDTH);
                int tileHeight = format.getInteger(MediaFormat.KEY_TILE_HEIGHT);
                int gridRows = format.getInteger(MediaFormat.KEY_GRID_ROWS);
                int gridCols = format.getInteger(MediaFormat.KEY_GRID_COLUMNS);
                assertTrue("Wrong tile width or grid cols",
                        ((width + tileWidth - 1) / tileWidth) == gridCols);
                assertTrue("Wrong tile height or grid rows",
                        ((height + tileHeight - 1) / tileHeight) == gridRows);
            }
        } catch (IOException e) {
            fail("Unable to open file");
        } finally {
            if (extractor != null) {
                extractor.release();
            }
            if (afd != null) {
                afd.close();
            }
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }

    private void copyMediaFile() {
        InputStream inputStream = null;
        FileOutputStream outputStream = null;
        Preconditions.assertTestFileExists(mInpPrefix + "testvideo.3gp");
        String outputPath = new File(
            Environment.getExternalStorageDirectory(), TEST_MEDIA_FILE).getAbsolutePath();
        try {
            inputStream = new FileInputStream(mInpPrefix + "testvideo.3gp");
            outputStream = new FileOutputStream(outputPath);
            copy(inputStream, outputStream);
        } catch (Exception e) {

        }finally {
            closeQuietly(inputStream);
            closeQuietly(outputStream);
        }
    }

    private int copy(InputStream in, OutputStream out) throws IOException {
        int total = 0;
        byte[] buffer = new byte[8192];
        int c;
        while ((c = in.read(buffer)) != -1) {
            total += c;
            out.write(buffer, 0, c);
        }
        return total;
    }

    private void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (RuntimeException rethrown) {
                throw rethrown;
            } catch (Exception ignored) {
            }
        }
    }

    private class Stopwatch {
        private long startTimeMs;
        private long endTimeMs;
        private boolean isStartCalled;

        public Stopwatch() {
            startTimeMs = endTimeMs = 0;
            isStartCalled = false;
        }

        public void start() {
            startTimeMs = System.currentTimeMillis();
            isStartCalled = true;
        }

        public void end() {
            endTimeMs = System.currentTimeMillis();
            if (!isStartCalled) {
                Log.e(TAG, "Error: end() must be called after start()!");
                return;
            }
            isStartCalled = false;
        }

        public void printDuration(String functionName) {
            long duration = endTimeMs - startTimeMs;
            Log.i(TAG, String.format("%s() took %d ms.", functionName, duration));
        }
    }
}
