package com.example.downloadtest;

public interface DownloadListener {
    public void onSuccess();
    public void onPaused();
    public void onCanceled();
    public void onFailed();
}
