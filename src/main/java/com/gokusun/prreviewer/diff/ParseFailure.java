package com.gokusun.prreviewer.diff;

/**
 * 一个文件解析失败的记录。
 *
 * @param path 失败文件的展示路径；路径本身也解析不出来时是占位文案
 * @param reason 失败原因
 */
public record ParseFailure(String path, String reason) {}
