package com.pandulapeter.campfire.data.source.local.implementation.zip

/**
 * Thrown when an archive is malformed, truncated, encrypted or uses a feature this minimal implementation does not
 * support (ZIP64, compression methods other than STORED and DEFLATE).
 */
internal class ZipException(message: String) : Exception(message)
