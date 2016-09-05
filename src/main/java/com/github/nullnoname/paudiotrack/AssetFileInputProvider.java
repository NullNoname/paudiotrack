/*
 * This is free and unencumbered software released into the public domain.
 *
 * Anyone is free to copy, modify, publish, use, compile, sell, or
 * distribute this software, either in source code form or as a compiled
 * binary, for any purpose, commercial or non-commercial, and by any
 * means.
 *
 * In jurisdictions that recognize copyright laws, the author or authors
 * of this software dedicate any and all copyright interest in the
 * software to the public domain. We make this dedication for the benefit
 * of the public at large and to the detriment of our heirs and
 * successors. We intend this dedication to be an overt act of
 * relinquishment in perpetuity of all present and future rights to this
 * software under copyright law.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 * For more information, please refer to <http://unlicense.org/>
 */
package com.github.nullnoname.paudiotrack;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.content.res.Resources;
import paulscode.sound.DefaultFileInputProvider;
import paulscode.sound.FilenameURL;

/**
 * The AssetFileInputProvider class loads files from Android assets
 * if the URL starts with "file:///android_asset/".
 * Other URLs are loaded normally via DefaultFileInputProvider.
 *
 * License of this class is Unlicense. For more information, please refer to http://unlicense.org/.
 * @author NullNoname
 */
public class AssetFileInputProvider extends DefaultFileInputProvider {
	/** Asset prefix when creating a URL */
	public static final String URL_ASSET_PREFIX = "file:///android_asset/";
	/** Asset path prefix when comparing */
	public static final String URL_PATH_ASSET_PREFIX = "/android_asset/";

	/**
	 * Check if the URL is android_asset directory
	 * @param url URL
	 * @return true if it is an android_asset URL
	 */
	public static boolean isAssetURL(URL url) {
		if(url == null) return false;
		return isAssetURL(url.getPath());
	}

	/**
	 * Check if the URL String is android_asset directory
	 * @param s URL String
	 * @return true if it is an android_asset URL
	 */
	public static boolean isAssetURL(String s) {
		if(s == null) return false;
		return s.startsWith(URL_PATH_ASSET_PREFIX);
	}

	/**
	 * Get the file path in an asset URL
	 * @param url URL
	 * @return File path
	 */
	public static String getAssetFilename(URL url) {
		return getAssetFilename(url.getPath());
	}

	/**
	 * Get the file path in an asset URL String
	 * @param s URL String
	 * @return File path
	 */
	public static String getAssetFilename(String s) {
		return s.substring(URL_PATH_ASSET_PREFIX.length());
	}

	/**
	 * Create a new asset URL
	 * @param path File path
	 * @return Asset URL
	 * @throws IllegalArgumentException If the created URL is invalid
	 */
	public static URL createAssetURL(String path) {
		try {
			return new URL(URL_ASSET_PREFIX+path);
		} catch (MalformedURLException e) {
			throw new IllegalArgumentException("Invalid URL", e);
		}
	}

	/** AssetManager */
	private AssetManager am;

	/**
	 * Constructor
	 * @param am AssetManager
	 */
	public AssetFileInputProvider(AssetManager am) {
		this.am = am;
	}
	/**
	 * Constructor
	 * @param r Resources (Resources#getAssets() is used to retrieve the AssetManager)
	 */
	public AssetFileInputProvider(Resources r) {
		this.am = r.getAssets();
	}
	/**
	 * Constructor
	 * @param c Context (Context#getAssets() is used to retrieve the AssetManager)
	 */
	public AssetFileInputProvider(Context c) {
		this.am = c.getAssets();
	}

	@Override
	public InputStream openStream(FilenameURL filenameURL) throws IOException {
		if(isAssetURL(filenameURL.getURL())) {
			return am.open(getAssetFilename(filenameURL.getURL()));
		}
		return super.openStream(filenameURL);
	}

	@Override
	public int getContentLength(FilenameURL filenameURL) {
		if(isAssetURL(filenameURL.getURL())) {
			try {
				String fileName = getAssetFilename(filenameURL.getURL());
				AssetFileDescriptor afd = am.openFd(fileName);
				return (int)afd.getLength();
			} catch (Exception e) {
				return -1;
			}
		}
		return super.getContentLength(filenameURL);
	}
}
