package com.sovworks.eds.crypto

import android.annotation.SuppressLint
import android.text.Editable
import android.text.InputFilter
import android.text.Selection
import android.text.SpanWatcher
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.util.Log
import com.sovworks.eds.android.Logger.Companion.debug
import com.sovworks.eds.android.Logger.Companion.log
import java.nio.CharBuffer
import java.util.Arrays
import java.util.IdentityHashMap
import kotlin.Int.Companion
import kotlin.math.max

class EditableSecureBuffer(sb: SecureBuffer) : Editable {
    private object GrowingArrayUtils {
        fun <T> append(current: Array<T?>, count: Int, newElement: T): Array<T?> {
            var current = current
            var count = count
            count++
            if (count >= current.size) current = current.copyOf(getNewSize(count - 1, 1))
            current[count - 1] = newElement
            return current
        }

        fun append(current: IntArray, count: Int, newElement: Int): IntArray {
            var current = current
            var count = count
            count++
            if (count >= current.size) current = current.copyOf(getNewSize(count - 1, 1))
            current[count - 1] = newElement
            return current
        }

        fun getNewSize(current: Int, adding: Int): Int {
            return max((current * 2).toDouble(), (current + adding).toDouble()).toInt()
        }
    }

    private object ArrayUtils {
        fun <T> emptyArray(kind: Class<T>): Array<T> {
            return java.lang.reflect.Array.newInstance(kind, 0) as Array<T>
        }
    }

    override fun equals(obj: Any?): Boolean {
        if (VERBOSE_LOG) debug(TAG + ": in equals")
        if (obj is EditableSecureBuffer && length == obj.length) {
            val d1 = CharArray(length)
            val d2 = d1.clone()
            try {
                getChars(0, d1.size, d1, 0)
                obj.getChars(0, d2.size, d2, 0)
                return d1.contentEquals(d2)
            } finally {
                SecureBuffer.Companion.eraseData(d1)
                SecureBuffer.Companion.eraseData(d2)
            }
        }
        return false
    }

    override fun hashCode(): Int {
        val d = CharArray(length)
        getChars(0, d.size, d, 0)
        return d.contentHashCode()
    }

    private val _sb: SecureBuffer

    /**
     * Return the char at the specified offset within the buffer.
     */
    override fun charAt(where: Int): Char {
        if (VERBOSE_LOG) debug(TAG + ": in charAt")
        val cb = _sb.charBuffer ?: return ' '
        cb.clear()
        val len = cb.capacity() - mGapLength
        if (where < 0) {
            throw IndexOutOfBoundsException("charAt: $where < 0")
        } else if (where >= len) {
            throw IndexOutOfBoundsException("charAt: $where >= length $len")
        }

        return if (where >= mGapStart) cb[where + mGapLength]
        else cb[where]
    }

    /**
     * Return the number of chars in the buffer.
     */
    override fun length(): Int {
        if (VERBOSE_LOG) debug(TAG + ": in length")
        val cb = _sb.charBuffer
        return if (cb == null) 0 else (cb.capacity() - mGapLength)
    }

    private fun resizeFor(size: Int) {
        if (VERBOSE_LOG) debug(TAG + ": in resizeFor")
        val cb = _sb.charBuffer ?: return
        cb.clear()
        val oldLength = cb.capacity()
        if (size + 1 <= oldLength) {
            return
        }

        val newText = CharArray(size * 2)
        cb[newText, 0, mGapStart]
        val newLength = newText.size
        val delta = newLength - oldLength
        val after = oldLength - (mGapStart + mGapLength)
        cb.position(oldLength - after)
        cb[newText, newLength - after, after]
        _sb.adoptData(CharBuffer.wrap(newText))

        mGapLength += delta
        if (mGapLength < 1) Exception("mGapLength < 1").printStackTrace()

        if (mSpanCount != 0) {
            for (i in 0..<mSpanCount) {
                if (mSpanStarts[i] > mGapStart) mSpanStarts[i] += delta
                if (mSpanEnds[i] > mGapStart) mSpanEnds[i] += delta
            }
            calcMax(treeRoot())
        }
    }

    private fun moveGapTo(where: Int) {
        if (VERBOSE_LOG) debug(TAG + ": in moveGapTo")
        if (where == mGapStart) return

        val atEnd = (where == length)

        val cb = _sb.charBuffer ?: return
        cb.clear()
        if (where < mGapStart) {
            val overlap = mGapStart - where
            val sub = cb.subSequence(where, where + overlap)
            cb.position(mGapStart + mGapLength - overlap)
            cb.put(sub)
        } else  /* where > mGapStart */ {
            val overlap = where - mGapStart
            val sub = cb.subSequence(where + mGapLength - overlap, where + mGapLength)
            cb.position(mGapStart)
            cb.put(sub)
        }

        if (mSpanCount != 0) {
            for (i in 0..<mSpanCount) {
                var start = mSpanStarts[i]
                var end = mSpanEnds[i]

                if (start > mGapStart) start -= mGapLength
                if (start > where) start += mGapLength
                else if (start == where) {
                    val flag = (mSpanFlags[i] and START_MASK) shr START_SHIFT

                    if (flag == POINT || (atEnd && flag == PARAGRAPH)) start += mGapLength
                }

                if (end > mGapStart) end -= mGapLength
                if (end > where) end += mGapLength
                else if (end == where) {
                    val flag = (mSpanFlags[i] and END_MASK)

                    if (flag == POINT || (atEnd && flag == PARAGRAPH)) end += mGapLength
                }

                mSpanStarts[i] = start
                mSpanEnds[i] = end
            }
            calcMax(treeRoot())
        }

        mGapStart = where
    }

    // Documentation from interface
    override fun insert(where: Int, tb: CharSequence, start: Int, end: Int): EditableSecureBuffer {
        if (VERBOSE_LOG) debug(TAG + ": in insert")
        return replace(where, where, tb, start, end)
    }

    // Documentation from interface
    override fun insert(where: Int, tb: CharSequence): EditableSecureBuffer {
        if (VERBOSE_LOG) debug(TAG + ": in insert 2")
        return replace(where, where, tb, 0, tb.length)
    }

    // Documentation from interface
    override fun delete(start: Int, end: Int): EditableSecureBuffer {
        if (VERBOSE_LOG) debug(TAG + ": in delete")
        val ret = replace(start, end, "", 0, 0)

        if (mGapLength > 2 * length) resizeFor(length)

        return ret // == this
    }

    // Documentation from interface
    override fun clear() {
        if (VERBOSE_LOG) debug(TAG + ": in clear")
        replace(0, length, "", 0, 0)
        mSpanInsertCount = 0
    }

    // Documentation from interface
    override fun clearSpans() {
        if (VERBOSE_LOG) debug(TAG + ": in clearSpans")
        for (i in mSpanCount - 1 downTo 0) {
            val what = mSpans[i]
            var ostart = mSpanStarts[i]
            var oend = mSpanEnds[i]

            if (ostart > mGapStart) ostart -= mGapLength
            if (oend > mGapStart) oend -= mGapLength

            mSpanCount = i
            mSpans[i] = null

            sendSpanRemoved(what, ostart, oend)
        }
        if (mIndexOfSpan != null) {
            mIndexOfSpan!!.clear()
        }
        mSpanInsertCount = 0
    }

    override fun toString(): String {
        val cs = CharArray(length)
        Arrays.fill(cs, ' ')
        return String(cs)
    }

    // Documentation from interface
    override fun append(text: CharSequence): EditableSecureBuffer {
        if (VERBOSE_LOG) debug(TAG + ": in append")
        val length = length
        return replace(length, length, text, 0, text.length)
    }

    /**
     * Appends the character sequence `text` and spans `what` over the appended part.
     * See [Spanned] for an explanation of what the flags mean.
     * @param text the character sequence to append.
     * @param what the object to be spanned over the appended text.
     * @param flags see [Spanned].
     * @return this `SpannableStringBuilder`.
     */
    fun append(text: CharSequence, what: Any, flags: Int): EditableSecureBuffer {
        if (VERBOSE_LOG) debug(TAG + ": in append 2")
        val start = length
        append(text)
        setSpan(what, start, length, flags)
        return this
    }

    // Documentation from interface
    override fun append(text: CharSequence, start: Int, end: Int): EditableSecureBuffer {
        if (VERBOSE_LOG) debug(TAG + ": in append 3")
        val length = length
        return replace(length, length, text, start, end)
    }

    // Documentation from interface
    override fun append(text: Char): EditableSecureBuffer {
        return append(text.toString())
    }

    // Returns true if a node was removed (so we can restart search from root)
    private fun removeSpansForChange(
        start: Int,
        end: Int,
        textIsRemoved: Boolean,
        i: Int
    ): Boolean {
        if (VERBOSE_LOG) debug(TAG + ": in removeSpansForChange")
        if ((i and 1) != 0) {
            // internal tree node
            if (resolveGap(mSpanMax[i]) >= start &&
                removeSpansForChange(start, end, textIsRemoved, leftChild(i))
            ) {
                return true
            }
        }
        if (i < mSpanCount) {
            if ((mSpanFlags[i] and Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) ==
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE && mSpanStarts[i] >= start && mSpanStarts[i] < mGapStart + mGapLength && mSpanEnds[i] >= start && mSpanEnds[i] < mGapStart + mGapLength &&  // The following condition indicates that the span would become empty
                (textIsRemoved || mSpanStarts[i] > start || mSpanEnds[i] < mGapStart)
            ) {
                mIndexOfSpan!!.remove(mSpans[i])
                removeSpan(i)
                return true
            }
            return resolveGap(mSpanStarts[i]) <= end && (i and 1) != 0 &&
                    removeSpansForChange(start, end, textIsRemoved, rightChild(i))
        }
        return false
    }

    private fun change(start: Int, end: Int, cs: CharSequence, csStart: Int, csEnd: Int) {
        if (VERBOSE_LOG) debug(TAG + ": in change")
        // Can be negative
        val replacedLength = end - start
        val replacementLength = csEnd - csStart
        val nbNewChars = replacementLength - replacedLength

        var changed = false
        for (i in mSpanCount - 1 downTo 0) {
            var spanStart = mSpanStarts[i]
            if (spanStart > mGapStart) spanStart -= mGapLength

            var spanEnd = mSpanEnds[i]
            if (spanEnd > mGapStart) spanEnd -= mGapLength

            if ((mSpanFlags[i] and Spanned.SPAN_PARAGRAPH) == Spanned.SPAN_PARAGRAPH) {
                val ost = spanStart
                val oen = spanEnd
                val clen = length

                if (spanStart > start && spanStart <= end) {
                    spanStart = end
                    while (spanStart < clen) {
                        if (spanStart > end && get(spanStart - 1) == '\n') break
                        spanStart++
                    }
                }

                if (spanEnd > start && spanEnd <= end) {
                    spanEnd = end
                    while (spanEnd < clen) {
                        if (spanEnd > end && get(spanEnd - 1) == '\n') break
                        spanEnd++
                    }
                }

                if (spanStart != ost || spanEnd != oen) {
                    setSpan(false, mSpans[i], spanStart, spanEnd, mSpanFlags[i])
                    changed = true
                }
            }

            var flags = 0
            if (spanStart == start) flags = flags or SPAN_START_AT_START
            else if (spanStart == end + nbNewChars) flags = flags or SPAN_START_AT_END
            if (spanEnd == start) flags = flags or SPAN_END_AT_START
            else if (spanEnd == end + nbNewChars) flags = flags or SPAN_END_AT_END
            mSpanFlags[i] = mSpanFlags[i] or flags
        }
        if (changed) {
            restoreInvariants()
        }

        moveGapTo(end)

        var cb: CharBuffer? = _sb.charBuffer ?: return

        if (nbNewChars >= mGapLength) {
            resizeFor(cb.capacity() + nbNewChars - mGapLength)
        }

        val textIsRemoved = replacementLength == 0
        // The removal pass needs to be done before the gap is updated in order to broadcast the
        // correct previous positions to the correct intersecting SpanWatchers
        if (replacedLength > 0) { // no need for span fixup on pure insertion
            while (mSpanCount > 0 &&
                removeSpansForChange(start, end, textIsRemoved, treeRoot())
            ) {
                // keep deleting spans as needed, and restart from root after every deletion
                // because deletion can invalidate an index.
            }
        }

        mGapStart += nbNewChars
        mGapLength -= nbNewChars

        if (mGapLength < 1) Exception("mGapLength < 1").printStackTrace()

        cb = _sb.charBuffer
        if (cb == null) return
        cb.clear()
        cb.position(start)
        cb.put(CharBuffer.wrap(cs, csStart, csEnd))

        if (replacedLength > 0) { // no need for span fixup on pure insertion
            val atEnd = (mGapStart + mGapLength == cb.capacity())

            for (i in 0..<mSpanCount) {
                val startFlag = (mSpanFlags[i] and START_MASK) shr START_SHIFT
                mSpanStarts[i] = updatedIntervalBound(
                    mSpanStarts[i], start, nbNewChars, startFlag,
                    atEnd, textIsRemoved
                )

                val endFlag = (mSpanFlags[i] and END_MASK)
                mSpanEnds[i] = updatedIntervalBound(
                    mSpanEnds[i], start, nbNewChars, endFlag,
                    atEnd, textIsRemoved
                )
            }
            restoreInvariants()
        }

        if (cs is Spanned) {
            val sp = cs
            val spans = sp.getSpans(csStart, csEnd, Any::class.java)

            for (i in spans.indices) {
                var st = sp.getSpanStart(spans[i])
                var en = sp.getSpanEnd(spans[i])

                if (st < csStart) st = csStart
                if (en > csEnd) en = csEnd

                // Add span only if this object is not yet used as a span in this string
                if (getSpanStart(spans[i]) < 0) {
                    val copySpanStart = st - csStart + start
                    val copySpanEnd = en - csStart + start
                    val copySpanFlags = sp.getSpanFlags(spans[i]) or SPAN_ADDED

                    val flagsStart = (copySpanFlags and START_MASK) shr START_SHIFT
                    val flagsEnd = copySpanFlags and END_MASK

                    if (!isInvalidParagraphStart(copySpanStart, flagsStart) &&
                        !isInvalidParagraphEnd(copySpanEnd, flagsEnd)
                    ) {
                        setSpan(false, spans[i], copySpanStart, copySpanEnd, copySpanFlags)
                    }
                }
            }
            restoreInvariants()
        }
    }

    private fun updatedIntervalBound(
        offset: Int, start: Int, nbNewChars: Int, flag: Int, atEnd: Boolean,
        textIsRemoved: Boolean
    ): Int {
        if (VERBOSE_LOG) debug(TAG + ": in updatedIntervalBound")
        if (offset >= start && offset < mGapStart + mGapLength) {
            if (flag == POINT) {
                // A POINT located inside the replaced range should be moved to the end of the
                // replaced text.
                // The exception is when the point is at the start of the range and we are doing a
                // text replacement (as opposed to a deletion): the point stays there.
                if (textIsRemoved || offset > start) {
                    return mGapStart + mGapLength
                }
            } else {
                if (flag == PARAGRAPH) {
                    if (atEnd) {
                        return mGapStart + mGapLength
                    }
                } else { // MARK
                    // MARKs should be moved to the start, with the exception of a mark located at
                    // the end of the range (which will be < mGapStart + mGapLength since mGapLength
                    // is > 0, which should stay 'unchanged' at the end of the replaced text.
                    return if (textIsRemoved || offset < mGapStart - nbNewChars) {
                        start
                    } else {
                        // Move to the end of replaced text (needed if nbNewChars != 0)
                        mGapStart
                    }
                }
            }
        }
        return offset
    }

    // Note: caller is responsible for removing the mIndexOfSpan entry.
    private fun removeSpan(i: Int) {
        if (VERBOSE_LOG) debug(TAG + ": in removeSpan")
        val `object` = mSpans[i]

        var start = mSpanStarts[i]
        var end = mSpanEnds[i]

        if (start > mGapStart) start -= mGapLength
        if (end > mGapStart) end -= mGapLength

        val count = mSpanCount - (i + 1)
        System.arraycopy(mSpans, i + 1, mSpans, i, count)
        System.arraycopy(mSpanStarts, i + 1, mSpanStarts, i, count)
        System.arraycopy(mSpanEnds, i + 1, mSpanEnds, i, count)
        System.arraycopy(mSpanFlags, i + 1, mSpanFlags, i, count)
        System.arraycopy(mSpanOrder, i + 1, mSpanOrder, i, count)

        mSpanCount--

        invalidateIndex(i)
        mSpans[mSpanCount] = null

        // Invariants must be restored before sending span removed notifications.
        restoreInvariants()

        sendSpanRemoved(`object`, start, end)
    }

    /**
     * Return externally visible offset given offset into gapped buffer.
     */
    private fun resolveGap(i: Int): Int {
        return if (i > mGapStart) i - mGapLength else i
    }

    // Documentation from interface
    override fun replace(start: Int, end: Int, tb: CharSequence): EditableSecureBuffer {
        if (VERBOSE_LOG) debug(TAG + ": in replace")
        return replace(start, end, tb, 0, tb.length)
    }

    // Documentation from interface
    @SuppressLint("DefaultLocale")
    override fun replace(
        start: Int, end: Int,
        tb: CharSequence, tbstart: Int, tbend: Int
    ): EditableSecureBuffer {
        var tb = tb
        var tbstart = tbstart
        var tbend = tbend
        if (VERBOSE_LOG) debug(TAG + ": in replace 2")
        checkRange("replace", start, end)

        val filtercount = mFilters.size
        for (i in 0..<filtercount) {
            val repl = mFilters[i]!!
                .filter(tb, tbstart, tbend, this, start, end)

            if (repl != null) {
                tb = repl
                tbstart = 0
                tbend = repl.length
            }
        }

        val origLen = end - start
        val newLen = tbend - tbstart

        if (origLen == 0 && newLen == 0 && !hasNonExclusiveExclusiveSpanAt(tb, tbstart)) {
            // This is a no-op iif there are no spans in tb that would be added (with a 0-length)
            // Early exit so that the text watchers do not get notified
            return this
        }

        val textWatchers = getSpans(
            start, start + origLen,
            TextWatcher::class.java
        )
        sendBeforeTextChanged(textWatchers, start, origLen, newLen)

        // Try to keep the cursor / selection at the same relative position during
        // a text replacement. If replaced or replacement text length is zero, this
        // is already taken care of.
        val adjustSelection = origLen != 0 && newLen != 0
        var selectionStart = 0
        var selectionEnd = 0
        if (adjustSelection) {
            selectionStart = Selection.getSelectionStart(this)
            selectionEnd = Selection.getSelectionEnd(this)
        }

        change(start, end, tb, tbstart, tbend)

        if (adjustSelection) {
            var changed = false
            if (selectionStart > start && selectionStart < end) {
                val diff = (selectionStart - start).toLong()
                val offset = (diff * newLen / origLen).toInt()
                selectionStart = start + offset

                changed = true
                setSpan(
                    false, Selection.SELECTION_START, selectionStart, selectionStart,
                    Spanned.SPAN_POINT_POINT
                )
            }
            if (selectionEnd > start && selectionEnd < end) {
                val diff = (selectionEnd - start).toLong()
                val offset = (diff * newLen / origLen).toInt()
                selectionEnd = start + offset

                changed = true
                setSpan(
                    false, Selection.SELECTION_END, selectionEnd, selectionEnd,
                    Spanned.SPAN_POINT_POINT
                )
            }
            if (changed) {
                restoreInvariants()
            }
        }

        if (VERBOSE_LOG) debug(
            String.format(
                "before send text changed: start=%d origLen=%d newLen=%d",
                start,
                origLen,
                newLen
            )
        )
        sendTextChanged(textWatchers, start, origLen, newLen)
        sendAfterTextChanged(textWatchers)

        // Span watchers need to be called after text watchers, which may update the layout
        sendToSpanWatchers(start, end, newLen - origLen)

        return this
    }

    private fun sendToSpanWatchers(replaceStart: Int, replaceEnd: Int, nbNewChars: Int) {
        if (VERBOSE_LOG) debug(TAG + ": in sendToSpanWatchers")
        for (i in 0..<mSpanCount) {
            val spanFlags = mSpanFlags[i]

            // This loop handles only modified (not added) spans.
            if ((spanFlags and SPAN_ADDED) != 0) continue
            var spanStart = mSpanStarts[i]
            var spanEnd = mSpanEnds[i]
            if (spanStart > mGapStart) spanStart -= mGapLength
            if (spanEnd > mGapStart) spanEnd -= mGapLength

            val newReplaceEnd = replaceEnd + nbNewChars
            var spanChanged = false

            var previousSpanStart = spanStart
            if (spanStart > newReplaceEnd) {
                if (nbNewChars != 0) {
                    previousSpanStart -= nbNewChars
                    spanChanged = true
                }
            } else if (spanStart >= replaceStart) {
                // No change if span start was already at replace interval boundaries before replace
                if ((spanStart != replaceStart ||
                            ((spanFlags and SPAN_START_AT_START) != SPAN_START_AT_START)) &&
                    (spanStart != newReplaceEnd ||
                            ((spanFlags and SPAN_START_AT_END) != SPAN_START_AT_END))
                ) {
                    // A correct previousSpanStart cannot be computed at this point.
                    // It would require to save all the previous spans' positions before the replace
                    // Using an invalid -1 value to convey this would break the broacast range
                    spanChanged = true
                }
            }

            var previousSpanEnd = spanEnd
            if (spanEnd > newReplaceEnd) {
                if (nbNewChars != 0) {
                    previousSpanEnd -= nbNewChars
                    spanChanged = true
                }
            } else if (spanEnd >= replaceStart) {
                // No change if span start was already at replace interval boundaries before replace
                if ((spanEnd != replaceStart ||
                            ((spanFlags and SPAN_END_AT_START) != SPAN_END_AT_START)) &&
                    (spanEnd != newReplaceEnd ||
                            ((spanFlags and SPAN_END_AT_END) != SPAN_END_AT_END))
                ) {
                    // same as above for previousSpanEnd
                    spanChanged = true
                }
            }

            if (spanChanged) {
                sendSpanChanged(mSpans[i], previousSpanStart, previousSpanEnd, spanStart, spanEnd)
            }
            mSpanFlags[i] = mSpanFlags[i] and SPAN_START_END_MASK.inv()
        }

        // Handle added spans
        for (i in 0..<mSpanCount) {
            val spanFlags = mSpanFlags[i]
            if ((spanFlags and SPAN_ADDED) != 0) {
                mSpanFlags[i] = mSpanFlags[i] and SPAN_ADDED.inv()
                var spanStart = mSpanStarts[i]
                var spanEnd = mSpanEnds[i]
                if (spanStart > mGapStart) spanStart -= mGapLength
                if (spanEnd > mGapStart) spanEnd -= mGapLength
                sendSpanAdded(mSpans[i], spanStart, spanEnd)
            }
        }
    }

    /**
     * Mark the specified range of text with the specified object.
     * The flags determine how the span will behave when text is
     * inserted at the start or end of the span's range.
     */
    override fun setSpan(what: Any, start: Int, end: Int, flags: Int) {
        if (VERBOSE_LOG) debug(TAG + ": in setSpan")
        setSpan(true, what, start, end, flags)
    }

    // Note: if send is false, then it is the caller's responsibility to restore
    // invariants. If send is false and the span already exists, then this method
    // will not change the index of any spans.
    private fun setSpan(send: Boolean, what: Any?, start: Int, end: Int, flags: Int) {
        var start = start
        var end = end
        if (VERBOSE_LOG) debug(TAG + ": in setSpan 2")
        checkRange("setSpan", start, end)

        val flagsStart = (flags and START_MASK) shr START_SHIFT
        if (isInvalidParagraphStart(start, flagsStart)) {
            throw RuntimeException("PARAGRAPH span must start at paragraph boundary")
        }

        val flagsEnd = flags and END_MASK
        if (isInvalidParagraphEnd(end, flagsEnd)) {
            throw RuntimeException("PARAGRAPH span must end at paragraph boundary")
        }

        // 0-length Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        if (flagsStart == POINT && flagsEnd == MARK && start == end) {
            if (send) {
                Log.e(TAG, "SPAN_EXCLUSIVE_EXCLUSIVE spans cannot have a zero length")
            }
            // Silently ignore invalid spans when they are created from this class.
            // This avoids the duplication of the above test code before all the
            // calls to setSpan that are done in this class
            return
        }

        val nstart = start
        val nend = end

        if (start > mGapStart) {
            start += mGapLength
        } else if (start == mGapStart) {
            if (flagsStart == POINT || (flagsStart == PARAGRAPH && start == length)) start += mGapLength
        }

        if (end > mGapStart) {
            end += mGapLength
        } else if (end == mGapStart) {
            if (flagsEnd == POINT || (flagsEnd == PARAGRAPH && end == length)) end += mGapLength
        }

        if (mIndexOfSpan != null) {
            val index = mIndexOfSpan!![what]
            if (index != null) {
                val i: Int = index
                var ostart = mSpanStarts[i]
                var oend = mSpanEnds[i]

                if (ostart > mGapStart) ostart -= mGapLength
                if (oend > mGapStart) oend -= mGapLength

                mSpanStarts[i] = start
                mSpanEnds[i] = end
                mSpanFlags[i] = flags

                if (send) {
                    restoreInvariants()
                    sendSpanChanged(what, ostart, oend, nstart, nend)
                }

                return
            }
        }

        mSpans = GrowingArrayUtils.append(mSpans, mSpanCount, what)
        mSpanStarts = GrowingArrayUtils.append(mSpanStarts, mSpanCount, start)
        mSpanEnds = GrowingArrayUtils.append(mSpanEnds, mSpanCount, end)
        mSpanFlags = GrowingArrayUtils.append(mSpanFlags, mSpanCount, flags)
        mSpanOrder = GrowingArrayUtils.append(mSpanOrder, mSpanCount, mSpanInsertCount)
        invalidateIndex(mSpanCount)
        mSpanCount++
        mSpanInsertCount++
        // Make sure there is enough room for empty interior nodes.
        // This magic formula computes the size of the smallest perfect binary
        // tree no smaller than mSpanCount.
        val sizeOfMax = 2 * treeRoot() + 1
        if (mSpanMax.size < sizeOfMax) {
            mSpanMax = IntArray(sizeOfMax)
        }

        if (send) {
            restoreInvariants()
            sendSpanAdded(what, nstart, nend)
        }
    }

    private fun isInvalidParagraphStart(start: Int, flagsStart: Int): Boolean {
        if (flagsStart == PARAGRAPH) {
            if (start != 0 && start != length) {
                val c = get(start - 1)

                return c != '\n'
            }
        }
        return false
    }

    private fun isInvalidParagraphEnd(end: Int, flagsEnd: Int): Boolean {
        if (flagsEnd == PARAGRAPH) {
            if (end != 0 && end != length) {
                val c = get(end - 1)

                return c != '\n'
            }
        }
        return false
    }

    /**
     * Remove the specified markup object from the buffer.
     */
    override fun removeSpan(what: Any) {
        if (VERBOSE_LOG) debug(TAG + ": in removeSpan")
        if (mIndexOfSpan == null) return
        val i = mIndexOfSpan!!.remove(what)
        if (i != null) {
            removeSpan(i)
        }
    }

    /**
     * Return the buffer offset of the beginning of the specified
     * markup object, or -1 if it is not attached to this buffer.
     */
    override fun getSpanStart(what: Any): Int {
        if (VERBOSE_LOG) debug(TAG + ": in getSpanStart")
        if (mIndexOfSpan == null) return -1
        val i = mIndexOfSpan!![what]
        return if (i == null) -1 else resolveGap(mSpanStarts[i])
    }

    /**
     * Return the buffer offset of the end of the specified
     * markup object, or -1 if it is not attached to this buffer.
     */
    override fun getSpanEnd(what: Any): Int {
        if (VERBOSE_LOG) debug(TAG + ": in getSpanEnd")
        if (mIndexOfSpan == null) return -1
        val i = mIndexOfSpan!![what]
        return if (i == null) -1 else resolveGap(mSpanEnds[i])
    }

    /**
     * Return the flags of the end of the specified
     * markup object, or 0 if it is not attached to this buffer.
     */
    override fun getSpanFlags(what: Any): Int {
        if (VERBOSE_LOG) debug(TAG + ": in getSpanFlags")
        if (mIndexOfSpan == null) return 0
        val i = mIndexOfSpan!![what]
        return if (i == null) 0 else mSpanFlags[i]
    }

    /**
     * Return an array of the spans of the specified type that overlap
     * the specified range of the buffer.  The kind may be Object.class to get
     * a list of all the spans regardless of type.
     */
    override fun <T> getSpans(queryStart: Int, queryEnd: Int, kind: Class<T>?): Array<T> {
        if (VERBOSE_LOG) debug(TAG + ": in getSpans")
        return getSpans(queryStart, queryEnd, kind, true)
    }

    /**
     * Return an array of the spans of the specified type that overlap
     * the specified range of the buffer.  The kind may be Object.class to get
     * a list of all the spans regardless of type.
     *
     * @param queryStart Start index.
     * @param queryEnd End index.
     * @param kind Class type to search for.
     * @param sort If true the results are sorted by the insertion order.
     * @param <T> span type
     * @return Array of the spans. Empty array if no results are found.
    </T> */
    private fun <T> getSpans(
        queryStart: Int, queryEnd: Int, kind: Class<T>?,
        sort: Boolean
    ): Array<T> {
        if (VERBOSE_LOG) debug(TAG + ": in getSpans 2")
        if (kind == null)
            return ArrayUtils.emptyArray(Any::class.java) as Array<T>
        if (mSpanCount == 0) return ArrayUtils.emptyArray(kind)
        val count = countSpans(queryStart, queryEnd, kind, treeRoot())
        if (count == 0) {
            return ArrayUtils.emptyArray(kind)
        }

        // Safe conversion, but requires a suppressWarning
        val ret = java.lang.reflect.Array.newInstance(kind, count) as Array<T>
        if (sort) {
            mPrioSortBuffer = checkSortBuffer(mPrioSortBuffer, count)
            mOrderSortBuffer = checkSortBuffer(mOrderSortBuffer, count)
        }
        getSpansRec<T>(
            queryStart, queryEnd, kind, treeRoot(), ret, mPrioSortBuffer,
            mOrderSortBuffer, 0, sort
        )
        if (sort) sort(ret, mPrioSortBuffer, mOrderSortBuffer)
        return ret
    }

    private fun countSpans(queryStart: Int, queryEnd: Int, kind: Class<*>, i: Int): Int {
        if (VERBOSE_LOG) debug(TAG + ": in countSpans")
        var count = 0
        if ((i and 1) != 0) {
            // internal tree node
            val left = leftChild(i)
            var spanMax = mSpanMax[left]
            if (spanMax > mGapStart) {
                spanMax -= mGapLength
            }
            if (spanMax >= queryStart) {
                count = countSpans(queryStart, queryEnd, kind, left)
            }
        }
        if (i < mSpanCount) {
            var spanStart = mSpanStarts[i]
            if (spanStart > mGapStart) {
                spanStart -= mGapLength
            }
            if (spanStart <= queryEnd) {
                var spanEnd = mSpanEnds[i]
                if (spanEnd > mGapStart) {
                    spanEnd -= mGapLength
                }
                if (spanEnd >= queryStart &&
                    (spanStart == spanEnd || queryStart == queryEnd ||
                            (spanStart != queryEnd && spanEnd != queryStart)) &&
                    (Any::class.java == kind || kind.isInstance(mSpans[i]))
                ) {
                    count++
                }
                if ((i and 1) != 0) {
                    count += countSpans(queryStart, queryEnd, kind, rightChild(i))
                }
            }
        }
        return count
    }

    /**
     * Fills the result array with the spans found under the current interval tree node.
     *
     * @param queryStart Start index for the interval query.
     * @param queryEnd End index for the interval query.
     * @param kind Class type to search for.
     * @param i Index of the current tree node.
     * @param ret Array to be filled with results.
     * @param priority Buffer to keep record of the priorities of spans found.
     * @param insertionOrder Buffer to keep record of the insertion orders of spans found.
     * @param count The number of found spans.
     * @param sort Flag to fill the priority and insertion order buffers. If false then
     * the spans with priority flag will be sorted in the result array.
     * @param <T> span type
     * @return The total number of spans found.
    </T> */
    private fun <T> getSpansRec(
        queryStart: Int,
        queryEnd: Int,
        kind: Class<T?>,
        i: Int,
        ret: Array<T?>,
        priority: IntArray,
        insertionOrder: IntArray,
        count: Int,
        sort: Boolean
    ): Int {
        var count = count
        if (VERBOSE_LOG) debug(TAG + ": in getSpansRec")
        if ((i and 1) != 0) {
            // internal tree node
            val left = leftChild(i)
            var spanMax = mSpanMax[left]
            if (spanMax > mGapStart) {
                spanMax -= mGapLength
            }
            if (spanMax >= queryStart) {
                count = getSpansRec<T?>(
                    queryStart, queryEnd, kind, left, ret, priority,
                    insertionOrder, count, sort
                )
            }
        }
        if (i >= mSpanCount) return count
        var spanStart = mSpanStarts[i]
        if (spanStart > mGapStart) {
            spanStart -= mGapLength
        }
        if (spanStart <= queryEnd) {
            var spanEnd = mSpanEnds[i]
            if (spanEnd > mGapStart) {
                spanEnd -= mGapLength
            }
            if (spanEnd >= queryStart &&
                (spanStart == spanEnd || queryStart == queryEnd ||
                        (spanStart != queryEnd && spanEnd != queryStart)) &&
                (Any::class.java == kind || kind.isInstance(mSpans[i]))
            ) {
                val spanPriority = mSpanFlags[i] and Spanned.SPAN_PRIORITY
                var target = count
                if (sort) {
                    priority[target] = spanPriority
                    insertionOrder[target] = mSpanOrder[i]
                } else if (spanPriority != 0) {
                    //insertion sort for elements with priority
                    var j = 0
                    while (j < count) {
                        val p = getSpanFlags(ret[j]) and Spanned.SPAN_PRIORITY
                        if (spanPriority > p) break
                        j++
                    }
                    System.arraycopy(ret, j, ret, j + 1, count - j)
                    target = j
                }
                ret[target] = mSpans[i] as T?
                count++
            }
            if (count < ret.size && (i and 1) != 0) {
                count = getSpansRec<T?>(
                    queryStart, queryEnd, kind, rightChild(i), ret, priority,
                    insertionOrder, count, sort
                )
            }
        }
        return count
    }

    /**
     * Check the size of the buffer and grow if required.
     *
     * @param buffer Buffer to be checked.
     * @param size Required size.
     * @return Same buffer instance if the current size is greater than required size. Otherwise a
     * new instance is created and returned.
     */
    private fun checkSortBuffer(buffer: IntArray, size: Int): IntArray {
        if (VERBOSE_LOG) debug(TAG + ": in checkSortBuffer")
        if (size > buffer.size) {
            return IntArray(size + buffer.size / 2)
        }
        return buffer
    }

    /**
     * An iterative heap sort implementation. It will sort the spans using first their priority
     * then insertion order. A span with higher priority will be before a span with lower
     * priority. If priorities are the same, the spans will be sorted with insertion order. A
     * span with a lower insertion order will be before a span with a higher insertion order.
     *
     * @param array Span array to be sorted.
     * @param priority Priorities of the spans
     * @param insertionOrder Insertion orders of the spans
     * @param <T> Span object type.
    </T> */
    private fun <T> sort(array: Array<T>, priority: IntArray, insertionOrder: IntArray) {
        if (VERBOSE_LOG) debug(TAG + ": in sort")
        val size = array.size
        for (i in size / 2 - 1 downTo 0) {
            siftDown(i, array, size, priority, insertionOrder)
        }

        for (i in size - 1 downTo 1) {
            val v = array[0]
            val prio = priority[0]
            val insertOrder = insertionOrder[0]
            array[0] = array[i]
            priority[0] = priority[i]
            insertionOrder[0] = insertionOrder[i]
            siftDown(0, array, i, priority, insertionOrder)
            array[i] = v
            priority[i] = prio
            insertionOrder[i] = insertOrder
        }
    }

    /**
     * Helper function for heap sort.
     *
     * @param index Index of the element to sift down.
     * @param array Span array to be sorted.
     * @param size Current heap size.
     * @param priority Priorities of the spans
     * @param insertionOrder Insertion orders of the spans
     * @param <T> Span object type.
    </T> */
    private fun <T> siftDown(
        index: Int, array: Array<T>, size: Int, priority: IntArray,
        insertionOrder: IntArray
    ) {
        var index = index
        if (VERBOSE_LOG) debug(TAG + ": in siftDown")
        val v = array[index]
        val prio = priority[index]
        val insertOrder = insertionOrder[index]

        var left = 2 * index + 1
        while (left < size) {
            if (left < size - 1 && compareSpans(left, left + 1, priority, insertionOrder) < 0) {
                left++
            }
            if (compareSpans(index, left, priority, insertionOrder) >= 0) {
                break
            }
            array[index] = array[left]
            priority[index] = priority[left]
            insertionOrder[index] = insertionOrder[left]
            index = left
            left = 2 * index + 1
        }
        array[index] = v
        priority[index] = prio
        insertionOrder[index] = insertOrder
    }

    /**
     * Compare two span elements in an array. Comparison is based first on the priority flag of
     * the span, and then the insertion order of the span.
     *
     * @param left Index of the element to compare.
     * @param right Index of the other element to compare.
     * @param priority Priorities of the spans
     * @param insertionOrder Insertion orders of the spans
     * @return comparison result
     */
    private fun compareSpans(
        left: Int, right: Int, priority: IntArray,
        insertionOrder: IntArray
    ): Int {
        if (VERBOSE_LOG) debug(TAG + ": in compareSpans")
        val priority1 = priority[left]
        val priority2 = priority[right]
        if (priority1 == priority2) {
            val x = insertionOrder[left]
            val y = insertionOrder[right]
            return if (x < y) -1 else (if (x == y) 0 else 1)
        }
        // since high priority has to be before a lower priority, the arguments to compare are
        // opposite of the insertion order check.
        return if (priority2 < priority1) -1 else 1
    }

    /**
     * Return the next offset after `start` but less than or
     * equal to `limit` where a span of the specified type
     * begins or ends.
     */
    override fun nextSpanTransition(start: Int, limit: Int, kind: Class<*>?): Int {
        var kind = kind
        if (VERBOSE_LOG) debug(TAG + ": in nextSpanTransition")
        if (mSpanCount == 0) return limit
        if (kind == null) {
            kind = Any::class.java
        }
        return nextSpanTransitionRec(start, limit, kind, treeRoot())
    }

    private fun nextSpanTransitionRec(start: Int, limit: Int, kind: Class<*>, i: Int): Int {
        var limit = limit
        if (VERBOSE_LOG) debug(TAG + ": in nextSpanTransitionRec")
        if ((i and 1) != 0) {
            // internal tree node
            val left = leftChild(i)
            if (resolveGap(mSpanMax[left]) > start) {
                limit = nextSpanTransitionRec(start, limit, kind, left)
            }
        }
        if (i < mSpanCount) {
            val st = resolveGap(mSpanStarts[i])
            val en = resolveGap(mSpanEnds[i])
            if (st > start && st < limit && kind.isInstance(mSpans[i])) limit = st
            if (en > start && en < limit && kind.isInstance(mSpans[i])) limit = en
            if (st < limit && (i and 1) != 0) {
                limit = nextSpanTransitionRec(start, limit, kind, rightChild(i))
            }
        }

        return limit
    }

    /**
     * Return a new CharSequence containing a copy of the specified
     * range of this buffer, including the overlapping spans.
     */
    override fun subSequence(start: Int, end: Int): CharSequence {
        if (VERBOSE_LOG) debug(TAG + ": in subSequence")
        return SpannableStringBuilder(this, start, end)
    }

    /**
     * Copy the specified range of chars from this buffer into the
     * specified array, beginning at the specified offset.
     */
    override fun getChars(start: Int, end: Int, dest: CharArray, destoff: Int) {
        if (VERBOSE_LOG) debug(TAG + ": in getChars")
        checkRange("getChars", start, end)

        val cb = _sb.charBuffer ?: return
        cb.clear()
        if (end <= mGapStart) {
            cb.position(start)
            cb[dest, destoff, end - start]
        } else if (start >= mGapStart) {
            cb.position(start + mGapLength)
            cb[dest, destoff, end - start]
        } else {
            cb.position(start)
            cb[dest, destoff, mGapStart - start].position(mGapStart + mGapLength)
            cb[dest, destoff + (mGapStart - start), end - mGapStart]
        }
    }

    private fun sendBeforeTextChanged(
        watchers: Array<TextWatcher>,
        start: Int,
        before: Int,
        after: Int
    ) {
        val n = watchers.size

        textWatcherDepth++
        for (i in 0..<n) {
            try {
                watchers[i].beforeTextChanged(this, start, before, after)
            } catch (e: Throwable) {
                log(e)
            }
        }
        textWatcherDepth--
    }

    private fun sendTextChanged(watchers: Array<TextWatcher>, start: Int, before: Int, after: Int) {
        if (VERBOSE_LOG) debug(TAG + ": in sendTextChanged")
        val n = watchers.size

        textWatcherDepth++
        for (i in 0..<n) {
            try {
                watchers[i].onTextChanged(this, start, before, after)
            } catch (e: Throwable) {
                log(e)
            }
        }
        textWatcherDepth--
    }

    private fun sendAfterTextChanged(watchers: Array<TextWatcher>) {
        if (VERBOSE_LOG) debug(TAG + ": in sendAfterTextChanged")
        val n = watchers.size

        textWatcherDepth++
        for (i in 0..<n) {
            try {
                watchers[i].afterTextChanged(this)
            } catch (e: Throwable) {
                log(e)
            }
        }
        textWatcherDepth--
    }

    private fun sendSpanAdded(what: Any?, start: Int, end: Int) {
        if (VERBOSE_LOG) debug(TAG + ": in sendSpanAdded")
        val recip = getSpans(start, end, SpanWatcher::class.java)
        val n = recip.size

        for (i in 0..<n) {
            try {
                recip[i].onSpanAdded(this, what, start, end)
            } catch (e: Throwable) {
                log(e)
            }
        }
    }

    private fun sendSpanRemoved(what: Any?, start: Int, end: Int) {
        if (VERBOSE_LOG) debug(TAG + ": in sendSpanRemoved")
        val recip = getSpans(start, end, SpanWatcher::class.java)
        val n = recip.size

        for (i in 0..<n) {
            try {
                recip[i].onSpanRemoved(this, what, start, end)
            } catch (e: Throwable) {
                log(e)
            }
        }
    }

    private fun sendSpanChanged(what: Any?, oldStart: Int, oldEnd: Int, start: Int, end: Int) {
        if (VERBOSE_LOG) debug(TAG + ": in sendSpanChanged")
        // The bounds of a possible SpanWatcher are guaranteed to be set before this method is
        // called, so that the order of the span does not affect this broadcast.
        val spanWatchers = getSpans(
            kotlin.math.min(oldStart.toDouble(), start.toDouble()).toInt(),
            kotlin.math.min(max(oldEnd.toDouble(), end.toDouble()), length.toDouble()).toInt(),
            SpanWatcher::class.java
        )
        val n = spanWatchers.size
        for (i in 0..<n) {
            try {
                spanWatchers[i].onSpanChanged(this, what, oldStart, oldEnd, start, end)
            } catch (e: Throwable) {
                log(e)
            }
        }
    }

    private fun checkRange(operation: String, start: Int, end: Int) {
        if (end < start) {
            throw IndexOutOfBoundsException(
                operation + " " +
                        region(start, end) + " has end before start"
            )
        }

        val len = length

        if (start > len || end > len) {
            throw IndexOutOfBoundsException(
                operation + " " +
                        region(start, end) + " ends beyond length " + len
            )
        }

        if (start < 0 || end < 0) {
            throw IndexOutOfBoundsException(
                operation + " " +
                        region(start, end) + " starts before 0"
            )
        }
    }

    // Documentation from interface
    override fun setFilters(filters: Array<InputFilter>) {
        if (VERBOSE_LOG) debug(TAG + ": in setFilters")
        requireNotNull(filters)

        mFilters = filters
    }

    // Documentation from interface
    override fun getFilters(): Array<InputFilter> {
        return mFilters
    }

    // Primitives for treating span list as binary tree
    // The spans (along with start and end offsets and flags) are stored in linear arrays sorted
    // by start offset. For fast searching, there is a binary search structure imposed over these
    // arrays. This structure is inorder traversal of a perfect binary tree, a slightly unusual
    // but advantageous approach.
    // The value-containing nodes are indexed 0 <= i < n (where n = mSpanCount), thus preserving
    // logic that accesses the values as a contiguous array. Other balanced binary tree approaches
    // (such as a complete binary tree) would require some shuffling of node indices.
    // Basic properties of this structure: For a perfect binary tree of height m:
    // The tree has 2^(m+1) - 1 total nodes.
    // The root of the tree has index 2^m - 1.
    // All leaf nodes have even index, all interior nodes odd.
    // The height of a node of index i is the number of trailing ones in i's binary representation.
    // The left child of a node i of height h is i - 2^(h - 1).
    // The right child of a node i of height h is i + 2^(h - 1).
    // Note that for arbitrary n, interior nodes of this tree may be >= n. Thus, the general
    // structure of a recursive traversal of node i is:
    // * traverse left child if i is an interior node
    // * process i if i < n
    // * traverse right child if i is an interior node and i < n
    private fun treeRoot(): Int {
        return Integer.highestOneBit(mSpanCount) - 1
    }

    // The span arrays are also augmented by an mSpanMax[] array that represents an interval tree
    // over the binary tree structure described above. For each node, the mSpanMax[] array contains
    // the maximum value of mSpanEnds of that node and its descendants. Thus, traversals can
    // easily reject subtrees that contain no spans overlapping the area of interest.
    // Note that mSpanMax[] also has a valid valuefor interior nodes of index >= n, but which have
    // descendants of index < n. In these cases, it simply represents the maximum span end of its
    // descendants. This is a consequence of the perfect binary tree structure.
    private fun calcMax(i: Int): Int {
        var max = 0
        if ((i and 1) != 0) {
            // internal tree node
            max = calcMax(leftChild(i))
        }
        if (i < mSpanCount) {
            max = max(max.toDouble(), mSpanEnds[i].toDouble()).toInt()
            if ((i and 1) != 0) {
                max = max(max.toDouble(), calcMax(rightChild(i)).toDouble()).toInt()
            }
        }
        mSpanMax[i] = max
        return max
    }

    // restores binary interval tree invariants after any mutation of span structure
    private fun restoreInvariants() {
        if (VERBOSE_LOG) debug(TAG + ": in restoreInvariants")
        if (mSpanCount == 0) return

        // invariant 1: span starts are nondecreasing

        // This is a simple insertion sort because we expect it to be mostly sorted.
        for (i in 1..<mSpanCount) {
            if (mSpanStarts[i] < mSpanStarts[i - 1]) {
                val span = mSpans[i]
                val start = mSpanStarts[i]
                val end = mSpanEnds[i]
                val flags = mSpanFlags[i]
                val insertionOrder = mSpanOrder[i]
                var j = i
                do {
                    mSpans[j] = mSpans[j - 1]
                    mSpanStarts[j] = mSpanStarts[j - 1]
                    mSpanEnds[j] = mSpanEnds[j - 1]
                    mSpanFlags[j] = mSpanFlags[j - 1]
                    mSpanOrder[j] = mSpanOrder[j - 1]
                    j--
                } while (j > 0 && start < mSpanStarts[j - 1])
                mSpans[j] = span
                mSpanStarts[j] = start
                mSpanEnds[j] = end
                mSpanFlags[j] = flags
                mSpanOrder[j] = insertionOrder
                invalidateIndex(j)
            }
        }

        // invariant 2: max is max span end for each node and its descendants
        calcMax(treeRoot())

        // invariant 3: mIndexOfSpan maps spans back to indices
        if (mIndexOfSpan == null) {
            mIndexOfSpan = IdentityHashMap()
        }
        for (i in mLowWaterMark..<mSpanCount) {
            val existing = mIndexOfSpan!![mSpans[i]]
            if (existing == null || existing != i) {
                mIndexOfSpan!![mSpans[i]] = i
            }
        }
        mLowWaterMark = Int.MAX_VALUE
    }

    // Call this on any update to mSpans[], so that mIndexOfSpan can be updated
    private fun invalidateIndex(i: Int) {
        mLowWaterMark = kotlin.math.min(i.toDouble(), mLowWaterMark.toDouble()).toInt()
    }

    private var mFilters = NO_FILTERS

    private var mGapStart: Int
    private var mGapLength: Int

    private var mSpans: Array<Any?>
    private var mSpanStarts: IntArray
    private var mSpanEnds: IntArray
    private var mSpanMax: IntArray // see calcMax() for an explanation of what this array stores
    private var mSpanFlags: IntArray
    private var mSpanOrder: IntArray // store the order of span insertion
    private var mSpanInsertCount: Int // counter for the span insertion
    private var mPrioSortBuffer: IntArray // buffer used to sort getSpans result
    private var mOrderSortBuffer: IntArray // buffer used to sort getSpans result

    private var mSpanCount: Int
    private var mIndexOfSpan: IdentityHashMap<Any?, Int>? = null
    private var mLowWaterMark = 0 // indices below this have not been touched

    // TextWatcher callbacks may trigger changes that trigger more callbacks. This keeps track of
    // how deep the callbacks go.
    @get:Suppress("unused")
    var textWatcherDepth: Int = 0
        private set

    init {
        if (VERBOSE_LOG) debug(TAG + ": creating")
        _sb = sb
        val cb = sb.charBuffer
        mGapStart = cb!!.length
        mGapLength = cb.capacity() - mGapStart
        mSpanCount = 0
        mSpanInsertCount = 0
        mSpans = arrayOfNulls(0)
        mOrderSortBuffer = IntArray(0)
        mPrioSortBuffer = mOrderSortBuffer
        mSpanOrder = mPrioSortBuffer
        mSpanMax = mSpanOrder
        mSpanFlags = mSpanMax
        mSpanEnds = mSpanFlags
        mSpanStarts = mSpanEnds
    }

    companion object {
        const val TAG: String = "EditableSecureBuffer"
        private const val VERBOSE_LOG = false

        private fun hasNonExclusiveExclusiveSpanAt(text: CharSequence, offset: Int): Boolean {
            if (text is Spanned) {
                val spanned = text
                val spans = spanned.getSpans(
                    offset, offset,
                    Any::class.java
                )
                val length = spans.size
                for (i in 0..<length) {
                    val span = spans[i]
                    val flags = spanned.getSpanFlags(span)
                    if (flags != Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) return true
                }
            }
            return false
        }

        private fun region(start: Int, end: Int): String {
            return "($start ... $end)"
        }

        // (i+1) & ~i is equal to 2^(the number of trailing ones in i)
        private fun leftChild(i: Int): Int {
            return i - (((i + 1) and i.inv()) shr 1)
        }

        private fun rightChild(i: Int): Int {
            return i + (((i + 1) and i.inv()) shr 1)
        }

        private val NO_FILTERS = arrayOfNulls<InputFilter>(0)
        private const val MARK = 1
        private const val POINT = 2
        private const val PARAGRAPH = 3

        private const val START_MASK = 0xF0
        private const val END_MASK = 0x0F
        private const val START_SHIFT = 4

        // These bits are not (currently) used by SPANNED flags
        private const val SPAN_ADDED = 0x800
        private const val SPAN_START_AT_START = 0x1000
        private const val SPAN_START_AT_END = 0x2000
        private const val SPAN_END_AT_START = 0x4000
        private const val SPAN_END_AT_END = 0x8000
        private const val SPAN_START_END_MASK = 0xF000
    }
}
