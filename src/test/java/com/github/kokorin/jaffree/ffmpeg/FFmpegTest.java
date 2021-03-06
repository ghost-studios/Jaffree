package com.github.kokorin.jaffree.ffmpeg;

import com.github.kokorin.jaffree.SizeUnit;
import com.github.kokorin.jaffree.StackTraceMatcher;
import com.github.kokorin.jaffree.StreamType;
import com.github.kokorin.jaffree.ffprobe.FFprobe;
import com.github.kokorin.jaffree.ffprobe.FFprobeResult;
import com.github.kokorin.jaffree.ffprobe.Stream;
import org.junit.*;
import org.junit.rules.ExpectedException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class FFmpegTest {
    public static Path BIN;
    public static Path SAMPLES = Paths.get("target/samples");
    public static Path VIDEO_MP4 = SAMPLES.resolve("MPEG-4/video.mp4");
    public static Path SMALL_FLV = SAMPLES.resolve("FLV/zelda.flv");
    public static Path SMALL_MP4 = SAMPLES.resolve("MPEG-4/turn-on-off.mp4");
    public static Path ERROR_MP4 = SAMPLES.resolve("non_existent.mp4");
    public static Path TRANSPORT_VOB = SAMPLES.resolve("MPEG-VOB/transport-stream/capture.neimeng");

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @BeforeClass
    public static void setUp() throws Exception {
        String ffmpegHome = System.getProperty("FFMPEG_BIN");
        if (ffmpegHome == null) {
            ffmpegHome = System.getenv("FFMPEG_BIN");
        }
        Assert.assertNotNull("Nor command line property, neither system variable FFMPEG_BIN is set up", ffmpegHome);
        BIN = Paths.get(ffmpegHome);

        Assert.assertTrue("Sample videos weren't found: " + SAMPLES.toAbsolutePath(), Files.exists(SAMPLES));
    }

    @Test
    public void generics() {
        UrlOutput urlOutput = UrlOutput.toPath(Paths.get("non_important.flv"))
                .setOutputPosition(123)
                .setFormat("nut")
                .addMap(2)
                .setOutput(null);

        FrameOutput frameOutput = FrameOutput.withConsumer(null)
                .setOutputPosition(123)
                .setFormat("nut")
                .addMap(2)
                .setOutput(null);

        NullOutput nullOutput = new NullOutput()
                .setOutputPosition(123)
                .setFormat("nut")
                .addMap(2)
                .setOutput(null);

        UrlInput urlInput = UrlInput.fromUrl(null)
                .setStreamLoop(1)
                .addArgument("arg1")
                .setInput("");

        FrameInput frameInput = FrameInput.withProducer(null)
                .setFrameOrderingBuffer(100)
                .setFrameRate(30)
                .setStreamLoop(1)
                .addArgument("arg1")
                .setInput("");
    }

    @Test
    public void testSimpleCopy() throws Exception {
        Path tempDir = Files.createTempDirectory("jaffree");
        Path outputPath = tempDir.resolve(VIDEO_MP4.getFileName());

        FFmpegResult result = FFmpeg.atPath(BIN)
                .addInput(UrlInput.fromPath(VIDEO_MP4))
                .addOutput(UrlOutput
                        .toPath(outputPath)
                        .copyAllCodecs())
                .execute();

        Assert.assertNotNull(result);
    }

    // For this test to pass ffmpeg must be added to Operation System PATH environment variable
    @Test
    public void testEnvPath() throws Exception {
        Path tempDir = Files.createTempDirectory("jaffree");
        Path outputPath = tempDir.resolve(VIDEO_MP4.getFileName());

        FFmpegResult result = FFmpeg.atPath()
                .addInput(UrlInput.fromPath(VIDEO_MP4))
                .addOutput(UrlOutput
                        .toPath(outputPath)
                        .copyAllCodecs())
                .execute();

        Assert.assertNotNull(result);
    }

    @Test
    public void testOutputAdditionalOption() throws Exception {
        Path tempDir = Files.createTempDirectory("jaffree");
        Path outputPath = tempDir.resolve("test.mp3");

        FFmpegResult result = FFmpeg.atPath(BIN)
                .addInput(UrlInput.fromPath(VIDEO_MP4))
                .addOutput(UrlOutput
                        .toPath(outputPath)
                        .setCodec(StreamType.AUDIO, "mp3")
                        .disableStream(StreamType.VIDEO)
                        .addArguments("-id3v2_version", "3")
                )
                .execute();

        Assert.assertNotNull(result);

        FFprobeResult probe = FFprobe.atPath(BIN)
                .setInputPath(outputPath)
                .setShowStreams(true)
                .execute();

        Assert.assertNotNull(probe);
        Assert.assertEquals(1, probe.getStreams().size());
        Assert.assertEquals(StreamType.AUDIO, probe.getStreams().get(0).getCodecType());
    }

    @Test
    public void testProgress() throws Exception {
        Path tempDir = Files.createTempDirectory("jaffree");
        Path outputPath = tempDir.resolve("test.mkv");

        final AtomicLong counter = new AtomicLong();

        ProgressListener listener = new ProgressListener() {
            @Override
            public void onProgress(FFmpegProgress progress) {
                counter.incrementAndGet();
            }
        };

        FFmpegResult result = FFmpeg.atPath(BIN)
                .addInput(UrlInput.fromPath(SMALL_FLV))
                .addOutput(UrlOutput.toPath(outputPath))
                .setProgressListener(listener)
                .execute();

        Assert.assertNotNull(result);
        Assert.assertTrue(counter.get() > 0);
    }

    @Test
    public void testProgress2() throws Exception {
        Path tempDir = Files.createTempDirectory("jaffree");
        Path outputPath = tempDir.resolve("test.flv");

        final AtomicLong counter = new AtomicLong();

        ProgressListener listener = new ProgressListener() {
            @Override
            public void onProgress(FFmpegProgress progress) {
                counter.incrementAndGet();
            }
        };

        FFmpegResult result = FFmpeg.atPath(BIN)
                .addInput(UrlInput.fromPath(SMALL_MP4))
                .addOutput(UrlOutput.toPath(outputPath))
                .setProgressListener(listener)
                .execute();

        Assert.assertNotNull(result);
        Assert.assertTrue(counter.get() > 0);
    }

    @Test
    public void testDuration() throws Exception {
        Path tempDir = Files.createTempDirectory("jaffree");
        Path outputPath = tempDir.resolve(VIDEO_MP4.getFileName());

        FFmpegResult result = FFmpeg.atPath(BIN)
                .addInput(UrlInput
                        .fromPath(VIDEO_MP4)
                        .setDuration(10, TimeUnit.SECONDS)
                )
                .addOutput(UrlOutput
                        .toPath(outputPath)
                        .copyAllCodecs())
                .execute();

        Assert.assertNotNull(result);

        double outputDuration = getDuration(outputPath);
        Assert.assertEquals(10.0, outputDuration, 0.1);

        result = FFmpeg.atPath(BIN)
                .addInput(UrlInput
                        .fromPath(VIDEO_MP4)
                        .setDuration(1. / 6., TimeUnit.MINUTES)
                )
                .setOverwriteOutput(true)
                .addOutput(UrlOutput
                        .toPath(outputPath)
                        .copyAllCodecs())
                .execute();

        Assert.assertNotNull(result);

        outputDuration = getDuration(outputPath);
        Assert.assertEquals(10.0, outputDuration, 0.1);
    }

    @Test
    public void testStopping() throws Exception {
        Path tempDir = Files.createTempDirectory("jaffree");
        Path outputPath = tempDir.resolve(VIDEO_MP4.getFileName());

        FFmpeg ffmpeg = FFmpeg.atPath(BIN)
                .addInput(UrlInput
                        .fromPath(VIDEO_MP4)
                )
                .addOutput(UrlOutput.toPath(outputPath));

        Future<FFmpegResult> futureResult = ffmpeg.executeAsync();

        Thread.sleep(1_000);

        boolean cancelled = futureResult.cancel(true);
        Assert.assertTrue(cancelled);

        Thread.sleep(1_000);
    }

    @Test
    public void testOutputPosition() throws Exception {
        Path tempDir = Files.createTempDirectory("jaffree");
        Path outputPath = tempDir.resolve(VIDEO_MP4.getFileName());

        FFmpegResult result = FFmpeg.atPath(BIN)
                .addInput(UrlInput.fromPath(VIDEO_MP4))
                .addOutput(UrlOutput
                        .toPath(outputPath)
                        .copyAllCodecs()
                        .setOutputPosition(15, TimeUnit.SECONDS)
                )
                .execute();

        Assert.assertNotNull(result);

        double outputDuration = getDuration(outputPath);
        Assert.assertEquals(15.0, outputDuration, 0.1);
    }

    @Test
    public void testSizeLimit() throws Exception {
        Path tempDir = Files.createTempDirectory("jaffree");
        Path outputPath = tempDir.resolve(VIDEO_MP4.getFileName());

        FFmpegResult result = FFmpeg.atPath(BIN)
                .addInput(UrlInput.fromPath(VIDEO_MP4))
                .addOutput(UrlOutput
                        .toPath(outputPath)
                        .copyAllCodecs()
                        .setSizeLimit(1, SizeUnit.MB)
                )
                .execute();

        Assert.assertNotNull(result);

        long outputSize = Files.size(outputPath);
        Assert.assertTrue(outputSize > 900_000);
        Assert.assertTrue(outputSize < 1_100_000);
    }

    @Test
    public void testPosition() throws Exception {
        Path tempDir = Files.createTempDirectory("jaffree");
        Path outputPath = tempDir.resolve(VIDEO_MP4.getFileName());

        FFmpegResult result = FFmpeg.atPath(BIN)
                .addInput(UrlInput
                        .fromPath(VIDEO_MP4)
                        .setPosition(10, TimeUnit.SECONDS)
                )
                .addOutput(UrlOutput
                        .toPath(outputPath)
                        .copyAllCodecs())
                .execute();

        Assert.assertNotNull(result);

        double inputDuration = getDuration(VIDEO_MP4);
        double outputDuration = getDuration(outputPath);

        Assert.assertEquals(inputDuration - 10, outputDuration, 0.5);
    }

    @Test
    public void testPositionNegative() throws Exception {
        Path tempDir = Files.createTempDirectory("jaffree");
        Path outputPath = tempDir.resolve(VIDEO_MP4.getFileName());

        FFmpegResult result = FFmpeg.atPath(BIN)
                .addInput(UrlInput
                        .fromPath(VIDEO_MP4)
                        .setPositionEof(-7, TimeUnit.SECONDS)
                )
                .addOutput(UrlOutput
                        .toPath(outputPath)
                        .copyAllCodecs())
                .execute();

        Assert.assertNotNull(result);

        double outputDuration = getDuration(outputPath);

        Assert.assertEquals(7.0, outputDuration, 0.5);
    }

    @Test
    public void testNullOutput() throws Exception {
        final AtomicLong time = new AtomicLong();

        FFmpegResult result = FFmpeg.atPath(BIN)
                .addInput(UrlInput
                        .fromPath(VIDEO_MP4)
                )
                .addOutput(
                        new NullOutput()
                )
                .setOverwriteOutput(true)
                .setProgressListener(new ProgressListener() {
                    @Override
                    public void onProgress(FFmpegProgress progress) {
                        time.set(progress.getTimeMillis());
                    }
                })
                .execute();

        Assert.assertNotNull(result);
        Assert.assertTrue(time.get() > 165_000);
    }

    @Test
    public void testMap() throws Exception {
        Path tempDir = Files.createTempDirectory("jaffree");
        Path outputPath = tempDir.resolve(VIDEO_MP4.getFileName());

        FFmpegResult result = FFmpeg.atPath(BIN)
                .addInput(UrlInput.fromPath(VIDEO_MP4))
                .addOutput(UrlOutput
                        .toPath(outputPath)
                        .copyAllCodecs()
                        .addMap(0, StreamType.AUDIO)
                        .addMap(0, StreamType.AUDIO)
                        .addMap(0, StreamType.VIDEO)
                )
                .execute();

        Assert.assertNotNull(result);

        FFprobeResult probe = FFprobe.atPath(BIN)
                .setShowStreams(true)
                .setInputPath(outputPath)
                .execute();
        Assert.assertNull(probe.getError());

        List<Stream> streamTypes = probe.getStreams();

        Assert.assertEquals(3, streamTypes.size());
        Assert.assertEquals(StreamType.AUDIO, streamTypes.get(0).getCodecType());
        Assert.assertEquals(StreamType.AUDIO, streamTypes.get(1).getCodecType());
        Assert.assertEquals(StreamType.VIDEO, streamTypes.get(2).getCodecType());
    }

    @Test
    @Ignore("This test requires manual verification of result frames")
    public void testAlpha() throws Exception {
        Path videoWithAlpha = SAMPLES.resolve("Alpha/Biking_Girl_Alpha.mov");

        FrameConsumer frameConsumer = new FrameConsumer() {
            @Override
            public void consumeStreams(List<com.github.kokorin.jaffree.ffmpeg.Stream> streams) {
                System.out.println(streams + "");
            }

            @Override
            public void consume(Frame frame) {
                System.out.println(frame + "");
            }
        };

        FFmpegResult result = FFmpeg.atPath(BIN)
                .addInput(UrlInput
                        .fromPath(videoWithAlpha)
                        .setDuration(1_000)
                )
                .addOutput(FrameOutput
                        .withConsumerAlpha(frameConsumer)
                        .disableStream(StreamType.AUDIO)
                )
                .execute();

        Assert.assertNotNull(result);
    }


    @Test
    public void testExceptionIsThrownIfFfmpegExitsWithError() {
        expectedException.expect(new StackTraceMatcher("No such file or directory"));

        FFmpegResult result = FFmpeg.atPath(BIN)
                .addInput(UrlInput.fromPath(Paths.get("nonexistent.mp4")))
                //.addOutput()
                .execute();
    }

    private static double getDuration(Path path) {
        FFprobeResult probe = FFprobe.atPath(BIN)
                .setShowStreams(true)
                .setInputPath(path)
                .execute();
        Assert.assertNull(probe.getError());

        double result = 0.0;
        for (Stream stream : probe.getStreams()) {
            result = Math.max(result, stream.getDuration());
        }

        return result;
    }
}
