package com.orhanobut.logger;

import java.text.ParseException;

/**
 * @Author: xgj
 * @Time: 2019/8/13 10:16
 * @Description: remove file interface
 */
public interface RemoveFile {
    void removeFileBySize() throws ParseException;

    void removeFileByHistory() throws ParseException;
}
