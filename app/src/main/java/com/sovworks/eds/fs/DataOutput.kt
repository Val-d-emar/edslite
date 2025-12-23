package com.sovworks.eds.fs

import java.io.IOException

interface DataOutput {
    /**
     * Writes the specified byte to this file. The write starts at the current file pointer.
     *
     * @param b the byte to be written.
     * @throws IOException
     */
    @Throws(IOException::class)
    fun write(b: Int)

    /**
     * Writes len bytes from the specified byte array starting at offset off to this file.
     *
     * @param b the data.
     * @param off - the start offset in the data.
     * @param len - the number of bytes to write.
     * @throws IOException
     */
    @Throws(IOException::class)
    fun write(b: ByteArray?, off: Int, len: Int)

    @Throws(IOException::class)
    fun flush()
}
