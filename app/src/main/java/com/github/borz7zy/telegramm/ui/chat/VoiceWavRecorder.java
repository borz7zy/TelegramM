package com.github.borz7zy.telegramm.ui.chat;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.SystemClock;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class VoiceWavRecorder {

    public interface OnLevelListener {
        void onLevel(int level0to100);
    }

    private final int sampleRate;
    private final File outFile;
    private final OnLevelListener onLevel;

    private AudioRecord audioRecord;
    private Thread worker;
    private volatile boolean running = false;
    private volatile boolean paused = false;

    private FileOutputStream fos;
    private long dataBytesWritten = 0;

    private final long emitEveryMs = 50;

    public VoiceWavRecorder(int sampleRate, File outFile, OnLevelListener onLevel) {
        this.sampleRate = sampleRate;
        this.outFile = outFile;
        this.onLevel = onLevel;
    }

    public void start() {
        if (running) return;

        int channelConfig = AudioFormat.CHANNEL_IN_MONO;
        int format = AudioFormat.ENCODING_PCM_16BIT;

        int minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, format);
        if (minBuf <= 0) throw new IllegalStateException("Bad min buffer: " + minBuf);

        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                format,
                minBuf * 2
        );
        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            throw new IllegalStateException("AudioRecord not initialized");
        }

        try {
            fos = new FileOutputStream(outFile);
            writeWavHeaderPlaceholder(fos, sampleRate, 1, 16);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        audioRecord.startRecording();
        running = true;

        worker = new Thread(() -> {
            short[] buffer = new short[minBuf / 2];
            byte[] bytes = new byte[buffer.length * 2];

            long lastEmit = 0;

            while (running) {
                if (paused) {
                    SystemClock.sleep(20);
                    continue;
                }

                int read = audioRecord.read(buffer, 0, buffer.length);
                if (read <= 0) continue;

                double sum = 0;
                for (int i = 0; i < read; i++) {
                    double s = buffer[i];
                    sum += s * s;
                }
                double rms = Math.sqrt(sum / read);
                int level = rmsToUiLevel(rms);

                long now = SystemClock.uptimeMillis();
                if (onLevel != null && now - lastEmit >= emitEveryMs) {
                    lastEmit = now;
                    onLevel.onLevel(level);
                }

                shortsToBytesLE(buffer, read, bytes);
                try {
                    fos.write(bytes, 0, read * 2);
                    dataBytesWritten += (long) read * 2;
                } catch (IOException e) {
                    running = false;
                }
            }
        }, "VoiceWavRecorder");

        worker.start();
    }

    public void pause() {
        paused = true;
    }

    public void resume() {
        paused = false;
    }

    public void stopAndFinalize() {
        running = false;

        if (worker != null) {
            try { worker.join(500); } catch (InterruptedException ignored) {}
            worker = null;
        }

        if (audioRecord != null) {
            try { audioRecord.stop(); } catch (Exception ignored) {}
            audioRecord.release();
            audioRecord = null;
        }

        if (fos != null) {
            try {
                fos.flush();
                fos.getFD().sync();
                fos.close();
            } catch (IOException ignored) {}
            fos = null;
        }

        try {
            WavHeader.fixHeader(outFile, dataBytesWritten, sampleRate, 1, 16);
        } catch (IOException ignored) {}
    }

    private static int rmsToUiLevel(double rms) {
        double norm = Math.max(rms / 32768.0, 1e-9);
        double db = 20.0 * Math.log10(norm);
        double clamped = Math.max(-60.0, Math.min(0.0, db));
        int level = (int) Math.round((clamped + 60.0) / 60.0 * 100.0);
        if (level < 0) return 0;
        return Math.min(level, 100);
    }

    private static void shortsToBytesLE(short[] in, int len, byte[] out) {
        for (int i = 0; i < len; i++) {
            short v = in[i];
            out[i * 2] = (byte) (v & 0xFF);
            out[i * 2 + 1] = (byte) ((v >> 8) & 0xFF);
        }
    }

    private static void writeWavHeaderPlaceholder(FileOutputStream fos, int sampleRate, int channels, int bitsPerSample) throws IOException {
        byte[] h = new byte[44];

        h[0] = 'R'; h[1] = 'I'; h[2] = 'F'; h[3] = 'F';
        h[8] = 'W'; h[9] = 'A'; h[10] = 'V'; h[11] = 'E';
        h[12] = 'f'; h[13] = 'm'; h[14] = 't'; h[15] = ' ';
        putLE32(h, 16, 16);
        putLE16(h, 20, (short) 1);
        putLE16(h, 22, (short) channels);
        putLE32(h, 24, sampleRate);

        int byteRate = sampleRate * channels * (bitsPerSample / 8);
        putLE32(h, 28, byteRate);

        short blockAlign = (short) (channels * (bitsPerSample / 8));
        putLE16(h, 32, blockAlign);

        putLE16(h, 34, (short) bitsPerSample);

        h[36] = 'd'; h[37] = 'a'; h[38] = 't'; h[39] = 'a';

        fos.write(h, 0, h.length);
    }

    private static void putLE16(byte[] b, int off, short v) {
        b[off] = (byte) (v & 0xFF);
        b[off + 1] = (byte) ((v >> 8) & 0xFF);
    }

    private static void putLE32(byte[] b, int off, int v) {
        b[off] = (byte) (v & 0xFF);
        b[off + 1] = (byte) ((v >> 8) & 0xFF);
        b[off + 2] = (byte) ((v >> 16) & 0xFF);
        b[off + 3] = (byte) ((v >> 24) & 0xFF);
    }

    static class WavHeader {
        static void fixHeader(File f, long dataBytes, int sampleRate, int channels, int bitsPerSample) throws IOException {
            java.io.RandomAccessFile raf = new java.io.RandomAccessFile(f, "rw");
            try {
                long fileSize = 44 + dataBytes;
                long riffChunkSize = fileSize - 8;

                raf.seek(4);
                writeLE32(raf, (int) riffChunkSize);

                raf.seek(40);
                writeLE32(raf, (int) dataBytes);

                raf.seek(24);
                writeLE32(raf, sampleRate);

                raf.seek(22);
                writeLE16(raf, (short) channels);

                int byteRate = sampleRate * channels * (bitsPerSample / 8);
                raf.seek(28);
                writeLE32(raf, byteRate);

                short blockAlign = (short) (channels * (bitsPerSample / 8));
                raf.seek(32);
                writeLE16(raf, blockAlign);

                raf.seek(34);
                writeLE16(raf, (short) bitsPerSample);
            } finally {
                raf.close();
            }
        }

        static void writeLE16(java.io.RandomAccessFile raf, short v) throws IOException {
            raf.write(v & 0xFF);
            raf.write((v >> 8) & 0xFF);
        }

        static void writeLE32(java.io.RandomAccessFile raf, int v) throws IOException {
            raf.write(v & 0xFF);
            raf.write((v >> 8) & 0xFF);
            raf.write((v >> 16) & 0xFF);
            raf.write((v >> 24) & 0xFF);
        }
    }
}
