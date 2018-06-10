package com.example.miyatama.supertikyujin;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

public class WavFile {
    private RandomAccessFile raf; //リアルタイム処理なのでランダムアクセスファイルクラスを使用する
    private File recFile; //録音後の書き込み、読み込みようファイル
    private byte[] RIFF = {'R','I','F','F'}; //wavファイルリフチャンクに書き込むチャンクID用
    private int fileSize = 36;
    private byte[] WAVE = {'W','A','V','E'}; //WAV形式でRIFFフォーマットを使用する
    private byte[] fmt = {'f','m','t',' '}; //fmtチャンク　スペースも必要
    private int fmtSize = 16; //fmtチャンクのバイト数
    private byte[] fmtID = {1, 0}; // フォーマットID リニアPCMの場合01 00 2byte
    private short chCount = 1; //チャネルカウント モノラルなので1 ステレオなら2にする
    private int bytePerSec = SoundDefine.SAMPLING_RATE * (fmtSize / 8) * chCount; //データ速度
    private short blockSize = (short) ((fmtSize / 8) * chCount); //ブロックサイズ (Byte/サンプリングレート * チャンネル数)
    private byte[] data = {'d', 'a', 't', 'a'}; //dataチャンク
    private int dataSize = 0; //波形データのバイト数


    public void createFile(String fileName){
        this.recFile = new File(fileName);
        if(recFile.exists()){
            recFile.delete();
        }
        try{
            recFile.createNewFile();
        }catch (IOException e){
            e.printStackTrace();
        }
        try{
            raf = new RandomAccessFile(recFile, "rw");
        }catch (FileNotFoundException e){
            e.printStackTrace();;
        }
        try{
            raf.seek(0);
            raf.write(RIFF);
            raf.write(littleEndianInteger(fileSize));
            raf.write(WAVE);
            raf.write(fmt);
            raf.write(littleEndianInteger(fmtSize));
            raf.write(fmtID);
            raf.write(littleEndianShort(chCount));
            raf.write(littleEndianInteger(SoundDefine.SAMPLING_RATE)); //サンプリング周波数
            raf.write(littleEndianInteger(bytePerSec));
            raf.write(littleEndianShort(blockSize));
            short bitPerSample = 16;
            raf.write(littleEndianShort(bitPerSample));
            raf.write(data);
            raf.write(littleEndianInteger(dataSize));
        }catch (IOException e){
            e.printStackTrace();
        }
    }

    private byte[] littleEndianInteger(int i){
        byte[] buffer = new byte[4];
        buffer[0] = (byte) i;
        buffer[1] = (byte) (i >> 8);
        buffer[2] = (byte) (i >> 16);
        buffer[3] = (byte) (i >> 24);
        return buffer;
    }

    public void addBigEndianData(short[] shortData){
        try {
            raf.seek(raf.length());
            raf.write(littleEndianShorts(shortData));
        } catch (IOException e) {
            e.printStackTrace();
        }
        updateFileSize();
        updateDataSize();
    }

    private void updateFileSize(){
        fileSize = (int) (recFile.length() - 8);
        byte[] fileSizeBytes = littleEndianInteger(fileSize);
        try {
            int FILESIZE_SEEK = 4;
            raf.seek(FILESIZE_SEEK);
            raf.write(fileSizeBytes);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    private void updateDataSize(){
        dataSize = (int) (recFile.length() - 44);
        byte[] dataSizeBytes = littleEndianInteger(dataSize);
        try {
            int DATASIZE_SEEK = 40;
            raf.seek(DATASIZE_SEEK);
            raf.write(dataSizeBytes);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private byte[] littleEndianShort(short s){
        byte[] buffer = new byte[2];
        buffer[0] = (byte) s;
        buffer[1] = (byte) (s >> 8);
        return buffer;
    }

    private byte[] littleEndianShorts(short[] s){
        byte[] buffer = new byte[s.length * 2];
        int i;
        for(i = 0; i < s.length; i++){
            buffer[2 * i] = (byte) s[i];
            buffer[2 * i + 1] = (byte) (s[i] >> 8);
        }
        return buffer;
    }

    public void close(){
        try {
            raf.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
