package com.ksy.recordlib.service.core;

import android.util.Log;

import com.ksy.recordlib.service.util.Constants;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * Created by eflakemac on 15/6/26.
 */
public class KsyRecordSender {
    //	@AccessedByNative
    public long mNativeRTMP;

    private Thread worker;
    private String mUrl;
    private boolean connected = false;

    private ArrayList<KSYFlvData> recordQueue;
    private Object mutex = new Object();

    private static final int FIRST_OPEN = 3;
    private static final int FROM_AUDIO = 8;
    private static final int FROM_VIDEO = 6;

    private static volatile int frame_video;
    private static volatile int frame_audio;

    private static KsyRecordSender ksyRecordSenderInstance;

    private KsyRecordSender() {
    }

    static {
        System.loadLibrary("rtmp");
        Log.i(Constants.LOG_TAG, "rtmp.so loaded");
        System.loadLibrary("ksyrtmp");
        Log.i(Constants.LOG_TAG, "ksyrtmp.so loaded");
    }


    public static KsyRecordSender getRecordInstance() {

        if (ksyRecordSenderInstance == null) {

            synchronized (KsyRecordSender.class) {

                if (ksyRecordSenderInstance == null) {
                    ksyRecordSenderInstance = new KsyRecordSender();
                }
            }
        }

        return ksyRecordSenderInstance;
    }


    public void setRecorderData(String url, int j) {

        recordQueue = new ArrayList<KSYFlvData>();

        mUrl = url;

        int i = _set_output_url(url);

        //3视频  0音频
        if (j == FIRST_OPEN) {
            int c = _open();
            Log.d(Constants.LOG_TAG, "c ==" + c + ">>i=" + i);
        }

    }


    public void start() throws IOException {

        worker = new Thread(new Runnable() {
            @Override
            public void run() {

                try {
                    cycle();

                } catch (Exception e) {
                    Log.e(Constants.LOG_TAG, "worker: thread exception. e＝" + e);
                    e.printStackTrace();
                }
            }
        });
        worker.start();
    }


    private void cycle() {

        while (true) {
            //send
            if (frame_video > 1 && frame_audio > 1) {

                KSYFlvData ksyFlv;

                synchronized (mutex) {
                    Collections.sort(recordQueue, new Comparator<KSYFlvData>() {
                        @Override
                        public int compare(KSYFlvData lhs, KSYFlvData rhs) {

                            return lhs.dts - rhs.dts;
                        }
                    });

                    ksyFlv = recordQueue.remove(0);
                }

                if (ksyFlv.type == 11) {
                    frame_video--;

                } else if (ksyFlv.type == 12) {
                    frame_audio--;
                }

                int w = _write(ksyFlv.byteBuffer, ksyFlv.byteBuffer.length);

                Log.d(Constants.LOG_TAG, " w=" + w + ">>data=" + "<<<>>>>" + ksyFlv.byteBuffer.length);

            } else {

                Log.d(Constants.LOG_TAG, "frame_video ||  frame_audio  <1 -------");
            }
        }
    }


    private void reconnect() throws Exception {

        if (connected) {
            return;
        }

        _close();

        _set_output_url(mUrl);

        int conncode = _open();

        connected = conncode == 0 ? true : false;
    }


    public void disconnect() {

        _close();

        recordQueue.clear();
    }


    //send data to server
    public synchronized void sender(KSYFlvData ksyFlvData, int k) {

        if (ksyFlvData == null) {
            return;
        }

        if (ksyFlvData.size <= 0) {
            return;
        }


        if (k == FROM_VIDEO) { //视频数据
            frame_video++;

        } else if (k == FROM_AUDIO) {//音频数据
            frame_audio++;
        }

        int time = ksyFlvData.dts;

        Log.d(Constants.LOG_TAG, "k==" + k + ">>>time=" + time + "<<<frame_video==" + frame_video + ">>>frame_audio=" + frame_audio);

        // add video data
        synchronized (mutex) {
            recordQueue.add(ksyFlvData);
        }

    }

    private native int _set_output_url(String url);

    private native int _open();

    private native int _close();

    private native int _write(byte[] buffer, int size);

}

