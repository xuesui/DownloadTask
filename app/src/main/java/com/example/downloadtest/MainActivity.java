package com.example.downloadtest;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.File;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
    private ProgressBar progressBar;
    private int lastProgress;
    private int progress;
    private boolean isPaused=false;
    private boolean isCanceled=false;
    ExecutorService cache= Executors.newCachedThreadPool();

    private DownloadListener listener=new DownloadListener() {
        @Override
        public void onSuccess() {
            Toast.makeText(MainActivity.this, "下载成功", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onPaused() {
            Toast.makeText(MainActivity.this, "下载暂停", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onCanceled() {
            Toast.makeText(MainActivity.this, "下载取消", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onFailed() {
            Toast.makeText(MainActivity.this, "下载失败", Toast.LENGTH_SHORT).show();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        progressBar=(ProgressBar)findViewById(R.id.progress_bar);
        Button startDownload=(Button)findViewById(R.id.start_download);
        Button pauseDownload=(Button)findViewById(R.id.pause_download);
        Button cancelDownload=(Button)findViewById(R.id.cancel_download);
        startDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startDownload();
            }
        });
        pauseDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pauseDownload();
                listener.onPaused();
            }
        });
        cancelDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancelDownload();
                listener.onCanceled();
            }
        });

        if (ContextCompat.checkSelfPermission(MainActivity.this,Manifest.permission.WRITE_EXTERNAL_STORAGE)!= PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(MainActivity.this,new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},1);
        }
    }

    public void downloadTask() {
        File file = null;
        InputStream is = null;
        RandomAccessFile savedFile = null;
        try {
            long downloadedLength = 0;
            URL url = new URL("http://cdn7.mydown.com/cloudmusicsetup_2.5.2.197409.exe");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            String downloadUrl = connection.getURL().getFile();
            String fileName = downloadUrl.substring(downloadUrl.lastIndexOf("/"));
            String directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath();
            file = new File(directory + fileName);

            if (file.exists()) {
                downloadedLength = file.length();
            }

            long contentLength = getContentLength(downloadUrl);

            if (contentLength==0){
                listener.onFailed();
            }else if (contentLength==downloadedLength){
                listener.onSuccess();
                cache.shutdown();
            }

            OkHttpClient client =new OkHttpClient();
            Request request=new Request.Builder().addHeader("Range","bytes="+downloadedLength+"-").url(downloadUrl).build();
            Response response=client.newCall(request).execute();
            if (response!=null){
                is=response.body().byteStream();
                savedFile=new RandomAccessFile(file,"rw");
                savedFile.seek(downloadedLength);
                byte[] b=new byte[1024];
                int total=0;
                int length;
                while ((length=is.read(b))!=-1){
                    if (isPaused){
                        break;
                    }else if (isCanceled){
                        break;
                    }else {
                        total+=length;
                        savedFile.write(b,0,length);
                        progress=(int)((total+downloadedLength)*100/contentLength);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                onProgressUpdate();
                            }
                        });
                    }
                }
                downloadedLength=file.length();
                if (downloadedLength==contentLength) {
                    listener.onSuccess();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            try{
                if (is!=null){
                    is.close();
                }
                if (savedFile!=null){
                    savedFile.close();
                }
                if (isCanceled&&file!=null){
                    file.delete();
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    private long getContentLength(String downloadUrl) throws Exception{
        OkHttpClient client=new OkHttpClient();
        Request request=new Request.Builder().url(downloadUrl).build();
        Response response=client.newCall(request).execute();
        if (response!=null&&response.isSuccessful()){
            long contentLength=response.body().contentLength();
            response.body().close();
            return contentLength;
        }

        return 0;
    }

    public void onProgressUpdate(){
        if (progress>lastProgress){
            progressBar.post(new Runnable() {
                @Override
                public void run() {
                    progressBar.setProgress(progress);
                }
            });

        }
    }

    public void pauseDownload(){
        isPaused=true;
    }

    public void cancelDownload(){
        isCanceled=true;
    }

    public void startDownload(){
        Runnable runnable =new Runnable() {
            @Override
            public void run() {
                downloadTask();
                if (isCanceled){
                    cache.shutdown();
                }
            }
        };
        cache.execute(runnable);
    }

}


